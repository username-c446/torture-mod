// Живая проверка выхода из игры: тело, наказание, пытка тела, возвращение, слом, перезапуск и смерть тела.
// Запуск из корня проекта: node tools/bot/check-offline.js
const { Vec3 } = require('vec3');
const {
    wait, check, startServer, waitForStart, stopServer, run, join, teleport, count, said, listed, tail, main,
} = require('./lib');

const X = 3000, Y = 150, Z = 3000;
const SPOT = new Vec3(X + 1.5, Y + 1, Z + 1.5);
// Селектор с type=player ищет только игроков в сети, поэтому тело ищем по имени среди всех сущностей.
const BODY = '@e[name=Prisoner,limit=1]';

const bodiesOf = (bot, name) => Object.values(bot.entities)
    .filter(entity => entity.type === 'player' && entity.username === name && entity !== bot.entity);

async function waitUntil(test, ms = 10000) {
    const deadline = Date.now() + ms;
    while (Date.now() < deadline) {
        if (test()) return true;
        await wait(300);
    }
    return false;
}

async function quit(bot) {
    bot.quit();
    await wait(2500);
}

async function healthOf(selector) {
    const output = await run(`data get entity ${selector} Health`, 1500);
    const line = output.split('\n').find(found => found.includes('entity data')) || '';
    const match = line.match(/: ([\d.]+)f/);
    return match ? Number(match[1]) : NaN;
}

async function setup(prisoner, captor) {
    await run('gamemode creative @a', 300);
    for (const command of ['gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'time set 1000',
        'weather clear', 'difficulty peaceful', 'gamerule naturalRegeneration false']) {
        await run(command, 300);
    }
    await run(`forceload add ${X} ${Z} ${X + 16} ${Z + 16}`, 4000);
    await run(`fill ${X - 2} ${Y - 1} ${Z - 2} ${X + 8} ${Y + 5} ${Z + 8} minecraft:air`, 300);
    await run(`kill @e[type=minecraft:item,x=${X},y=${Y},z=${Z},distance=..30]`, 300);
    await run(`fill ${X} ${Y} ${Z} ${X + 6} ${Y} ${Z + 6} minecraft:stone`, 300);
    for (const name of ['Prisoner', 'Captor']) {
        await run(`torturemod release ${name}`, 200);
        await run(`clear ${name}`, 200);
        await run(`effect clear ${name}`, 200);
        await run(`effect give ${name} minecraft:instant_health 1 10`, 200);
    }
    await teleport(prisoner, SPOT.x, SPOT.y, SPOT.z);
    await teleport(captor, X + 3.5, Y + 1, Z + 1.5);
    await run('gamemode survival @a', 300);
    await run('give Captor minecraft:iron_sword', 300);
    await run('give Prisoner minecraft:diamond 3', 300);
    await run('torturemod capture Prisoner', 1500);
}

main(async () => {
    let prisoner = await join('Prisoner');
    let captor = await join('Captor');
    try {
        await setup(prisoner, captor);

        // 1. Выход оставляет тело, которого нет среди игроков в сети, и снимает 10 % шкалы.
        await quit(prisoner);
        const appeared = await waitUntil(() => bodiesOf(captor, 'Prisoner').length === 1);
        check('на месте вышедшего пленного остаётся тело', appeared,
            `${bodiesOf(captor, 'Prisoner').length} тел видно палачу`);
        const online = await run('list', 1000);
        check('тела нет среди игроков в сети', !/Prisoner/.test(tail(online, 1)), tail(online, 1));
        let list = await run('torturemod list', 1000);
        check('выход снимает 10 % сопротивления', /Prisoner: .*90/.test(list), tail(list, 1));

        // 2. Тело можно пытать.
        const body = bodiesOf(captor, 'Prisoner')[0];
        if (body) {
            await captor.equip(captor.inventory.items().find(item => item.name === 'iron_sword'), 'hand');
            await captor.lookAt(body.position.offset(0, 1.2, 0));
            captor.activateEntity(body);
            await wait(1200);
        }
        const bodyHealth = await healthOf(BODY);
        check('пытка ранит тело', bodyHealth < 20, `здоровье тела ${bodyHealth}`);

        // 3. Возвращение: состояние тела переходит к игроку, тело пропадает.
        prisoner = await join('Prisoner');
        await wait(2500);
        check('игрок возвращается раненым, как его тело', prisoner.health < 20, `здоровье ${prisoner.health}`);
        check('игрок возвращается на место тела', prisoner.entity.position.distanceTo(SPOT) < 2,
            `${prisoner.entity.position}`);
        check('тело пропадает после возвращения', bodiesOf(captor, 'Prisoner').length === 1,
            `${bodiesOf(captor, 'Prisoner').length} игроков с этим ником видно палачу`);
        check('игрок узнаёт о наказании', said(prisoner, 'Выходов: 1 из 5'), prisoner.msgs.slice(-2).join(' / '));
        list = await run('torturemod list', 1000);
        check('после возвращения плен сохраняется', listed(list, 'Prisoner'), tail(list, 1));

        // 4. Пятый выход ломает пленного.
        for (let exit = 2; exit <= 5; exit++) {
            await quit(prisoner);
            prisoner = await join('Prisoner');
            await wait(1500);
        }
        check('пятый выход ломает пленного', said(prisoner, 'сломлены'), prisoner.msgs.slice(-2).join(' / '));

        // 5. Тело возвращается после перезапуска сервера.
        await quit(prisoner);
        captor.quit();
        await stopServer();
        startServer();
        await waitForStart();
        captor = await join('Captor');
        const restored = await waitUntil(() => bodiesOf(captor, 'Prisoner').length === 1, 15000);
        const position = await run(`data get entity ${BODY} Pos`, 1500);
        check('тело появляется снова после перезапуска', restored,
            `${bodiesOf(captor, 'Prisoner').length} тел видно палачу, на сервере: ${tail(position, 1)}`);

        // 6. Смерть тела: вещи выпадают, плен снимается, вернувшийся игрок мёртв.
        await run(`kill ${BODY}`, 2500);
        const drops = await waitUntil(() => Object.values(captor.entities).some(entity => entity.name === 'item'));
        check('при смерти тела выпадают вещи', drops);
        list = await run('torturemod list', 1000);
        check('смерть тела снимает плен', !listed(list, 'Prisoner'), tail(list, 1));
        prisoner = await join('Prisoner');
        await wait(3000);
        check('вернувшийся игрок мёртв и без вещей', prisoner.deaths > 0 && count(prisoner, 'diamond') === 0,
            `смертей ${prisoner.deaths}, алмазов ${count(prisoner, 'diamond')}`);
    } finally {
        await run('gamerule naturalRegeneration true', 300);
        [prisoner, captor].forEach(bot => bot.quit());
    }
});

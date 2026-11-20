// Живая проверка плена: клетка, ограничения пленного, освобождение, перезапуск.
// Запуск из корня проекта: node tools/bot/check-captivity.js
const { Vec3 } = require('vec3');
const {
    wait, check, startServer, waitForStart, stopServer, run, join, seen, teleport, count, said, shown, listed, tail, main,
} = require('./lib');

const X = 1000, Y = 150, Z = 1000;

main(async () => {
    const prisoner = await join('Prisoner');
    check('ванильный клиент заходит на сервер с модом', true);
    const captor = await join('Captor');
    const far = await join('Far');

    // Боты заходят там, где были в прошлый раз, и могут упасть, пока площадка перестраивается:
    // на время подготовки они в творческом режиме, где падение не убивает.
    await run('gamemode creative @a', 300);
    for (const command of ['gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'time set 1000',
        'weather clear', 'difficulty peaceful']) {
        await run(command, 300);
    }
    // Сначала грузим чанки и строим полы, иначе боты падают до того, как под ними появится опора.
    await run(`forceload add ${X} ${Z} ${X + 16} ${Z + 16}`, 300);
    await run(`forceload add ${X + 300} ${Z + 300}`, 4000);
    // Мир остаётся от прошлых прогонов: очищаем площадку и снимаем старый плен.
    await run(`fill ${X - 1} ${Y - 1} ${Z - 1} ${X + 9} ${Y + 5} ${Z + 5} minecraft:air`, 300);
    await run(`kill @e[type=minecraft:cow,x=${X},y=${Y},z=${Z},distance=..20]`, 300);
    for (const name of ['Prisoner', 'Captor', 'Far']) {
        await run(`torturemod release ${name}`, 200);
        // Боты хранят вещи с прошлых прогонов: начинаем с пустого инвентаря.
        await run(`clear ${name}`, 200);
    }
    await run(`fill ${X} ${Y} ${Z} ${X + 4} ${Y + 4} ${Z + 4} minecraft:iron_bars hollow`, 300);
    await run(`setblock ${X + 2} ${Y} ${Z + 2} minecraft:lodestone`, 300);
    await run(`fill ${X + 5} ${Y} ${Z} ${X + 8} ${Y} ${Z + 4} minecraft:stone`, 300);
    await run(`setblock ${X + 300} ${Y} ${Z + 300} minecraft:stone`, 300);
    await teleport(captor, X + 6.5, Y + 1, Z + 2.5);
    await teleport(prisoner, X + 2.5, Y + 1, Z + 2.5);
    await teleport(far, X + 300.5, Y + 1, Z + 300.5);
    await run('gamemode survival Captor', 300);
    await run('give Captor minecraft:lead 3', 300);
    await run('give Prisoner minecraft:stone 5', 2000);
    // После дальнего телепорта клиент получает чанки не сразу: ждём, пока оба бота увидят клетку.
    const cageBlock = new Vec3(X, Y + 1, Z + 2);
    for (let i = 0; i < 30; i++) {
        const sees = bot => bot.blockAt(cageBlock) && bot.blockAt(cageBlock).name === 'iron_bars';
        if (sees(prisoner) && sees(captor)) break;
        await wait(500);
    }

    // 1. Захват в клетке поводком.
    const leadsBefore = count(captor, 'lead');
    const target = await seen(captor, 'Prisoner');
    if (!target) throw new Error('Captor does not see Prisoner');
    await captor.equip(captor.inventory.items().find(item => item.name === 'lead'), 'hand');
    await captor.lookAt(target.position.offset(0, 1.6, 0));
    captor.activateEntity(target);
    await wait(1500);
    let list = await run('torturemod list');
    check('захват поводком в клетке с магнетитом', listed(list, 'Prisoner'), tail(list, 3));
    check('пленный получает сообщение', said(prisoner, 'плен'), prisoner.msgs.slice(-3).join(' / '));
    check('поводок тратится', count(captor, 'lead') === leadsBefore - 1, `${leadsBefore} -> ${count(captor, 'lead')}`);

    // 2. Ограничения пленного.
    const wall = new Vec3(X, Y + 1, Z + 2);
    try {
        await Promise.race([prisoner.dig(prisoner.blockAt(wall), true), wait(3000)]);
    } catch (e) {
        // Отменённое копание может завершиться ошибкой на стороне бота: важен результат на сервере.
    }
    await wait(1500);
    check('пленный не ломает стену', prisoner.blockAt(wall).name === 'iron_bars', prisoner.blockAt(wall).name);

    prisoner.chat('help me please');
    await wait(1500);
    check('чат пленного слышат рядом', captor.msgs.some(m => m.includes('help me please')), captor.msgs.slice(-2).join(' / '));
    check('чат пленного не слышат далеко', !far.msgs.some(m => m.includes('help me please')), far.msgs.slice(-2).join(' / '));

    prisoner.chat('/me waves');
    await wait(1500);
    check('команды пленного заблокированы', said(prisoner, 'команда недоступна') && !said(captor, 'waves'),
        prisoner.msgs.slice(-2).join(' / '));

    const stoneBefore = count(prisoner, 'stone');
    const stack = prisoner.inventory.items().find(item => item.name === 'stone');
    if (stack) {
        try {
            await Promise.race([prisoner.tossStack(stack), wait(3000)]);
        } catch (e) {
            // Как и с копанием, важно состояние на сервере.
        }
    }
    await wait(2000);
    check('пленный не выбрасывает вещи', stoneBefore > 0 && count(prisoner, 'stone') === stoneBefore,
        `${stoneBefore} -> ${count(prisoner, 'stone')}`);

    // 3. Выход из клетки освобождает.
    await run(`tp Prisoner ${X + 10.5} ${Y + 1} ${Z + 10.5}`, 3000);
    list = await run('torturemod list');
    check('выход из клетки освобождает', !listed(list, 'Prisoner') && said(prisoner, 'свободны'), tail(list));

    // 4. Защита от повторного захвата.
    await teleport(prisoner, X + 2.5, Y + 1, Z + 2.5);
    const again = await seen(captor, 'Prisoner');
    if (again) captor.activateEntity(again);
    await wait(1500);
    list = await run('torturemod list');
    check('повторный захват сразу после освобождения запрещён',
        !listed(list, 'Prisoner') && shown(captor, 'недавно'), captor.bars.slice(-2).join(' / '));

    // 5. Моб: захват кликом по маркеру, клетка не превращается в поводок.
    await run(`tp Prisoner ${X + 300.5} ${Y + 1} ${Z + 300.5}`, 500);
    await run(`summon minecraft:cow ${X + 2.5} ${Y + 1} ${Z + 2.5} {NoAI:1b}`, 1500);
    await captor.lookAt(new Vec3(X + 2.5, Y + 1, Z + 2.5));
    await captor.activateBlock(captor.blockAt(new Vec3(X + 2, Y, Z + 2)));
    await wait(1500);
    list = await run('torturemod list');
    check('клик по магнетиту берёт в плен моба внутри', listed(list, 'Cow'), tail(list));

    // 6. Сломанная стена освобождает.
    await run(`setblock ${X + 4} ${Y + 2} ${Z + 2} minecraft:air`, 6500);
    list = await run('torturemod list');
    check('разрушенная клетка освобождает', !listed(list, 'Cow'), tail(list));

    // 7. Плен командой и смерть.
    const output = await run('torturemod capture Prisoner');
    list = await run('torturemod list');
    check('команда администратора берёт в плен без клетки', listed(list, 'Prisoner'), tail(output, 1));
    await run('kill Prisoner', 2000);
    list = await run('torturemod list');
    check('смерть освобождает', !listed(list, 'Prisoner'), tail(list));

    // 8. Плен переживает перезапуск сервера.
    await run('torturemod capture Captor');
    [prisoner, captor, far].forEach(bot => bot.quit());
    await stopServer();
    startServer();
    await waitForStart();
    list = await run('torturemod list', 2000);
    check('плен сохраняется после перезапуска', listed(list, 'Captor'), tail(list));
    await run('torturemod release Captor');
});

// Живая проверка конвоя: поводок с видимой верёвкой, переход в другую клетку, подтягивание,
// таймер возврата и перевозка в лодке.
// Запуск из корня проекта: node tools/bot/check-convoy.js
const fs = require('fs');
const path = require('path');
const { Vec3 } = require('vec3');
const { ROOT, wait, check, run, join, seen, teleport, listed, tail, main } = require('./lib');

const X = 6000, Y = 150, Z = 6000;
const CONFIG = path.join(ROOT, 'platform-forge-1.20.1', 'run', 'config', 'torturemod', 'config.yml');
// Порог обрыва выше, потому что пленного и палача телепортируют по очереди.
const TEST_CONFIG = ['convoy:', '  return_seconds: 5', '  break_distance: 40'].join('\n');

async function cage(x) {
    await run(`fill ${x} ${Y} ${Z} ${x + 4} ${Y + 4} ${Z + 4} minecraft:iron_bars hollow`, 300);
    await run(`setblock ${x + 2} ${Y} ${Z + 2} minecraft:lodestone`, 300);
}

async function leadClick(captor, target) {
    await captor.equip(captor.inventory.items().find(item => item.name === 'lead'), 'hand');
    await captor.lookAt(target.position.offset(0, 1.2, 0));
    captor.activateEntity(target);
    await wait(1200);
}

main(async () => {
    const original = fs.existsSync(CONFIG) ? fs.readFileSync(CONFIG, 'utf8') : null;
    const prisoner = await join('Prisoner');
    const captor = await join('Captor');
    try {
        fs.writeFileSync(CONFIG, (original || '') + '\n' + TEST_CONFIG + '\n', 'utf8');
        await run('torturemod reload', 1000);
        await run('gamemode creative @a', 300);
        for (const command of ['gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'time set 1000',
            'weather clear', 'difficulty peaceful']) {
            await run(command, 300);
        }
        await run(`forceload add ${X} ${Z} ${X + 48} ${Z + 16}`, 5000);
        await run(`fill ${X - 2} ${Y} ${Z - 2} ${X + 48} ${Y + 5} ${Z + 10} minecraft:air`, 300);
        await run(`fill ${X - 2} ${Y - 1} ${Z - 2} ${X + 48} ${Y - 1} ${Z + 10} minecraft:stone`, 300);
        await run(`kill @e[type=minecraft:boat,x=${X},y=${Y},z=${Z},distance=..60]`, 300);
        for (const name of ['Prisoner', 'Captor']) {
            await run(`torturemod release ${name}`, 200);
            await run(`clear ${name}`, 200);
        }
        await cage(X);
        await cage(X + 10);
        await teleport(prisoner, X + 2.5, Y + 1, Z + 2.5);
        await teleport(captor, X + 5.5, Y + 1, Z + 2.5);
        await run('gamemode survival @a', 300);
        await run('give Captor minecraft:lead 2', 1000);

        let target = await seen(captor, 'Prisoner');
        if (!target) throw new Error('Captor does not see Prisoner');
        await leadClick(captor, target);
        let list = await run('torturemod list', 1000);
        if (!listed(list, 'Prisoner')) throw new Error('capture in cage failed: ' + tail(list, 1));

        // 1. Поводок с видимой верёвкой.
        await leadClick(captor, target);
        const rope = Object.values(captor.entities).some(entity => entity.name === 'bat'
            && entity.position.distanceTo(target.position) < 3);
        check('пленного берут на поводок', captor.bars.some(bar => bar.includes('Вы ведёте Prisoner')), captor.bars.slice(-1)[0]);
        check('верёвку видно у пленного', rope);

        // 2. На поводке выход из клетки не освобождает, отпущенный в другой клетке остаётся в ней.
        await teleport(prisoner, X + 12.5, Y + 1, Z + 2.5);
        await teleport(captor, X + 15.5, Y + 1, Z + 2.5);
        await wait(2500);
        list = await run('torturemod list', 1000);
        check('на поводке выход из клетки не освобождает', listed(list, 'Prisoner'), tail(list, 1));
        target = await seen(captor, 'Prisoner');
        await leadClick(captor, target);
        list = await run('torturemod list', 1000);
        check('отпущенный в другой клетке получает её',
            captor.bars.some(bar => bar.includes('в новой клетке')) && list.includes(`${X + 12} ${Y + 1} ${Z + 2}`), tail(list, 1));

        // 3. Поводок тянет за палачом.
        await leadClick(captor, target);
        // Сначала палач, потом пленный: иначе поводок тянет пленного обратно во время телепорта.
        await teleport(captor, X + 37.5, Y + 1, Z + 2.5);
        await teleport(prisoner, X + 30.5, Y + 1, Z + 2.5);
        // Поводок начинает тянуть, пока сценарий ещё ждёт подтверждения телепорта: сравниваем с точкой телепорта.
        const startX = X + 30.5;
        await wait(3000);
        check('поводок тянет пленного к палачу', prisoner.entity.position.x - startX > 1,
            `${startX.toFixed(1)} -> ${prisoner.entity.position.x.toFixed(1)}`);

        // 4. Отпущенный вне клетки: таймер возврата, затем свобода.
        target = await seen(captor, 'Prisoner');
        await leadClick(captor, target);
        check('вне клетки запускается таймер возврата', captor.msgs.some(m => m.includes('Верните Prisoner в клетку за 5 с')),
            captor.msgs.slice(-1)[0]);
        await wait(7000);
        list = await run('torturemod list', 1000);
        check('не вернули вовремя — плен спадает', !listed(list, 'Prisoner'), tail(list, 1));

        // 5. Перевозка в лодке: пленный сам не слезает.
        await run('torturemod capture Prisoner', 1000);
        await teleport(prisoner, X + 30.5, Y, Z + 2.5);
        await teleport(captor, X + 32.5, Y, Z + 2.5);
        await run(`summon minecraft:boat ${X + 33.5} ${Y} ${Z + 4.5}`, 1500);
        target = await seen(captor, 'Prisoner');
        await leadClick(captor, target);
        const boat = captor.nearestEntity(entity => entity.name === 'boat');
        if (!boat) throw new Error('no boat');
        await captor.lookAt(boat.position);
        captor.activateEntity(boat);
        await wait(1500);
        let riding = await run('data get entity Prisoner RootVehicle', 1000);
        check('палач сажает пленного в лодку', /entity data/.test(riding), captor.bars.slice(-2).join(' / '));
        // Игрок слезает с транспорта приседанием.
        prisoner.setControlState('sneak', true);
        await wait(800);
        prisoner.setControlState('sneak', false);
        await wait(1000);
        riding = await run('data get entity Prisoner RootVehicle', 1000);
        check('пленный сам не слезает с лодки', /entity data/.test(riding), tail(riding, 1));

        // 6. Палач снимает пленного и отпускает поводок: пленный без клетки остаётся в плену.
        target = await seen(captor, 'Prisoner');
        await leadClick(captor, target);
        riding = await run('data get entity Prisoner RootVehicle', 1000);
        list = await run('torturemod list', 1000);
        check('палач снимает пленного с транспорта', !/entity data/.test(riding) && listed(list, 'Prisoner'), tail(riding, 1));
    } finally {
        if (original === null) {
            if (fs.existsSync(CONFIG)) fs.unlinkSync(CONFIG);
        } else {
            fs.writeFileSync(CONFIG, original, 'utf8');
        }
        await run('torturemod reload', 500);
        await run('torturemod release Prisoner', 300);
        [prisoner, captor].forEach(bot => bot.quit());
    }
});

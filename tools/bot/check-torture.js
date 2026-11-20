// Живая проверка пыток: четыре типа, перезарядка, меню пленного, невосприимчивость, слом и казнь.
// Запуск из корня проекта: node tools/bot/check-torture.js
const fs = require('fs');
const path = require('path');
const {
    ROOT, wait, check, run, join, seen, nearest, teleport, useOn, openMenu, count, said, shown, listed, tail, main,
} = require('./lib');

const X = 2000, Y = 150, Z = 2000;
const CONFIG = path.join(ROOT, 'platform-forge-1.20.1', 'run', 'config', 'torturemod', 'config.yml');
// UUID страйдера задаём сами, чтобы взять его в плен командой.
const STRIDER_NBT = '{NoAI:1b,UUID:[I;1,2,3,4]}';
const STRIDER_UUID = '00000001-0000-0002-0000-000300000004';

async function entityData(name, path) {
    const output = await run(`data get entity ${name} ${path}`, 2000);
    return output.split('\n').find(line => line.includes('entity data')) || '';
}

main(async () => {
    const original = fs.existsSync(CONFIG) ? fs.readFileSync(CONFIG, 'utf8') : null;
    const prisoner = await join('Prisoner');
    const captor = await join('Captor');
    try {
        // Боты заходят там, где были в прошлый раз, и могут упасть, пока площадка перестраивается.
        await run('gamemode creative @a', 300);
        for (const command of ['gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'time set 1000',
            'weather clear', 'difficulty peaceful', 'gamerule naturalRegeneration false']) {
            await run(command, 300);
        }
        await run(`forceload add ${X} ${Z} ${X + 16} ${Z + 16}`, 4000);
        await run(`fill ${X - 2} ${Y - 1} ${Z - 2} ${X + 8} ${Y + 5} ${Z + 8} minecraft:air`, 300);
        await run(`kill @e[type=minecraft:strider,x=${X},y=${Y},z=${Z},distance=..30]`, 300);
        await run(`fill ${X} ${Y} ${Z} ${X + 6} ${Y} ${Z + 6} minecraft:stone`, 300);
        for (const name of ['Prisoner', 'Captor']) {
            await run(`torturemod release ${name}`, 200);
            await run(`clear ${name}`, 200);
            await run(`effect clear ${name}`, 200);
            // Боты сохраняют здоровье между прогонами, а пытка смертельна: начинаем с полного.
            await run(`effect give ${name} minecraft:instant_health 1 10`, 200);
        }
        await teleport(prisoner, X + 1.5, Y + 1, Z + 1.5);
        await teleport(captor, X + 3.5, Y + 1, Z + 1.5);
        for (const name of ['Prisoner', 'Captor']) {
            await run(`gamemode survival ${name}`, 200);
        }
        for (const item of ['iron_sword', 'flint_and_steel', 'water_bucket', 'spider_eye 3']) {
            await run(`give Captor minecraft:${item}`, 200);
        }
        await run('torturemod capture Prisoner', 2000);

        const target = await seen(captor, 'Prisoner');
        if (!target) throw new Error('Captor does not see Prisoner');

        // 1. Удары: урон, прочность, шкала у обоих.
        await useOn(captor, target, 'iron_sword');
        check('удар мечом ранит пленного', prisoner.health < 20, `здоровье ${prisoner.health}`);
        const sword = captor.inventory.items().find(item => item.name === 'iron_sword');
        check('меч теряет прочность', sword && sword.durabilityUsed > 0, `износ ${sword && sword.durabilityUsed}`);
        check('шкала видна пленному и палачу',
            prisoner.barTitles.some(bar => bar.includes('сопротивление')) && captor.barTitles.some(bar => bar.includes('сопротивление')),
            prisoner.barTitles.slice(-1)[0]);

        // 2. Перезарядка.
        await useOn(captor, target, 'iron_sword');
        check('перезарядка не даёт повторить сразу', shown(captor, 'Перезарядка'), captor.bars.slice(-1)[0]);

        // 3. Огонь, вода, яд через ванильные механики.
        await useOn(captor, target, 'flint_and_steel');
        const fire = await entityData('Prisoner', 'Fire');
        check('огниво поджигает', /: [1-9]\d*s/.test(fire), fire);

        await useOn(captor, target, 'water_bucket');
        check('ведро воды отнимает воздух', prisoner.minOxygen < 20, `минимум воздуха ${prisoner.minOxygen}/20`);
        check('ведро пустеет', count(captor, 'water_bucket') === 0 && count(captor, 'bucket') === 1,
            `water ${count(captor, 'water_bucket')}, empty ${count(captor, 'bucket')}`);

        await useOn(captor, target, 'spider_eye');
        const effects = await entityData('Prisoner', 'ActiveEffects');
        check('паучий глаз травит', effects.includes('minecraft:poison'), effects.slice(0, 120));
        check('паучий глаз тратится', count(captor, 'spider_eye') === 2, `${count(captor, 'spider_eye')}`);

        // 4. Меню пленного по Shift + ПКМ, вещи из него не забрать.
        let window = await openMenu(captor, target);
        check('Shift + ПКМ открывает меню пленного', window && window.title.includes('Пленный'), window && window.title);
        if (window) {
            await captor.clickWindow(4, 0, 0);
            await wait(800);
            check('из меню нельзя забрать предмет', count(captor, 'name_tag') === 0
                && captor.currentWindow && captor.currentWindow.slots[4] && captor.currentWindow.slots[4].name === 'name_tag',
                `в инвентаре ${count(captor, 'name_tag')}`);
            await captor.clickWindow(21, 0, 0);
            await wait(1500);
        }
        let list = await run('torturemod list');
        check('кнопка «отпустить» освобождает', !listed(list, 'Prisoner') && said(prisoner, 'свободны'), tail(list));

        // 5. Невосприимчивость по ванильным свойствам: страйдер не горит.
        await run(`summon minecraft:strider ${X + 3.5} ${Y + 1} ${Z + 3.5} ${STRIDER_NBT}`, 800);
        await run(`torturemod capture ${STRIDER_UUID}`, 1000);
        const strider = await nearest(captor, 'strider');
        if (strider) {
            await run('give Captor minecraft:flint_and_steel', 500);
            const bars = captor.bars.length;
            await useOn(captor, strider, 'flint_and_steel');
            check('огнестойкий моб не поддаётся огню', captor.bars.slice(bars).some(bar => bar.includes('не поддаётся')),
                captor.bars.slice(-1)[0]);
        } else {
            check('огнестойкий моб не поддаётся огню', false, 'страйдер не найден');
        }
        await run(`torturemod release ${STRIDER_UUID}`, 300);

        // 6. Слом и казнь: временно усиливаем удар через конфиг и перезагрузку.
        fs.writeFileSync(CONFIG, (original || '') + '\ntorture:\n  types:\n    strike:\n      resistance_drop: 500\n', 'utf8');
        const reload = await run('torturemod reload', 1500);
        await run('effect clear Prisoner', 200);
        await run('effect give Prisoner minecraft:instant_health 1 10', 300);
        await run('torturemod capture Prisoner', 1000);
        await wait(3500);
        await useOn(captor, target, 'iron_sword');
        check('пленный ломается', said(prisoner, 'сломлены') && said(captor, 'сломлен'), tail(reload, 1));
        window = await openMenu(captor, target);
        if (window) {
            await captor.clickWindow(23, 0, 0);
            await wait(2000);
        }
        list = await run('torturemod list');
        check('казнь убивает сломленного и снимает плен', !listed(list, 'Prisoner') && said(captor, 'казнён'),
            captor.msgs.slice(-2).join(' / '));
    } finally {
        if (original === null) {
            if (fs.existsSync(CONFIG)) fs.unlinkSync(CONFIG);
        } else {
            fs.writeFileSync(CONFIG, original, 'utf8');
        }
        await run('torturemod reload', 500);
        await run('gamerule naturalRegeneration true', 300);
        [prisoner, captor].forEach(bot => bot.quit());
    }
});

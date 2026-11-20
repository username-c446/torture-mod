// Живая проверка допроса: пленный говорит постепенно, сведения видит только допрашивающий.
// Запуск из корня проекта: node tools/bot/check-interrogation.js
const fs = require('fs');
const path = require('path');
const { ROOT, wait, check, run, join, seen, teleport, useOn, openMenu, tail, main } = require('./lib');

const X = 4000, Y = 150, Z = 4000;
const CONFIG = path.join(ROOT, 'platform-forge-1.20.1', 'run', 'config', 'torturemod', 'config.yml');
const BUTTON_SLOT = 19;
// Быстрые отметки соседей и сильный удар, чтобы пройти пороги за пару действий.
const TEST_CONFIG = [
    'interrogation:', '  allies:', '    sample_seconds: 1', '    min_samples: 3',
    'torture:', '  types:', '    strike:', '      resistance_drop: 40',
].join('\n');

/** Открывает меню пленного, жмёт «Допросить» и возвращает сообщения, пришедшие палачу. */
async function interrogate(captor, target) {
    const before = captor.msgs.length;
    const window = await openMenu(captor, target);
    if (!window) return [];
    await captor.clickWindow(BUTTON_SLOT, 0, 0);
    await wait(1500);
    return captor.msgs.slice(before);
}

main(async () => {
    const original = fs.existsSync(CONFIG) ? fs.readFileSync(CONFIG, 'utf8') : null;
    const prisoner = await join('Prisoner');
    const captor = await join('Captor');
    const friend = await join('Friend');
    try {
        fs.writeFileSync(CONFIG, (original || '') + '\n' + TEST_CONFIG + '\n', 'utf8');
        await run('torturemod reload', 1000);
        await run('gamemode creative @a', 300);
        for (const command of ['gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'time set 1000',
            'weather clear', 'difficulty peaceful', 'gamerule naturalRegeneration false']) {
            await run(command, 300);
        }
        await run(`forceload add ${X} ${Z} ${X + 16} ${Z + 16}`, 4000);
        await run(`fill ${X - 2} ${Y - 1} ${Z - 2} ${X + 8} ${Y + 5} ${Z + 8} minecraft:air`, 300);
        await run(`fill ${X} ${Y} ${Z} ${X + 6} ${Y} ${Z + 6} minecraft:stone`, 300);
        for (const name of ['Prisoner', 'Captor', 'Friend']) {
            await run(`torturemod release ${name}`, 200);
            await run(`clear ${name}`, 200);
            await run(`effect clear ${name}`, 200);
            await run(`effect give ${name} minecraft:instant_health 1 10`, 200);
        }
        await run('team remove Bandits', 200);
        await run('team add Bandits', 200);
        await run('team join Bandits Prisoner', 300);
        await run('team join Bandits Friend', 300);
        await run(`spawnpoint Prisoner ${X + 1} ${Y + 1} ${Z + 1}`, 300);
        await run('give Prisoner minecraft:diamond 3', 200);
        await run('item replace entity Prisoner enderchest.0 with minecraft:emerald 5', 200);
        await run('give Captor minecraft:iron_sword', 200);
        await teleport(prisoner, X + 1.5, Y + 1, Z + 1.5);
        await teleport(friend, X + 2.5, Y + 1, Z + 2.5);
        // Пленный и друг какое-то время вместе до плена: так друг становится частым соседом.
        await wait(5000);
        await teleport(friend, X + 300.5, Y + 60, Z + 300.5);
        await teleport(captor, X + 3.5, Y + 1, Z + 1.5);
        await run('gamemode survival Prisoner', 300);
        await run('gamemode survival Captor', 300);
        await run('torturemod capture Prisoner', 1500);
        const target = await seen(captor, 'Prisoner');
        if (!target) throw new Error('Captor does not see Prisoner');

        // 1. При полной шкале пленный молчит.
        let lines = await interrogate(captor, target);
        check('при полной шкале пленный молчит', lines.some(line => line.includes('Союзники: молчит'))
            && lines.some(line => line.includes('Инвентарь: молчит')), lines.join(' / '));

        // 2. Около 40 %: называет союзников, но не места.
        await useOn(captor, target, 'iron_sword');
        lines = await interrogate(captor, target);
        check('при 40 % называет команду', lines.some(line => line.includes('команда Bandits: Friend')), lines.join(' / '));
        check('при 40 % называет частого соседа', lines.some(line => line.includes('часто бывал рядом с') && line.includes('Friend')),
            lines.join(' / '));
        check('при 40 % молчит о координатах', lines.some(line => line.includes('Координаты: молчит')), lines.join(' / '));

        // 3. Сломленный рассказывает всё.
        await wait(3500);
        await useOn(captor, target, 'iron_sword');
        lines = await interrogate(captor, target);
        check('сломленный называет точку возрождения',
            lines.some(line => line.includes(`точка возрождения minecraft:overworld ${X + 1} ${Y + 1} ${Z + 1}`)), lines.join(' / '));
        check('сломленный перечисляет инвентарь', lines.some(line => line.includes('minecraft:diamond ×3')), lines.join(' / '));
        check('сломленный перечисляет эндер-сундук', lines.some(line => line.includes('minecraft:emerald ×5')),
            lines.join(' / '));
        check('показания видит только допрашивающий', !friend.msgs.some(line => line.includes('Показания'))
            && !prisoner.msgs.some(line => line.includes('Показания')), friend.msgs.slice(-1).join(''));
        const list = await run('torturemod list', 1000);
        check('пленный остаётся в плену после допроса', /Prisoner: /.test(list), tail(list, 1));
    } finally {
        if (original === null) {
            if (fs.existsSync(CONFIG)) fs.unlinkSync(CONFIG);
        } else {
            fs.writeFileSync(CONFIG, original, 'utf8');
        }
        await run('torturemod reload', 500);
        await run('torturemod release Prisoner', 300);
        await run('team remove Bandits', 300);
        await run('gamerule naturalRegeneration true', 300);
        [prisoner, captor, friend].forEach(bot => bot.quit());
    }
});

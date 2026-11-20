// Живая проверка побега: серия прыжков и приседаний, неудачная попытка, освобождение ножницами.
// Запуск из корня проекта: node tools/bot/check-escape.js
const fs = require('fs');
const path = require('path');
const { ROOT, wait, check, run, join, seen, teleport, listed, tail, main } = require('./lib');

const X = 5000, Y = 150, Z = 5000;
const CONFIG = path.join(ROOT, 'platform-forge-1.20.1', 'run', 'config', 'torturemod', 'config.yml');
// Частые попытки с гарантированным шансом, чтобы проверка шла секунды, а не минуты.
const TEST_CONFIG = [
    'escape:', '  attempt_interval_seconds: 5', '  max_chance_percent: 100', '  sequence_seconds: 12',
    '  rescue:', '    seconds: 3',
].join('\n');

/** Ждёт серию от сервера и возвращает её шаги. */
async function waitForSequence(bot, ms = 20000) {
    const from = bot.msgs.length;
    const deadline = Date.now() + ms;
    while (Date.now() < deadline) {
        const line = bot.msgs.slice(from).find(message => message.includes('Повторите:'));
        if (line) return line.split('Повторите:')[1].trim().split(' → ');
        await wait(300);
    }
    return null;
}

async function perform(bot, step) {
    const control = step === 'прыжок' ? 'jump' : 'sneak';
    bot.setControlState(control, true);
    await wait(350);
    bot.setControlState(control, false);
    await wait(control === 'jump' ? 900 : 400);
}

main(async () => {
    const original = fs.existsSync(CONFIG) ? fs.readFileSync(CONFIG, 'utf8') : null;
    const prisoner = await join('Prisoner');
    const helper = await join('Friend');
    try {
        fs.writeFileSync(CONFIG, (original || '') + '\n' + TEST_CONFIG + '\n', 'utf8');
        await run('torturemod reload', 1000);
        await run('gamemode creative @a', 300);
        for (const command of ['gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'time set 1000',
            'weather clear', 'difficulty peaceful']) {
            await run(command, 300);
        }
        await run(`forceload add ${X} ${Z} ${X + 16} ${Z + 16}`, 4000);
        await run(`fill ${X - 2} ${Y - 1} ${Z - 2} ${X + 8} ${Y + 5} ${Z + 8} minecraft:air`, 300);
        await run(`fill ${X} ${Y} ${Z} ${X + 6} ${Y} ${Z + 6} minecraft:stone`, 300);
        for (const name of ['Prisoner', 'Friend']) {
            await run(`torturemod release ${name}`, 200);
            await run(`clear ${name}`, 200);
        }
        await teleport(prisoner, X + 1.5, Y + 1, Z + 1.5);
        await teleport(helper, X + 3.5, Y + 1, Z + 1.5);
        await run('gamemode survival @a', 300);
        await run('give Friend minecraft:shears', 300);

        // 1. Верная серия и выигранный шанс: пленный выскальзывает из наручников, плен остаётся.
        await run('torturemod capture Prisoner', 1000);
        let steps = await waitForSequence(prisoner);
        check('пленному приходит серия для побега', steps !== null, steps && steps.join(' → '));
        if (steps) {
            for (const step of steps) await perform(prisoner, step);
        }
        await wait(1000);
        let list = await run('torturemod list', 1000);
        check('верная серия снимает наручники, но не плен', listed(list, 'Prisoner')
            && prisoner.msgs.some(m => m.includes('выскользнули из наручников')),
            prisoner.msgs.slice(-2).join(' / '));

        // 2. Неверный шаг — неудача и пауза дольше обычной. Новый захват снова надевает наручники.
        await run('torturemod release Prisoner', 500);
        await run('torturemod capture Prisoner', 1000);
        steps = await waitForSequence(prisoner);
        if (steps) {
            await perform(prisoner, steps[0] === 'прыжок' ? 'присед' : 'прыжок');
        }
        await wait(1000);
        list = await run('torturemod list', 1000);
        check('неверный шаг не освобождает', listed(list, 'Prisoner'), tail(list, 1));
        check('после неудачи следующая попытка позже', prisoner.msgs.some(m => m.includes('Следующая попытка через 10 с')),
            prisoner.msgs.slice(-1)[0]);

        // 3. Помощник ножницами перекусывает наручники, затем освобождает.
        const target = await seen(helper, 'Prisoner');
        if (!target) throw new Error('Friend does not see Prisoner');
        await helper.equip(helper.inventory.items().find(item => item.name === 'shears'), 'hand');
        await helper.lookAt(target.position.offset(0, 1.2, 0));
        // Нажимаем чаще, чем пауза, прерывающая освобождение (1,5 с), и дольше, чем наручники (5 с) и путы (3 с).
        for (let i = 0; i < 17; i++) {
            helper.activateEntity(target);
            await wait(600);
        }
        await wait(1000);
        list = await run('torturemod list', 1000);
        check('ножницы перекусывают наручники', prisoner.msgs.some(m => m.includes('Friend перекусывает ваши наручники')),
            prisoner.msgs.slice(-3).join(' / '));
        check('ножницы помощника освобождают', !listed(list, 'Prisoner') && helper.msgs.some(m => m.includes('освобождён')),
            helper.bars.slice(-2).join(' / '));
        check('пленный знает, что его освобождают', prisoner.msgs.some(m => m.includes('Friend пытается вас освободить')),
            prisoner.msgs.slice(-2).join(' / '));
        const shears = helper.inventory.items().find(item => item.name === 'shears');
        check('ножницы изнашиваются', shears && shears.durabilityUsed > 0, `износ ${shears && shears.durabilityUsed}`);
    } finally {
        if (original === null) {
            if (fs.existsSync(CONFIG)) fs.unlinkSync(CONFIG);
        } else {
            fs.writeFileSync(CONFIG, original, 'utf8');
        }
        await run('torturemod reload', 500);
        await run('torturemod release Prisoner', 300);
        [prisoner, helper].forEach(bot => bot.quit());
    }
});

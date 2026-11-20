// Общая обвязка живых проверок: сервер разработки, консоль, ванильные боты и итог.
// Нужен run/server.properties с online-mode=false: боты заходят без лицензии.
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');

const ROOT = path.resolve(__dirname, '../..');
const PORT = 25565;
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));

const state = { server: null, exited: null, lines: [], results: [] };

function check(name, ok, detail = '') {
    state.results.push({ name, ok, detail });
    console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' :: ' + detail : ''}`);
}

function startServer() {
    const server = spawn('cmd.exe', ['/c', path.join(ROOT, 'gradlew.bat'), ':platform-forge-1.20.1:runServer',
        '--console=plain'], { cwd: ROOT });
    const collect = data => data.toString('utf8').split(/\r?\n/).filter(Boolean).forEach(line => state.lines.push(line));
    server.stdout.on('data', collect);
    server.stderr.on('data', collect);
    state.server = server;
    state.exited = new Promise(resolve => server.once('exit', resolve));
    state.startMark = state.lines.length;
}

async function waitFor(pattern, ms, from = 0) {
    const deadline = Date.now() + ms;
    while (Date.now() < deadline) {
        if (state.lines.slice(from).some(line => pattern.test(line))) return true;
        await wait(500);
    }
    return false;
}

/**
 * Ждёт запуска сервера. Сразу после другой сборки Gradle иногда останавливает свой демон
 * («stop command received»), поэтому такой сбой повторяем.
 */
async function waitForStart(retries = 2) {
    for (let attempt = 0; ; attempt++) {
        await waitFor(/Done \(|stop command received|BUILD FAILED/, 300000, state.startMark);
        const output = state.lines.slice(state.startMark);
        if (output.some(line => /Done \(/.test(line))) {
            // Сервер пишет «Done» чуть раньше, чем начинает принимать игроков и команды консоли:
            // ждём, пока консоль ответит, иначе первая команда сценария может потеряться.
            await wait(3000);
            for (let i = 0; i < 10; i++) {
                if (/players online/.test(await run('list', 1000))) return;
            }
            throw new Error('server console does not answer');
        }
        if (attempt >= retries || !output.some(line => /stop command received/.test(line))) {
            throw new Error('server did not start');
        }
        console.log('Gradle stopped its daemon, starting the server again');
        await Promise.race([state.exited, wait(15000)]);
        await wait(3000);
        startServer();
    }
}

async function stopServer(ms = 60000) {
    if (state.server && state.server.exitCode === null) {
        state.server.stdin.write('stop\n');
        await Promise.race([state.exited, wait(ms)]);
    }
}

/**
 * Команда в консоль сервера: возвращает строки, которые сервер вывел за время ожидания.
 * Если сервер подтормаживает и ещё печатает ответ, ждём, пока вывод не затихнет.
 */
async function run(command, ms = 1000) {
    const mark = state.lines.length;
    state.server.stdin.write(command + '\n');
    await wait(ms);
    const deadline = Date.now() + 3000;
    let seen = state.lines.length;
    while (Date.now() < deadline) {
        await wait(300);
        if (state.lines.length === seen) break;
        seen = state.lines.length;
    }
    return state.lines.slice(mark).join('\n');
}

function join(username) {
    return new Promise((resolve, reject) => {
        const bot = mineflayer.createBot({ host: '127.0.0.1', port: PORT, username, version: '1.20.1', auth: 'offline' });
        bot.msgs = [];
        // Ресурспак сервер предлагает сразу при входе, поэтому слушаем пакет с подключения.
        bot.packs = [];
        bot._client.on('resource_pack_send', packet => bot.packs.push(packet));
        bot.bars = [];
        bot.barTitles = [];
        bot.on('messagestr', (message, position) => (position === 'game_info' ? bot.bars : bot.msgs).push(message));
        bot.on('actionBar', json => bot.bars.push(json.toString()));
        bot.on('bossBarCreated', bar => bot.barTitles.push(bar.title.toString()));
        bot.on('bossBarUpdated', bar => bot.barTitles.push(bar.title.toString()));
        // Воздух быстро восстанавливается, поэтому запоминаем минимум, который видел клиент.
        bot.minOxygen = 20;
        // Бот возрождается сразу, поэтому смерти считаем с момента подключения.
        bot.deaths = 0;
        bot.on('death', () => { bot.deaths++; });
        bot.on('breath', () => { bot.minOxygen = Math.min(bot.minOxygen, bot.oxygenLevel); });
        bot.once('spawn', () => resolve(bot));
        bot.once('kicked', reason => reject(new Error(`${username} kicked: ${JSON.stringify(reason)}`)));
        bot.once('error', reject);
    });
}

/** Ждёт, пока сервер пришлёт боту сущность игрока рядом: после телепорта это занимает время. */
async function seen(bot, username, ms = 15000) {
    const deadline = Date.now() + ms;
    while (Date.now() < deadline) {
        const entity = bot.players[username] && bot.players[username].entity;
        if (entity && entity.position.distanceTo(bot.entity.position) < 8) return entity;
        await wait(500);
    }
    const other = bot.players[username] && bot.players[username].entity;
    console.log(`DEBUG ${bot.username} at ${bot.entity.position}, ${username} ${other ? 'at ' + other.position : 'not tracked'}`);
    return null;
}

/**
 * Телепортирует бота и ждёт, пока клиент примет новое место. После дальнего телепорта mineflayer
 * иногда продолжает слать старые координаты, поэтому команду повторяем.
 */
async function teleport(bot, x, y, z, attempts = 5) {
    const { Vec3 } = require('vec3');
    const target = new Vec3(x, y, z);
    for (let i = 0; i < attempts; i++) {
        await run(`tp ${bot.username} ${x} ${y} ${z}`, 300);
        const deadline = Date.now() + 4000;
        while (Date.now() < deadline) {
            if (bot.entity && bot.entity.position.distanceTo(target) < 1.5) {
                await wait(500);
                return true;
            }
            await wait(250);
        }
    }
    console.log(`DEBUG ${bot.username} did not accept teleport, at ${bot.entity && bot.entity.position}`);
    return false;
}

/** Применяет предмет из инвентаря бота к цели, как ПКМ игрока. */
async function useOn(bot, target, item) {
    const stack = bot.inventory.items().find(found => found.name === item);
    if (!stack) throw new Error(`${bot.username} has no ${item}`);
    await bot.equip(stack, 'hand');
    await bot.lookAt(target.position.offset(0, 1.2, 0));
    bot.activateEntity(target);
    await wait(1200);
}

/** Ответ сервера на data get: консоль может отвечать с задержкой, поэтому ищем нужную строку. */
/** Shift + ПКМ по цели; возвращает открывшееся окно или null. */
function openMenu(bot, target) {
    return new Promise(resolve => {
        const timer = setTimeout(() => resolve(null), 5000);
        bot.once('windowOpen', window => {
            clearTimeout(timer);
            resolve(window);
        });
        bot.setControlState('sneak', true);
        setTimeout(() => {
            bot.activateEntity(target);
            setTimeout(() => bot.setControlState('sneak', false), 500);
        }, 300);
    });
}

/** Ждёт ближайшую сущность заданного типа, например strider. */
async function nearest(bot, name, ms = 15000) {
    const deadline = Date.now() + ms;
    while (Date.now() < deadline) {
        const entity = bot.nearestEntity(e => e.name === name);
        if (entity) return entity;
        await wait(500);
    }
    return null;
}

const count = (bot, name) => bot.inventory.items().filter(item => item.name === name)
    .reduce((sum, item) => sum + item.count, 0);
const said = (bot, text) => bot.msgs.some(message => message.includes(text));
const shown = (bot, text) => bot.bars.some(bar => bar.includes(text));
const listed = (output, name) => output.includes(`${name}: `);
const tail = (output, lines = 2) => output.split('\n').slice(-lines).join(' / ');

/** Запускает сервер, проходит сценарий и печатает итог. Полный лог сервера пишет в last-run-<сценарий>.log. */
async function main(scenario) {
    startServer();
    try {
        await waitForStart();
        await scenario();
    } catch (e) {
        check('сценарий завершился без ошибок', false, e.stack || e.message);
    } finally {
        const log = `last-run-${path.basename(process.argv[1], '.js')}.log`;
        fs.writeFileSync(path.join(__dirname, log), state.lines.join('\n'), 'utf8');
        const errors = state.lines.filter(line => /Exception|ERROR/.test(line) && /torturemod/i.test(line));
        check('в логе нет ошибок мода', errors.length === 0, errors.slice(0, 3).join(' / '));
        await stopServer();
        const failed = state.results.filter(result => !result.ok).length;
        console.log(`\n${state.results.length - failed}/${state.results.length} checks passed`);
        process.exit(failed === 0 ? 0 : 1);
    }
}

module.exports = {
    ROOT, wait, check, startServer, waitForStart, stopServer, run, join, seen, nearest, teleport, useOn, openMenu,
    count, said, shown, listed, tail, main,
};

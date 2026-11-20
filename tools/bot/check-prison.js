// Живая проверка тюрьмы: разметка без команд, пульт, сундуки, наручники, зоны, норма, выкуп,
// побег с тревогой, мобы на службе и справка.
// Запуск из корня проекта: node tools/bot/check-prison.js
const fs = require('fs');
const path = require('path');
const { Vec3 } = require('vec3');
const { ROOT, wait, check, run, join, seen, teleport, count, said, shown, listed, tail, main } = require('./lib');

const X = 7000, Y = 150, Z = 7000;
const ZOMBIE = '00000001-0000-0002-0000-000300000004';
const CONFIG = path.join(ROOT, 'platform-forge-1.20.1', 'run', 'config', 'torturemod', 'config.yml');
// Зомби ломается с одного удара, чтобы проверить службу мобов без долгой пытки.
const TEST_CONFIG = ['torture:', '  types:', '    strike:', '      resistance_drop: 1.0',
    'resistance:', '  mobs:', '    minecraft:zombie:', '      max: 0.1',
    'resource_pack:', '  url: "http://127.0.0.1:8099/pack.zip"'].join('\n');

const at = (dx, dy, dz) => new Vec3(X + dx, Y + dy, Z + dz);

async function hold(bot, item) {
    const stack = bot.inventory.items().find(found => found.name === item);
    if (!stack) throw new Error(`${bot.username} has no ${item}`);
    await bot.equip(stack, 'hand');
    // Сервер должен узнать о смене предмета в руке раньше, чем бот нажмёт.
    await wait(500);
}

async function emptyHand(bot) {
    const free = [...Array(9).keys()].find(slot => !bot.inventory.slots[36 + slot]);
    bot.setQuickBarSlot(free === undefined ? 8 : free);
    await wait(300);
}

async function sneaking(bot, action) {
    bot.setControlState('sneak', true);
    await wait(400);
    await action();
    await wait(400);
    bot.setControlState('sneak', false);
    await wait(300);
}

/** ЛКМ по блоку: начало ломания, затем отмена, как короткое нажатие. */
async function leftClick(bot, pos) {
    await bot.lookAt(pos.offset(0.5, 0.5, 0.5));
    bot._client.write('block_dig', { status: 0, location: pos, face: 1, sequence: 0 });
    await wait(100);
    bot._client.write('block_dig', { status: 1, location: pos, face: 1, sequence: 0 });
    bot.swingArm();
    await wait(700);
}

async function rightClick(bot, pos) {
    await bot.lookAt(pos.offset(0.5, 0.5, 0.5));
    await bot.activateBlock(bot.blockAt(pos));
    await wait(800);
}

/** Действие, после которого должно открыться окно; возвращает окно или null. */
function expectWindow(bot, action, ms = 5000) {
    return new Promise(resolve => {
        const timer = setTimeout(() => resolve(null), ms);
        bot.once('windowOpen', window => {
            clearTimeout(timer);
            setTimeout(() => resolve(window), 300);
        });
        action();
    });
}

/** Перекладывает всю стопку предмета из инвентаря в первый свободный слот окна. */
async function put(bot, window, item) {
    const from = window.slots.findIndex((slot, index) => index >= window.inventoryStart && slot && slot.name === item);
    if (from < 0) throw new Error(`${bot.username} has no ${item} in window`);
    const to = window.slots.findIndex((slot, index) => index < window.inventoryStart && !slot);
    await bot.clickWindow(from, 0, 0);
    await bot.clickWindow(to, 0, 0);
    await wait(300);
}

async function click(bot, window, slot) {
    return expectWindow(bot, () => bot.clickWindow(slot, 0, 0).catch(() => {}));
}

main(async () => {
    const original = fs.existsSync(CONFIG) ? fs.readFileSync(CONFIG, 'utf8') : null;
    // Конфиг до входа ботов: ресурспак предлагается при входе.
    fs.writeFileSync(CONFIG, (original || '') + '\n' + TEST_CONFIG + '\n', 'utf8');
    await run('torturemod reload', 1500);
    const owner = await join('Owner');
    const prisoner = await join('Prisoner');
    const buyer = await join('Buyer');
    const bots = [owner, prisoner, buyer];
    let book = false;
    owner._client.on('open_book', () => { book = true; });
    await wait(1500);
    const pack = owner.packs[0] || null;
    try {
        for (const command of ['gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'time set 1000',
            'weather clear', 'difficulty easy', 'gamemode creative @a', 'op Owner']) {
            await run(command, 300);
        }
        await run(`forceload add ${X - 16} ${Z - 16} ${X + 48} ${Z + 48}`, 5000);
        await run(`kill @e[type=minecraft:zombie,x=${X},y=${Y},z=${Z},distance=..80]`, 300);
        await run(`fill ${X - 8} ${Y} ${Z - 8} ${X + 40} ${Y + 8} ${Z + 40} minecraft:air`, 300);
        await run(`fill ${X - 8} ${Y - 1} ${Z - 8} ${X + 40} ${Y - 1} ${Z + 40} minecraft:stone`, 300);
        for (const name of ['Owner', 'Prisoner', 'Buyer']) {
            await run(`torturemod release ${name}`, 200);
            await run(`clear ${name}`, 200);
        }
        // Тюрьма прошлого прогона мешает разметке.
        await run(`torturemod prison delete minecraft:overworld ${X + 5} ${Y} ${Z + 5}`, 800);
        await run(`setblock ${X + 20} ${Y + 5} ${Z + 20} minecraft:stone`, 200);
        await run(`setblock ${X + 18} ${Y + 3} ${Z + 18} minecraft:stone`, 200);
        await run(`setblock ${X + 10} ${Y} ${Z + 3} minecraft:lectern`, 200);
        await run(`setblock ${X + 5} ${Y} ${Z + 5} minecraft:chest`, 200);
        await run(`setblock ${X + 7} ${Y} ${Z + 5} minecraft:chest`, 200);
        for (const [item, n] of [['stick', 1], ['chain', 1], ['wooden_sword', 1], ['ender_eye', 1],
            ['iron_helmet', 1], ['cobblestone', 4], ['gold_ingot', 2]]) {
            await run(`give Owner minecraft:${item} ${n}`, 200);
        }
        await run('give Prisoner minecraft:cobblestone 4', 200);
        await run('give Buyer minecraft:gold_ingot 3', 200);
        await run('give Buyer minecraft:chain 2', 200);
        await run('give Buyer minecraft:iron_ingot 1', 200);
        await run(`setblock ${X + 2} ${Y} ${Z + 3} minecraft:crafting_table`, 200);
        await teleport(owner, X + 1.5, Y, Z + 1.5);
        await run('gamemode survival Owner', 300);

        // 0. Ресурспак при входе и крафт наручников: цепь, слиток, цепь.
        const info = await run('torturemod resourcepack', 1000);
        check('мод собирает ресурспак и предлагает его при входе', pack && pack.url.endsWith('/pack.zip')
            && info.includes(pack.hash), `${pack && pack.hash} | ${tail(info, 1)}`);
        // Крафтит покупатель: после окна верстака mineflayer путает инвентарь, а дальше он нужен только в окнах.
        await teleport(buyer, X + 2.5, Y, Z + 1.5);
        const table = await expectWindow(buyer, () => rightClick(buyer, at(2, 0, 3)));
        if (!table) throw new Error('crafting table did not open');
        const one = async (item, slot) => {
            const from = table.slots.findIndex((found, index) => index >= table.inventoryStart && found && found.name === item);
            await buyer.clickWindow(from, 0, 0);
            await buyer.clickWindow(slot, 1, 0);
            if (table.selectedItem) await buyer.clickWindow(from, 0, 0);
            await wait(300);
        };
        await one('chain', 1);
        await one('iron_ingot', 2);
        await one('chain', 3);
        await wait(800);
        // Результат берём обычным нажатием и кладём в пустой слот: так mineflayer не теряет инвентарь.
        await buyer.clickWindow(0, 0, 0);
        const empty = table.slots.findIndex((found, index) => index >= table.inventoryStart && !found);
        await buyer.clickWindow(empty, 0, 0);
        await wait(500);
        const cuffsItem = table.slots[empty];
        buyer.closeWindow(table);
        await wait(500);
        check('наручники крафтятся из цепей и слитка', cuffsItem && cuffsItem.count === 1
            && JSON.stringify(cuffsItem.nbt).includes('torturemod:handcuffs') && JSON.stringify(cuffsItem.nbt).includes('740001'),
            JSON.stringify(cuffsItem && cuffsItem.nbt).slice(0, 200));
        await run('gamemode survival Prisoner', 300);
        await run('give Prisoner minecraft:iron_pickaxe', 300);

        // 1. Разметка без команд: углы, сброс, подтверждение.
        await hold(owner, 'stick');
        await leftClick(owner, at(0, -1, 0));
        check('ЛКМ палкой ставит первый угол', said(owner, 'Первый угол'), owner.msgs.slice(-1)[0]);
        await sneaking(owner, async () => owner.swingArm());
        check('Shift+ЛКМ в воздух сбрасывает разметку', said(owner, 'Разметка сброшена'), owner.msgs.slice(-1)[0]);
        await leftClick(owner, at(0, -1, 0));
        await teleport(owner, X + 18.5, Y, Z + 18.5);
        await rightClick(owner, at(20, 5, 20));
        check('ПКМ палкой ставит второй угол и пишет размер', said(owner, 'Область 21×7×21'), owner.msgs.slice(-2).join(' / '));
        await sneaking(owner, async () => owner.activateItem());
        check('Shift+ПКМ в воздух создаёт тюрьму', said(owner, 'создана: 21×7×21'), owner.msgs.slice(-2).join(' / '));
        const prisons = await run('torturemod prison', 1000);
        // Консоль сервера на Windows портит кириллицу, поэтому ищем координаты.
        check('тюрьма видна в списке', prisons.includes(`${X} ${Y - 1} ${Z}`), tail(prisons, 1));

        // 2. Зона внутри тюрьмы.
        await teleport(owner, X + 13.5, Y, Z + 13.5);
        await leftClick(owner, at(12, -1, 12));
        await teleport(owner, X + 17.5, Y, Z + 17.5);
        await rightClick(owner, at(18, 3, 18));
        await sneaking(owner, async () => owner.activateItem());
        check('разметка внутри тюрьмы создаёт зону', said(owner, 'Зона «Зона 1» создана'), owner.msgs.slice(-1)[0]);

        // 3. Пульт и сундуки.
        await teleport(owner, X + 8.5, Y, Z + 3.5);
        await sneaking(owner, async () => rightClick(owner, at(10, 0, 3)));
        check('Shift+ПКМ палкой делает кафедру пультом', said(owner, 'Кафедра стала пультом'), owner.msgs.slice(-1)[0]);
        await sneaking(owner, async () => rightClick(owner, at(5, 0, 5)));
        await sneaking(owner, async () => rightClick(owner, at(7, 0, 5)));
        await sneaking(owner, async () => rightClick(owner, at(7, 0, 5)));
        await sneaking(owner, async () => rightClick(owner, at(7, 0, 5)));
        check('Shift+ПКМ палкой меняет роль сундука', said(owner, 'Сундук сдачи') && said(owner, 'Сундук выкупа'),
            owner.msgs.slice(-2).join(' / '));
        await emptyHand(owner);
        let menu = await expectWindow(owner, () => rightClick(owner, at(10, 0, 3)));
        check('ПКМ по пульту открывает меню тюрьмы', menu && JSON.stringify(menu.title).includes('Пульт'),
            menu && JSON.stringify(menu.title));
        // Норма: 4 булыжника.
        menu = await click(owner, menu, 13);
        const samples = await click(owner, menu, 10);
        await put(owner, samples, 'cobblestone');
        owner.closeWindow(samples);
        await wait(1000);
        check('образцы задают норму и возвращаются', said(owner, 'Норма: 4 cobblestone') && count(owner, 'cobblestone') === 4,
            owner.msgs.slice(-1)[0]);

        // 4. Пленный в тюрьме: приписка и наручники.
        await teleport(prisoner, X + 14.5, Y, Z + 14.5);
        await run('torturemod capture Prisoner', 1000);
        await wait(1500);
        check('пленный внутри тюрьмы становится её заключённым', said(prisoner, 'Вы заключённый тюрьмы'),
            prisoner.msgs.slice(-3).join(' / '));
        check('при захвате надеваются наручники', said(prisoner, 'надели наручники'));
        await teleport(owner, X + 12.5, Y, Z + 14.5);
        let target = await seen(owner, 'Prisoner');
        await emptyHand(owner);
        for (let i = 0; i < 5; i++) {
            await owner.lookAt(target.position.offset(0, 1.2, 0));
            owner.activateEntity(target);
            await wait(900);
        }
        check('тюремщик снимает наручники пустой рукой', shown(owner, 'сняты и у вас в руках') && count(owner, 'chain') === 2,
            owner.bars.slice(-1)[0]);

        // 5. Флаги: в зоне ломать можно, вне зоны нельзя.
        await hold(prisoner, 'iron_pickaxe');
        await prisoner.lookAt(at(15, -1, 15).offset(0.5, 0.5, 0.5));
        try {
            await prisoner.dig(prisoner.blockAt(at(15, -1, 15)), true);
        } catch (e) { /* сервер мог отменить ломание */ }
        await wait(800);
        let block = await run(`execute if block ${X + 15} ${Y - 1} ${Z + 15} minecraft:air`, 800);
        check('без наручников в рабочей зоне можно ломать', /Test passed/.test(block), tail(block, 1));
        await teleport(prisoner, X + 8.5, Y, Z + 8.5);
        try {
            await prisoner.dig(prisoner.blockAt(at(9, -1, 8)), true);
        } catch (e) { /* ожидаемо */ }
        await wait(800);
        block = await run(`execute if block ${X + 9} ${Y - 1} ${Z + 8} minecraft:stone`, 800);
        check('вне зоны ломать нельзя', /Test passed/.test(block), tail(block, 1) + ' | ' + prisoner.bars.slice(-1)[0]);

        // 6. Норма: пленный сдаёт булыжник в сундук сдачи.
        await teleport(prisoner, X + 5.5, Y, Z + 3.5);
        const deposit = await expectWindow(prisoner, () => rightClick(prisoner, at(5, 0, 5)));
        check('сундук сдачи открывает меню сдачи', deposit && JSON.stringify(deposit.title).includes('Сдача'),
            deposit && JSON.stringify(deposit.title));
        if (deposit) {
            await put(prisoner, deposit, 'cobblestone');
            prisoner.closeWindow(deposit);
            await wait(1000);
        }
        const stored = await run(`data get block ${X + 5} ${Y} ${Z + 5} Items`, 800);
        check('сданное лежит в сундуке и норма выполнена', said(prisoner, 'Норма выполнена') && /cobblestone/.test(stored),
            prisoner.msgs.slice(-1)[0]);

        // 7. Выкуп: цена через пульт, оплата через сундук выкупа.
        await teleport(owner, X + 8.5, Y, Z + 3.5);
        await emptyHand(owner);
        menu = await expectWindow(owner, () => rightClick(owner, at(10, 0, 3)));
        menu = await click(owner, menu, 10);
        menu = await click(owner, menu, 0);
        const price = await click(owner, menu, 19);
        await put(owner, price, 'gold_ingot');
        owner.closeWindow(price);
        await wait(1000);
        check('цена выкупа задаётся образцами', said(owner, 'Цена за Prisoner: 2 gold_ingot'), owner.msgs.slice(-1)[0]);
        await teleport(buyer, X + 7.5, Y, Z + 3.5);
        let ransom = await expectWindow(buyer, () => rightClick(buyer, at(7, 0, 5)));
        check('сундук выкупа показывает пленных с ценой', ransom && JSON.stringify(ransom.title).includes('Выкуп'),
            ransom && JSON.stringify(ransom.title));
        const pay = ransom && await click(buyer, ransom, 0);
        if (pay) {
            await put(buyer, pay, 'gold_ingot');
            buyer.closeWindow(pay);
            await wait(1500);
        }
        let list = await run('torturemod list', 1000);
        check('оплата освобождает пленного, сдача возвращается', !listed(list, 'Prisoner') && said(prisoner, 'выкупил вас')
            && count(buyer, 'gold_ingot') === 1, `${tail(list, 1)}; gold ${count(buyer, 'gold_ingot')}`);
        const paid = await run(`data get block ${X + 7} ${Y} ${Z + 5} Items`, 800);
        check('оплата лежит в сундуке выкупа', /gold_ingot/.test(paid), tail(paid, 1));

        // 8. Побег за границы на глазах охраны.
        await teleport(prisoner, X + 18.5, Y, Z + 10.5);
        await run('torturemod capture Prisoner', 1000);
        await teleport(owner, X + 19.5, Y, Z + 14.5);
        await teleport(prisoner, X + 23.5, Y, Z + 10.5);
        await wait(2500);
        list = await run('torturemod list', 1000);
        check('выход за границы освобождает', !listed(list, 'Prisoner'), tail(list, 1));
        check('охрана видит побег и получает тревогу', said(owner, 'Тревога!') && said(prisoner, 'охрана вас заметила'),
            owner.msgs.slice(-1)[0]);

        // 9. Мобы на службе: зомби ломается, око Эндера подчиняет, режим и снаряжение.
        await run('time set 18000', 300);
        await run('gamemode creative Owner', 300);
        await teleport(owner, X + 8.5, Y, Z + 12.5);
        await run(`summon minecraft:zombie ${X + 10} ${Y} ${Z + 12} {UUID:[I;1,2,3,4],PersistenceRequired:1b,NoAI:1b}`, 800);
        await run(`torturemod capture ${ZOMBIE}`, 800);
        const zombie = owner.nearestEntity(entity => entity.name === 'zombie');
        if (!zombie) throw new Error('no zombie');
        // Консоль портит кириллицу, поэтому подпись читаем у клиента бота.
        const label = () => JSON.stringify(zombie.metadata);
        const zombieHere = async () => {
            await run(`tp ${ZOMBIE} ${X + 10} ${Y} ${Z + 12}`, 300);
            await wait(800);
        };
        // Пока проверяем нажатия, ИИ зомби выключен: иначе он уходит из досягаемости. Перед проверкой охраны включаем.
        await zombieHere();
        await hold(owner, 'wooden_sword');
        await owner.lookAt(zombie.position.offset(0, 1.2, 0));
        owner.activateEntity(zombie);
        await wait(1200);
        await zombieHere();
        await hold(owner, 'ender_eye');
        await owner.lookAt(zombie.position.offset(0, 1.2, 0));
        owner.activateEntity(zombie);
        await wait(1500);
        let name = label();
        check('око Эндера подчиняет сломленного моба', said(owner, 'служит тюрьме') && /Охрана/.test(name),
            name.slice(0, 120) + ' | ' + owner.bars.slice(-3).join(' / ') + ' | ' + owner.msgs.slice(-3).join(' / '));
        await zombieHere();
        await emptyHand(owner);
        await sneaking(owner, async () => {
            await owner.lookAt(zombie.position.offset(0, 1.2, 0));
            owner.activateEntity(zombie);
            await wait(600);
        });
        await wait(1000);
        name = label();
        check('Shift+ПКМ рукой меняет режим моба', /Свита/.test(name), name.slice(0, 120));
        // Поза приседа доходит до сервера с задержкой: ждём, чтобы ПКМ не считался Shift+ПКМ.
        await wait(1500);
        await zombieHere();
        await hold(owner, 'iron_helmet');
        await owner.lookAt(zombie.position.offset(0, 1.2, 0));
        owner.activateEntity(zombie);
        await wait(1200);
        const armor = await run(`data get entity ${ZOMBIE} ArmorItems`, 800);
        check('ПКМ бронёй надевает её на моба', /iron_helmet/.test(armor), tail(armor, 1) + ' | ' + owner.bars.slice(-2).join(' / '));
        // Обратно на пост: за мной → курьер → на посту.
        await emptyHand(owner);
        for (let i = 0; i < 2; i++) {
            await wait(1000);
            await zombieHere();
            await sneaking(owner, async () => {
                await owner.lookAt(zombie.position.offset(0, 1.2, 0));
                owner.activateEntity(zombie);
                await wait(600);
            });
        }
        await run(`data merge entity ${ZOMBIE} {NoAI:0b}`, 300);
        // Охрана атакует беглеца: тюремщик далеко и не видит, пленный выходит рядом с зомби.
        await teleport(owner, X - 6.5, Y, Z - 6.5);
        prisoner.msgs.length = 0;
        await teleport(prisoner, X + 18.5, Y, Z + 12.5);
        await run('torturemod capture Prisoner', 1000);
        await run(`tp ${ZOMBIE} ${X + 19} ${Y} ${Z + 12}`, 300);
        const healthBefore = prisoner.health;
        await teleport(prisoner, X + 22.5, Y, Z + 12.5);
        await wait(6000);
        check('моб-охранник замечает беглеца', said(prisoner, 'охрана вас заметила'), prisoner.msgs.slice(-1)[0]);
        check('моб-охранник атакует беглеца', prisoner.health < healthBefore || prisoner.deaths > 0,
            `${healthBefore} -> ${prisoner.health}; ${label().slice(0, 160)}`);

        // 10. Справка: книга и карты.
        owner.chat('/torturemod help');
        await wait(1500);
        check('справка открывает книгу', book);
        // Карт 28: инвентарь должен быть пуст, иначе часть упадёт на землю.
        await run('clear Owner', 300);
        await wait(1500);
        owner.chat('/torturemod help maps');
        await wait(6000);
        check('картинки справки выдаются картами', count(owner, 'filled_map') >= 20,
            `maps ${count(owner, 'filled_map')}; ${owner.msgs.slice(-2).join(' / ')}`);

        // 11. Удаление тюрьмы через пульт: два нажатия.
        await teleport(owner, X + 8.5, Y, Z + 3.5);
        await emptyHand(owner);
        menu = await expectWindow(owner, () => rightClick(owner, at(10, 0, 3)));
        menu = await click(owner, menu, 15);
        menu = await click(owner, menu, 16);
        await click(owner, menu, 16);
        await wait(800);
        const after = await run('torturemod prison', 1000);
        check('тюрьма удаляется из пульта', said(owner, 'удалена') && !after.includes(`${X} ${Y - 1} ${Z}`), tail(after, 1));
    } finally {
        if (original === null) {
            if (fs.existsSync(CONFIG)) fs.unlinkSync(CONFIG);
        } else {
            fs.writeFileSync(CONFIG, original, 'utf8');
        }
        await run('torturemod reload', 500);
        await run('torturemod release Prisoner', 300);
        await run(`kill ${ZOMBIE}`, 300);
        await run('difficulty peaceful', 300);
        bots.forEach(bot => bot.quit());
    }
});

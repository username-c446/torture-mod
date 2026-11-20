// Живая проверка фоновых задач: перезагрузка конфига и отчёт по предметам отвечают, мод меряет свою нагрузку.
// Запуск из корня проекта: node tools/bot/check-threads.js
const fs = require('fs');
const path = require('path');
const { ROOT, wait, check, run, tail, main } = require('./lib');

const REPORT = path.join(ROOT, 'platform-forge-1.20.1', 'run', 'config', 'torturemod', 'items-report.txt');

main(async () => {
    if (fs.existsSync(REPORT)) fs.unlinkSync(REPORT);

    const reload = await run('torturemod reload', 2000);
    check('перезагрузка конфига в фоне отвечает', /\/INFO\] \[minecraft\/MinecraftServer\]: \S/.test(reload)
        && !/reload_failed|Config reload failed/.test(reload), tail(reload, 1));

    const report = await run('torturemod items-report', 3000);
    const lines = fs.existsSync(REPORT) ? fs.readFileSync(REPORT, 'utf8').split('\n').filter(Boolean) : [];
    check('отчёт по предметам записан в фоне', lines.length > 1000 && lines.some(line => line.startsWith('minecraft:iron_sword\tstrike')),
        `${lines.length} строк; ${tail(report, 1)}`);

    await wait(5000);
    const perf = await run('torturemod perf', 1500);
    const numbers = (tail(perf, 1).match(/\d+(\.\d+)?/g) || []).map(Number);
    check('замер нагрузки мода работает', numbers.length >= 4 && numbers[numbers.length - 4] >= 90, tail(perf, 1));
});

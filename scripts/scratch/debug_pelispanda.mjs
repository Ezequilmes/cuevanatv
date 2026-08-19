import { chromium } from 'playwright';

async function run() {
    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext();
    const page = await context.newPage();
    const url = 'https://pelispanda.org/serie/quien-lo-mato';

    console.log(`Navigating to ${url}...`);
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });
    await page.waitForTimeout(5000);

    // Scroll
    await page.evaluate(() => window.scrollBy(0, 1000));
    await page.waitForTimeout(2000);

    const html = await page.content();
    console.log('--- Page Title ---');
    console.log(await page.title());

    const links = await page.$$eval('a', els => els.map(el => ({ text: el.innerText, href: el.href })));
    const playerLinks = links.filter(l => l.href.includes('/player/'));

    console.log(`Found ${playerLinks.length} player links.`);
    if (playerLinks.length > 0) {
        console.log('Sample player link:', playerLinks[0]);
    } else {
        console.log('NO PLAYER LINKS FOUND. Listing first 20 links:');
        console.log(links.slice(0, 20));
    }

    const buttons = await page.$$eval('button', els => els.map(el => ({ text: el.innerText, class: el.className })));
    console.log('Buttons found:', buttons.length);
    console.log('Sample buttons:', buttons.slice(0, 10));

    await browser.close();
}

run().catch(console.error);

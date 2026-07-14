import "dotenv/config";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { chromium } from "playwright";
import { createClient } from "@supabase/supabase-js";
import { resolveDynamicLink } from "./sync_api_server.mjs";

const rawUrls = (process.env.URLS || "").trim();
const URLS =
  rawUrls.length > 0
    ? rawUrls.split("|").map((u) => u.trim()).filter(Boolean)
    : [pathToFileURL(path.join(process.cwd(), "scripts", "demo_page.html")).toString()];

const headless = (process.env.HEADLESS || "true").toLowerCase() === "true";
const clickSelectors = (process.env.CLICK_SELECTORS || ".play-video,.btn-action")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);
const iframeWaitMs = Number(process.env.IFRAME_WAIT_MS || 20000);
const dryRun = (process.env.DRY_RUN || "").toLowerCase() === "true";

const supabaseUrl = process.env.SUPABASE_URL || "";
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY || "";
const supabase = dryRun ? null : createClient(supabaseUrl, supabaseKey);

async function scrapeData(page, url) {
  await page.goto(url, { waitUntil: "domcontentloaded", timeout: 60000 });

  const title =
    (await page.locator("h1").first().textContent().catch(() => null))?.trim() ||
    (await page.title().catch(() => null)) ||
    null;

  const imageUrl =
    (await page.locator('meta[property="og:image"]').getAttribute("content").catch(() => null)) ||
    (await page.locator("article img").first().getAttribute("src").catch(() => null)) ||
    (await page.locator("img").first().getAttribute("src").catch(() => null)) ||
    null;

  let clicked = false;
  for (const sel of clickSelectors) {
    const el = page.locator(sel).first();
    if (await el.count()) {
      await el.scrollIntoViewIfNeeded().catch(() => {});
      await el.click({ timeout: 15000 }).catch(() => {});
      clicked = true;
      break;
    }
  }

  await page.waitForSelector("iframe", { timeout: iframeWaitMs }).catch(() => {});

  const iframeSrcs = await page.$$eval("iframe", (iframes) =>
    Array.from(
      new Set(
        iframes
          .map((f) => f.getAttribute("src"))
          .filter((s) => typeof s === "string" && s.trim().length > 0)
          .map((s) => s.trim())
      )
    )
  );

  return { source_page_url: url, title, poster_url: imageUrl, iframe_srcs: iframeSrcs, clicked };
}

async function syncToSupabase(data) {
  if (!supabase) return;
  if (!supabaseUrl || !supabaseKey) throw new Error("Faltan SUPABASE_URL o SUPABASE_SERVICE_ROLE_KEY");

  const { error } = await supabase.from("items_sync").upsert(
    {
      source_page_url: data.source_page_url,
      title: data.title,
      poster_url: data.poster_url,
      description: data.description || "",
      iframe_srcs: data.iframe_srcs,
      updated_at: new Date().toISOString(),
    },
    { onConflict: "source_page_url" }
  );

  if (error) throw error;
}

async function main() {
  const needsHeadful = URLS.some((u) => u.includes("voe.sx") || u.includes("ok.ru/video/") || u.includes("ok.ru/videoembed/"));
  const browser = await chromium.launch({ headless: needsHeadful ? false : headless });
  const context = await browser.newContext({
    userAgent:
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123 Safari/537.36",
  });

  try {
    const page = await context.newPage();
    for (const url of URLS) {
      const data = await scrapeData(page, url);
      const resolvedIframes = [];
      for (const src of data.iframe_srcs || []) {
        const resolved = await resolveDynamicLink(browser, src).catch(() => null);
        resolvedIframes.push(resolved || src);
      }
      data.iframe_srcs = [...new Set(resolvedIframes)];
      await syncToSupabase(data);
      const t = data.title ? data.title.replace(/\s+/g, " ").trim() : "null";
      console.log(
        `OK source_page_url=${data.source_page_url} title=${t} clicked=${data.clicked} iframes=${data.iframe_srcs.length}`
      );
      if (dryRun) console.log(JSON.stringify(data, null, 2));
    }
  } finally {
    await context.close();
    await browser.close();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

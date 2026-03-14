import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    },
  })
}

const BASE = "https://www2.gnula.one/"
const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

async function getHtml(url: string): Promise<string | null> {
  try {
    const res = await fetch(url, {
      headers: {
        "User-Agent": UA,
        Referer: BASE,
      },
    })
    if (!res.ok) return null
    return await res.text()
  } catch {
    return null
  }
}

function absUrl(u: string) {
  if (!u) return ""
  if (u.startsWith("//")) return "https:" + u
  if (u.startsWith("/")) return new URL(u, BASE).toString()
  if (u.startsWith("http")) return u
  try {
    return new URL(u, BASE).toString()
  } catch {
    return u
  }
}

function scrapeList(html: string) {
  const items: Array<{ title: string; posterUrl: string; url: string }> = []
  const blocks = html.split(/<\/article>|<\/div>\s*<\/div>/i)
  for (const b of blocks) {
    const a = b.match(/<a[^>]+href=["']([^"']+)["'][^>]*>(.*?)<\/a>/is)
    if (!a) continue
    const href = absUrl(a[1])
    const imgM =
      b.match(/<img[^>]+(?:data-src|src)=["']([^"']+)["'][^>]*>/i) ||
      a[2].match(/<img[^>]+(?:data-src|src)=["']([^"']+)["'][^>]*>/i)
    const titleM =
      b.match(/<(?:h3|h2|span)[^>]*class=["'][^"']*title[^"']*["'][^>]*>(.*?)<\/(?:h3|h2|span)>/is) ||
      b.match(/<h3[^>]*>(.*?)<\/h3>/is) ||
      a[2].match(/alt=["']([^"']+)["']/i)
    const title = titleM ? titleM[1].replace(/<[^>]+>/g, "").trim() : ""
    const poster = imgM ? absUrl(imgM[1]) : ""
    if (title && href) items.push({ title, posterUrl: poster, url: href })
  }
  return items
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
        "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      },
    })
  }
  try {
    const body = await req.json().catch(() => ({} as any))
    const q = (body?.query ?? "").toString().trim()
    const url = q ? `${BASE}?s=${encodeURIComponent(q)}` : BASE
    const html = await getHtml(url)
    if (!html) return json({ items: [] })
    const items = scrapeList(html).slice(0, 40)
    return json({ items })
  } catch {
    return json({ items: [] })
  }
})

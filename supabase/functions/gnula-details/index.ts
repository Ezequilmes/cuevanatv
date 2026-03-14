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

function textBetween(html: string, selRe: RegExp) {
  const m = html.match(selRe)
  if (!m) return ""
  return m[1].replace(/<[^>]+>/g, "").trim()
}

function extractServers(html: string) {
  const out: Array<{ name: string; pageUrl?: string; playableUrl?: string }> = []
  const seen = new Set<string>()
  const btnRe =
    /<(?:button|a|li)[^>]+(?:data-url|data-href|data-link|data-src|href)=["']([^"']+)["'][^>]*>(.*?)<\/(?:button|a|li)>/gis
  let m: RegExpExecArray | null
  while ((m = btnRe.exec(html))) {
    const link = absUrl(m[1])
    const label = m[2].replace(/<[^>]+>/g, "").trim() || "Servidor"
    if (link && !seen.has(link)) {
      seen.add(link)
      out.push({ name: label, pageUrl: link })
    }
  }
  const ifrRe = /<iframe[^>]+src=["']([^"']+)["'][^>]*>/gi
  while ((m = ifrRe.exec(html))) {
    const link = absUrl(m[1])
    if (link && !seen.has(link)) {
      seen.add(link)
      out.push({ name: "Iframe", pageUrl: link })
    }
  }
  const rx = /(https?:\/\/[^\s"'\\]+?(?:m3u8|mp4)[^\s"'\\]*)/gi
  let mx: RegExpExecArray | null
  while ((mx = rx.exec(html))) {
    const pl = mx[1]
    if (pl && !seen.has(pl)) {
      seen.add(pl)
      out.push({ name: "Directo", playableUrl: pl })
    }
  }
  return out
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
    const body = await req.json()
    const url = (body?.url ?? "").toString().trim()
    if (!url) return json({ error: "url requerido" }, 400)
    const html = await getHtml(url)
    if (!html) return json({ error: "No disponible" }, 502)
    const titleM =
      html.match(/<h1[^>]*>(.*?)<\/h1>/is) ||
      html.match(/<meta\s+property=["']og:title["'][^>]*content=["']([^"']+)["']/i)
    const title = titleM ? (titleM[1] || titleM[2] || "").replace(/<[^>]+>/g, "").trim() : ""
    const posterM =
      html.match(/<meta\s+property=["']og:image["'][^>]*content=["']([^"']+)["']/i) ||
      html.match(/<img[^>]+(?:data-src|src)=["']([^"']+)["'][^>]*class=["'][^"']*poster[^"']*["']/i)
    const posterUrl = posterM ? absUrl(posterM[1]) : ""
    const description =
      textBetween(html, /<div[^>]+class=["'][^"']*(?:resumen|sinopsis|description|plot)[^"']*["'][^>]*>([\s\S]*?)<\/div>/i) ||
      textBetween(html, /<div[^>]+id=["']sinopsis["'][^>]*>([\s\S]*?)<\/div>/i)
    const servers = extractServers(html)
    return json({ title, posterUrl, description, servers })
  } catch {
    return json({ error: "Solicitud inválida" }, 400)
  }
})

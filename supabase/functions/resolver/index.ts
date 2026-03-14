import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

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

async function getHtml(url: string, referer?: string): Promise<string | null> {
  try {
    const headers: Record<string, string> = { "User-Agent": UA }
    if (referer) {
      headers["Referer"] = referer
      try {
        const u = new URL(referer)
        headers["Origin"] = `${u.protocol}//${u.host}`
      } catch {}
    }
    const res = await fetch(url, { headers })
    if (!res.ok) return null
    return await res.text()
  } catch {
    return null
  }
}

function findFirstPlayable(text: string): string | null {
  const r = /(https?:\/\/[^\s"'\\]+?\.(?:m3u8|mp4)[^\s"'\\]*)/i
  const m = text.match(r)
  return m ? m[1] : null
}

async function resolveVoe(url: string): Promise<string | null> {
  const html = await getHtml(url)
  if (!html) return null
  const direct = findFirstPlayable(html)
  if (direct) return direct
  const atobR = /atob\(['"]([A-Za-z0-9+/=]+)['"]\)/g
  for (const m of html.matchAll(atobR)) {
    try {
      const decoded = atob(m[1])
      const d = findFirstPlayable(decoded)
      if (d) return d
    } catch {}
  }
  const contentR = /"content"\s*:\s*"(https?:[^"']+?\.(?:m3u8|mp4)[^"]*)"/i
  const mc = html.match(contentR)
  if (mc) return mc[1].replace(/\\\//g, "/")
  return null
}

async function resolveFembed(url: string): Promise<string | null> {
  const html = await getHtml(url)
  if (!html) return null
  const direct = findFirstPlayable(html)
  if (direct) return direct
  const srcR = /"file"\s*:\s*"(https?:[^"']+?\.(?:m3u8|mp4)[^"]*)"/i
  const ms = html.match(srcR)
  if (ms) return ms[1].replace(/\\\//g, "/")
  return null
}

async function resolveStreamwish(url: string): Promise<string | null> {
  const html = await getHtml(url)
  if (!html) return null
  const direct = findFirstPlayable(html)
  if (direct) return direct
  const fileR = /"file"\s*:\s*"(https?:[^"']+?\.(?:m3u8|mp4)[^"]*)"/i
  const mf = html.match(fileR)
  if (mf) return mf[1].replace(/\\\//g, "/")
  const anyR = /(https?:\/\/[^\s"'\\]+?(?:m3u8|mp4)[^\s"'\\]*)/i
  const ma = html.match(anyR)
  if (ma) return ma[1]
  return null
}

async function resolveOkRu(url: string): Promise<string | null> {
  const html = await getHtml(url)
  if (!html) return null
  const direct = findFirstPlayable(html)
  if (direct) return direct
  const hlsR = /"hlsManifestUrl"\s*:\s*"(https?:[^"']+?\.m3u8[^"]*)"/i
  const mh = html.match(hlsR)
  if (mh) return mh[1].replace(/\\\//g, "/")
  const m3u8R = /(https?:\/\/[^"'\\]+?\.m3u8[^"'\\]*)/i
  const m3 = html.match(m3u8R)
  if (m3) return m3[1]
  return null
}

async function resolveByHost(url: string): Promise<string | null> {
  const u = url.toLowerCase()
  if (u.includes("voe.sx") || u.includes("voe.")) return await resolveVoe(url)
  if (u.includes("streamwish") || u.includes("wish")) return await resolveStreamwish(url)
  if (u.includes("fembed") || u.includes("feurl") || u.includes("femax")) return await resolveFembed(url)
  if (u.includes("ok.ru")) return await resolveOkRu(url)
  return null
}

async function resolveGeneric(url: string): Promise<string | null> {
  const html = await getHtml(url)
  if (!html) return null
  const direct = findFirstPlayable(html)
  if (direct) return direct
  const hostUrl = /(https?:\/\/[^\s"'\\]*(voe|streamwish|wish|fembed|ok\.ru)[^\s"'\\]*)/i
  const m = html.match(hostUrl)
  if (m) {
    const link = m[1].replace(/\\\//g, "/")
    const by = await resolveByHost(link)
    if (by) return by
  }
  const ifr = [...html.matchAll(/<iframe[^>]+src=['"]([^"'#?]+[^"']*)['"]/gi)]
  for (const mm of ifr) {
    const src = mm[1]
    const r = await resolveByHost(src)
    if (r) return r
    const h2 = await resolveGeneric(src)
    if (h2) return h2
  }
  return null
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
    const pageUrl = (body?.pageUrl ?? "").toString()
    if (!pageUrl) return json({ error: "pageUrl requerido" }, 400)
    const by = await resolveByHost(pageUrl)
    const playable = by ?? (await resolveGeneric(pageUrl))
    if (!playable) return json({ error: "No se pudo resolver" }, 422)
    return json({ playableUrl: playable }, 200)
  } catch {
    return json({ error: "Solicitud inválida" }, 400)
  }
})

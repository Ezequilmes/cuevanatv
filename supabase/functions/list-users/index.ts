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

async function fetchJson(url: string, init: RequestInit) {
  const res = await fetch(url, init)
  const text = await res.text()
  let data: any = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = null
  }
  return { ok: res.ok, status: res.status, data, text }
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
    const url = Deno.env.get("SUPABASE_URL") || ""
    const srk = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || ""
    if (!url || !srk) return json({ error: "Missing env" }, 500)
    const auth = req.headers.get("authorization") || ""
    if (!auth.startsWith("Bearer ")) return json({ error: "No auth" }, 401)
    const userCheck = await fetchJson(`${url}/auth/v1/user`, {
      headers: { authorization: auth, apikey: srk },
    })
    if (!userCheck.ok) return json({ error: "Invalid JWT" }, 401)
    const userId = userCheck.data?.id || userCheck.data?.user?.id
    if (!userId) return json({ error: "Invalid user" }, 401)
    const adminCheck = await fetchJson(`${url}/rest/v1/admins?user_id=eq.${userId}&select=user_id`, {
      headers: { apikey: srk, authorization: `Bearer ${srk}` },
    })
    if (!adminCheck.ok) return json({ error: "Admin check failed" }, 403)
    if (!Array.isArray(adminCheck.data) || adminCheck.data.length === 0) return json({ error: "Forbidden" }, 403)
    const body = await req.json().catch(() => ({}))
    const sinceHours = typeof body?.sinceHours === "number" ? body.sinceHours : 24
    const now = Date.now()
    const cutoff = new Date(now - sinceHours * 3600 * 1000).toISOString()
    const resp = await fetchJson(`${url}/auth/v1/admin/users?per_page=100`, {
      headers: {
        apikey: srk,
        authorization: `Bearer ${srk}`,
        "Content-Type": "application/json",
      },
    })
    if (!resp.ok) return json({ error: "Auth admin users failed", status: resp.status, body: resp.text }, resp.status)
    const users = Array.isArray(resp.data?.users) ? resp.data.users : resp.data
    const filtered = (users || []).filter((u: any) => {
      const ca = u?.created_at || u?.createdAt
      return ca && ca >= cutoff
    }).map((u: any) => ({
      id: u.id,
      email: u.email,
      created_at: u.created_at || u.createdAt,
      last_sign_in_at: u.last_sign_in_at || u.lastSignInAt || null,
    }))
    return json({ users: filtered })
  } catch {
    return json({ error: "Bad request" }, 400)
  }
})

import "dotenv/config";
import { createClient } from "@supabase/supabase-js";

const { SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY } = process.env;
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

function normalize(s) {
  if (!s) return null;
  try {
    const u = new URL(s);
    const host = u.hostname.toLowerCase();
    const path = u.pathname.replace(/\/+$/, '');
    return `${u.protocol}//${host}${path}`.toLowerCase();
  } catch (e) {
    return s.trim().replace(/\/+$/, '').toLowerCase();
  }
}

async function dedupe() {
  console.log("🚀 INICIANDO DE-DUPLICACIÓN PROFESIONAL...");

  // 1. Obtener todos los títulos
  const { data: titles, error } = await supabase.from('titles').select('id, title, source_page_url, created_at');
  if (error) { console.error(error); return; }

  const map = new Map();

  for (const t of titles || []) {
    const key = normalize(t.source_page_url) || `__no_src__::${t.title.toLowerCase().trim()}`;
    if (!map.has(key)) map.set(key, []);
    map.get(key).push(t);
  }

  let mergedCount = 0;

  for (const [key, group] of map) {
    if (group.length <= 1) continue;

    // Mantener el más antiguo
    group.sort((a,b) => new Date(a.created_at) - new Date(b.created_at));
    const keeper = group[0];
    const toRemove = group.slice(1);

    console.log(`🔗 Unificando ${toRemove.length} duplicados en: ${keeper.title} (${keeper.id})`);

    for (const rem of toRemove) {
      // Reasignar servidores al título principal
      const { error: updErr } = await supabase.from('servers').update({ title_id: keeper.id }).eq('title_id', rem.id);
      if (updErr) console.error(`Error reasignando: ${updErr.message}`);

      // Borrar el título duplicado
      const { error: delErr } = await supabase.from('titles').delete().eq('id', rem.id);
      if (delErr) console.error(`Error borrando: ${delErr.message}`);

      mergedCount++;
    }
  }

  console.log(`✅ DE-DUPLICACIÓN FINALIZADA. Títulos unificados: ${mergedCount}`);
}

dedupe();

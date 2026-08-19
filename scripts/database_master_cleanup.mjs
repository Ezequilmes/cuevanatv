import "dotenv/config";
import { createClient } from "@supabase/supabase-js";

const { SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY } = process.env;
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

async function cleanup() {
  console.log("🚀 INICIANDO LIMPIEZA MAESTRA DE BASE DE DATOS...");

  // 1. Eliminar películas que son en realidad episodios de Vikingos
  console.log("🧹 Eliminando episodios de Vikingos infiltrados en 'titles'...");
  const { error: delViking } = await supabase
    .from('titles')
    .delete()
    .ilike('title', '%Viking%')
    .eq('type', 'movie');

  if (delViking) console.error("Error cleaning Vikingos:", delViking.message);

  // 2. Eliminar registros generados sin portada real (los que tienen ui-avatars)
  console.log("🧹 Eliminando registros sin portada real (placeholder ui-avatars)...");
  const { error: delNoCover } = await supabase
    .from('titles')
    .delete()
    .ilike('poster_url', '%ui-avatars%');

  if (delNoCover) console.error("Error cleaning no-cover items:", delNoCover.message);

  // 3. Eliminar duplicados porplayable_url en títulos (mantener el más viejo)
  console.log("🧹 Eliminando duplicados por URL de video...");
  const { data: allTitles } = await supabase.from('titles').select('id, playable_url, created_at').order('created_at', { ascending: true });

  const seenUrls = new Set();
  const idsToDelete = [];

  for (const t of allTitles || []) {
    if (!t.playable_url) continue;
    if (seenUrls.has(t.playable_url)) {
      idsToDelete.push(t.id);
    } else {
      seenUrls.set(t.playable_url);
    }
  }

  if (idsToDelete.length > 0) {
    console.log(`🧹 Borrando ${idsToDelete.length} duplicados exactos...`);
    await supabase.from('titles').delete().in('id', idsToDelete);
  }

  console.log("✅ LIMPIEZA MAESTRA FINALIZADA.");
}

cleanup();

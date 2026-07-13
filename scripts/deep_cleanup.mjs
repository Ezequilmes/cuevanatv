import "dotenv/config";
import { createClient } from "@supabase/supabase-js";
import path from "path";
import { fileURLToPath } from "url";
import dotenv from "dotenv";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, "../.env") });

const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_ROLE_KEY);

async function deepCleanup() {
  console.log("🚀 INICIANDO LIMPIEZA PROFUNDA (SISTEMA COMPLETO)...");

  // --- 1. LIMPIEZA DE SERVERS (CAPÍTULOS) SIN LÍMITE ---
  console.log("\n--- Pasó 1: Limpiando Capítulos Duplicados ---");
  let allServers = [];
  let from = 0;
  const step = 1000;
  let fetching = true;

  while (fetching) {
    const { data, error } = await supabase
      .from("servers")
      .select("id, title_id, season_number, episode_number")
      .range(from, from + step - 1);

    if (error) { console.error("Error al traer servers:", error.message); break; }
    if (data.length === 0) { fetching = false; break; }

    allServers = allServers.concat(data);
    from += step;
    console.log(`📡 Descargados ${allServers.length} registros de capítulos...`);
  }

  const seenServers = new Map();
  const serversToDelete = [];

  for (const s of allServers) {
    const key = `${s.title_id}|${s.season_number}|${s.episode_number}`;
    if (seenServers.has(key)) {
      serversToDelete.push(s.id);
    } else {
      seenServers.set(key, s.id);
    }
  }

  if (serversToDelete.length > 0) {
    console.log(`🧹 Borrando ${serversToDelete.length} capítulos basura...`);
    for (let i = 0; i < serversToDelete.length; i += 100) {
      const chunk = serversToDelete.slice(i, i + 100);
      await supabase.from("servers").delete().in("id", chunk);
    }
    console.log("✅ Capítulos limpios.");
  } else {
    console.log("✨ No había capítulos duplicados fuera del primer lote.");
  }

  // --- 2. LIMPIEZA DE TITLES (SERIES/CANALES) ---
  console.log("\n--- Pasó 2: Limpiando Títulos Duplicados ---");
  const { data: titles, error: tError } = await supabase
    .from("titles")
    .select("id, title, category, type");

  if (tError) { console.error("Error al traer títulos:", tError.message); return; }

  const seenTitles = new Map();
  const titlesToDelete = [];

  for (const t of titles) {
    const key = `${t.title.toLowerCase().trim()}|${t.type}`;
    if (seenTitles.has(key)) {
      titlesToDelete.push(t.id);
    } else {
      seenTitles.set(key, t.id);
    }
  }

  if (titlesToDelete.length > 0) {
    console.log(`🧹 Borrando ${titlesToDelete.length} títulos duplicados (canales/series)...`);
    for (let i = 0; i < titlesToDelete.length; i += 50) {
      const chunk = titlesToDelete.slice(i, i + 50);
      // Nota: Borrar un título borrará sus capítulos por CASCADE en SQL
      await supabase.from("titles").delete().in("id", chunk);
    }
    console.log("✅ Títulos limpios.");
  }

  console.log("\n🎉 ¡SISTEMA TOTALMENTE SANEADO!");
}

deepCleanup();

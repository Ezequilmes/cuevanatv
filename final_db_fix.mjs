import { createClient } from "@supabase/supabase-js";
import dotenv from "dotenv";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, ".env") });

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

const OLD_DOMAIN = "cuevana-tv-arg.duckdns.org";
const NEW_DOMAIN = "races-reed-spent-refer.trycloudflare.com"; // El túnel activo

async function fixTable(tableName) {
    console.log(`🔍 Buscando registros en ${tableName}...`);
    const { data: records, error } = await supabase
        .from(tableName)
        .select("id, playable_url")
        .like("playable_url", `%${OLD_DOMAIN}%`);

    if (error) {
        console.error(`❌ Error en ${tableName}:`, error.message);
        return;
    }

    if (!records || records.length === 0) {
        console.log(`✅ No hay nada que arreglar en ${tableName}.`);
        return;
    }

    console.log(`🚀 Reparando ${records.length} registros en ${tableName}...`);
    let count = 0;

    for (const rec of records) {
        const newUrl = rec.playable_url.replace(OLD_DOMAIN, NEW_DOMAIN).replace("http://", "https://");
        const { error: updErr } = await supabase
            .from(tableName)
            .update({ playable_url: newUrl })
            .eq("id", rec.id);

        if (!updErr) count++;
        else console.warn(`⚠️ Fallo en ID ${rec.id}:`, updErr.message);
    }
    console.log(`✨ Finalizado ${tableName}: ${count} actualizados.`);
}

(async () => {
    console.log("--- INICIANDO REPARACIÓN MAESTRA DE BASE DE DATOS ---");
    await fixTable("servers");
    await fixTable("titles");
    console.log("--- PROCESO COMPLETADO ---");
})();

import dotenv from "dotenv";
import path from "path";
import { createClient } from "@supabase/supabase-js";

dotenv.config({ path: path.resolve('D:/magik/Mi app/Cuevanatv/.env') });

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

async function consolidate() {
    console.log("🚀 INICIANDO CONSOLIDACIÓN POR TÍTULO EXACTO...");

    // 1. Obtener todas las series
    const { data: allTitles } = await supabase.from('titles').select('id, title, type').eq('type', 'series');

    const titlesMap = new Map(); // title.toLowerCase() -> id_principal
    const toDelete = [];
    let consolidatedCount = 0;

    for (const t of allTitles || []) {
        const normTitle = t.title.toLowerCase().trim();

        if (titlesMap.has(normTitle)) {
            const principalId = titlesMap.get(normTitle);
            console.log(`🔗 Fusionando duplicado: "${t.title}" (ID: ${t.id}) -> ID Principal: ${principalId}`);

            // Reasignar servidores al título principal
            const { error: updErr } = await supabase.from('servers').update({ title_id: principalId }).eq('title_id', t.id);
            if (!updErr) {
                toDelete.push(t.id);
                consolidatedCount++;
            }
        } else {
            titlesMap.set(normTitle, t.id);
        }
    }

    // 2. Eliminar los registros duplicados de la tabla 'titles'
    if (toDelete.length > 0) {
        console.log(`🧹 Borrando ${toDelete.length} registros duplicados de la tabla 'titles'...`);
        const { error: delErr } = await supabase.from('titles').delete().in('id', toDelete);
        if (delErr) console.error("❌ Error al borrar títulos:", delErr.message);
    }

    console.log(`🏁 FINALIZADO. Se consolidaron ${consolidatedCount} series duplicadas.`);
}

consolidate().catch(console.error);

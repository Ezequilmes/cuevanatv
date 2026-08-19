import dotenv from "dotenv";
import path from "path";
import { createClient } from "@supabase/supabase-js";

dotenv.config({ path: path.resolve('D:/magik/Mi app/Cuevanatv/.env') });

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

function normalize(text) {
    if (!text) return "";
    return text.toLowerCase()
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "") // Quitar acentos
        .replace(/[^a-z0-9]/g, "")      // Quitar todo lo que no sea letra o número
        .trim();
}

async function consolidate() {
    console.log("🚀 INICIANDO CONSOLIDACIÓN FUZZY DE SERIES...");

    const { data: allTitles } = await supabase.from('titles').select('id, title, type').eq('type', 'series');

    const titlesMap = new Map(); // normalized_title -> id_principal
    const toDelete = [];
    let consolidatedCount = 0;

    // Ordenamos para que los títulos con acentos o nombres más "completos" tengan prioridad de ser el principal
    const sortedTitles = allTitles.sort((a, b) => b.title.length - a.title.length);

    for (const t of sortedTitles || []) {
        const normTitle = normalize(t.title);
        if (!normTitle) continue;

        if (titlesMap.has(normTitle)) {
            const principalId = titlesMap.get(normTitle);
            console.log(`🔗 Fusionando: "${t.title}" (${t.id}) -> ID Principal: ${principalId}`);

            // Reasignar servidores
            const { error: updErr } = await supabase.from('servers').update({ title_id: principalId }).eq('title_id', t.id);
            if (!updErr) {
                toDelete.push(t.id);
                consolidatedCount++;
            } else {
                console.error(`❌ Error reasignando ${t.title}:`, updErr.message);
            }
        } else {
            titlesMap.set(normTitle, t.id);
        }
    }

    if (toDelete.length > 0) {
        console.log(`🧹 Borrando ${toDelete.length} registros duplicados de 'titles'...`);
        const { error: delErr } = await supabase.from('titles').delete().in('id', toDelete);
        if (delErr) console.error("❌ Error al borrar:", delErr.message);
    }

    console.log(`🏁 FINALIZADO. Se consolidaron ${consolidatedCount} series.`);
}

consolidate().catch(console.error);

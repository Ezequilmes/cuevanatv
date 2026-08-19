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
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/[^a-z0-9]/g, "")
        .trim();
}

async function consolidate() {
    console.log("🚀 INICIANDO CONSOLIDACIÓN FUZZY V2...");

    const { data: allTitles } = await supabase.from('titles').select('id, title, type').eq('type', 'series');
    const sortedTitles = allTitles.sort((a, b) => b.title.length - a.title.length);

    const titlesMap = new Map(); // normalized_title -> id_principal

    for (const t of sortedTitles || []) {
        const normTitle = normalize(t.title);
        if (!normTitle) continue;

        if (titlesMap.has(normTitle)) {
            const principalId = titlesMap.get(normTitle);
            console.log(`🔗 Fusionando: "${t.title}" (${t.id}) -> ID Principal: ${principalId}`);

            // 1. Obtener servidores del duplicado
            const { data: duplicateServers } = await supabase.from('servers').select('*').eq('title_id', t.id);

            for (const srv of duplicateServers || []) {
                // 2. Intentar mover el servidor al principal
                const { error: moveErr } = await supabase.from('servers').update({ title_id: principalId }).eq('id', srv.id);

                if (moveErr && moveErr.code === '23505') {
                    // 3. Si falla por duplicado, borramos este servidor porque el principal ya tiene ese capítulo
                    console.log(`🗑️ Capítulo duplicado S${srv.season_number}E${srv.episode_number} detectado, eliminando...`);
                    await supabase.from('servers').delete().eq('id', srv.id);
                }
            }

            // 4. Borrar el título duplicado una vez vacío
            await supabase.from('titles').delete().eq('id', t.id);
            console.log(`✅ Registro de título "${t.title}" eliminado.`);

        } else {
            titlesMap.set(normTitle, t.id);
        }
    }

    console.log(`🏁 FINALIZADO.`);
}

consolidate().catch(console.error);

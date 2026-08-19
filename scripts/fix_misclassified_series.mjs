import dotenv from "dotenv";
import path from "path";
import { createClient } from "@supabase/supabase-js";

dotenv.config({ path: path.resolve('D:/magik/Mi app/Cuevanatv/.env') });

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

async function fixIntegrity() {
    console.log("🚀 INICIANDO SANEAMIENTO DE CATEGORÍAS (Series -> Movies)...");

    // 1. Obtener todas las series actuales
    const { data: series } = await supabase.from('titles').select('id, title, playable_url, type').eq('type', 'series');

    console.log(`📊 Analizando ${series?.length || 0} posibles series...`);
    let fixedCount = 0;

    for (const s of series || []) {
        // 2. Contar capítulos en la tabla servers
        const { count, error } = await supabase
            .from('servers')
            .select('*', { count: 'exact', head: true })
            .eq('title_id', s.id);

        if (error) {
            console.error(`❌ Error consultando capítulos para ${s.title}:`, error.message);
            continue;
        }

        // HEURÍSTICA DE RECLASIFICACIÓN:
        // Caso A: 0 capítulos y tiene una URL de reproducción directa
        // Caso B: 1 solo capítulo y el nombre del título parece una película (no tiene patrón SxEx)

        if (count === 0) {
            if (s.playable_url && s.playable_url.trim() !== '') {
                console.log(`🎬 [RECLASIFICADO] "${s.title}" es una Película (0 capítulos, URL directa).`);
                await supabase.from('titles').update({ type: 'movie', category: 'Novedades' }).eq('id', s.id);
                fixedCount++;
            } else {
                console.log(`⚠️ [HUÉRFANO] "${s.title}" es una serie sin capítulos ni URL. Se mantiene para revisión.`);
            }
        } else if (count === 1) {
            // Si tiene 1 solo capítulo, verificamos si el título tiene formato de serie
            const hasSeriesPattern = /[sS](\d{1,2})[eE](\d{2})|(\d{1,2})x(\d{2})/i.test(s.title);
            if (!hasSeriesPattern) {
                console.log(`🎬 [CONSOLIDADO] "${s.title}" convertido a Película (1 solo capítulo, sin patrón de serie).`);

                // Obtenemos la URL de ese único capítulo para moverla al título principal
                const { data: serverData } = await supabase.from('servers').select('playable_url').eq('title_id', s.id).single();

                await supabase.from('titles').update({
                    type: 'movie',
                    category: 'Novedades',
                    playable_url: serverData?.playable_url || s.playable_url
                }).eq('id', s.id);

                // Borramos el registro de server para que no aparezca como capítulo
                await supabase.from('servers').delete().eq('title_id', s.id);
                fixedCount++;
            }
        }
    }

    console.log(`🏁 SANEAMIENTO FINALIZADO. ${fixedCount} títulos corregidos.`);
}

fixIntegrity().catch(console.error);

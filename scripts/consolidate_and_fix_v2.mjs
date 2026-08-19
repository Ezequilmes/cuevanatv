import "dotenv/config";
import { createClient } from "@supabase/supabase-js";

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

/**
 * Función para limpiar nombres de series y extraer temporada/capítulo
 */
function parseSeriesName(rawName) {
    // Regex mejorado para capturar Nombre, Temporada y Episodio
    const seriesRegex = /^(.+?)\s*(?:[sS]?(\d{1,2}))(?:[eE]?(\d{2}))/i;
    const match = rawName.match(seriesRegex);

    if (match) {
        return {
            baseName: match[1].trim(),
            season: parseInt(match[2]),
            episode: parseInt(match[3]),
            isSeries: true
        };
    }
    return { baseName: rawName.trim(), isSeries: false };
}

async function consolidate() {
    console.log("🚀 Iniciando Consolidación Maestra V2...");

    // 1. CARGAR TODO
    const { data: allTitles } = await supabase.from('titles').select('*');
    const { data: allServers } = await supabase.from('servers').select('*');

    console.log(`📊 Analizando ${allTitles.length} títulos y ${allServers.length} servidores...`);

    const titlesToKeep = new Map(); // Normalized Name -> Title Object
    const titlesToDelete = [];
    const serversToUpdate = [];

    for (const t of allTitles) {
        // PROTECCIÓN LIVE: Si es un canal en vivo, lo ignoramos de la consolidación de series
        const isLive = t.is_live || t.type === 'live' || (t.playable_url && t.playable_url.includes('.m3u8'));
        if (isLive) {
            if (t.type !== 'live') {
                console.log(`📡 Corrigiendo clasificación de canal: ${t.title}`);
                await supabase.from('titles').update({ type: 'live', is_live: true, category: 'TV Argentina' }).eq('id', t.id);
            }
            continue;
        }

        const parsed = parseSeriesName(t.title);
        const normName = parsed.baseName.toLowerCase();

        if (parsed.isSeries) {
            // Es un registro que parece un capítulo suelto en la tabla 'titles'
            console.log(`📺 Capítulo suelto detectado: "${t.title}" -> Buscando serie base: "${parsed.baseName}"`);

            // Buscar si ya tenemos un registro para la SERIE BASE
            let mainTitle = titlesToKeep.get(normName);

            // Si no existe en el mapa, buscamos en los datos originales una serie con ese nombre exacto
            if (!mainTitle) {
                mainTitle = allTitles.find(x => x.title.toLowerCase() === normName && x.type === 'series');
            }

            if (!mainTitle) {
                // Crear el título base virtualmente para este proceso
                console.log(`🆕 Creando título base para serie: ${parsed.baseName}`);
                const { data: newMain } = await supabase.from('titles').insert([{
                    title: parsed.baseName,
                    type: 'series',
                    category: 'Series',
                    published: true,
                    source_page_url: `series://${encodeURIComponent(parsed.baseName).toLowerCase()}`,
                    poster_url: t.poster_url || `https://ui-avatars.com/api/?name=${encodeURIComponent(parsed.baseName)}&background=000&color=fff`
                }]).select().single();

                mainTitle = newMain;
                titlesToKeep.set(normName, mainTitle);
            }

            // Mover este "título" a la tabla 'servers' de la serie base
            console.log(`🔗 Vinculando "${t.title}" como S${parsed.season}E${parsed.episode} a serie ID: ${mainTitle.id}`);
            await supabase.from('servers').upsert([{
                title_id: mainTitle.id,
                name: `Capítulo ${parsed.episode}`,
                playable_url: t.playable_url,
                season_number: parsed.season,
                episode_number: parsed.episode
            }], { onConflict: 'title_id,season_number,episode_number' });

            // Marcar para borrar de 'titles' porque ahora es un server
            if (t.id !== mainTitle.id) {
                titlesToDelete.push(t.id);
            }

        } else {
            // Es una película o una serie base ya registrada
            if (titlesToKeep.has(normName)) {
                const existing = titlesToKeep.get(normName);
                console.log(`🗑️ Duplicado exacto: "${t.title}". Fusionando ID: ${t.id} con ID Original: ${existing.id}`);

                // Mover sus servidores al original
                await supabase.from('servers').update({ title_id: existing.id }).eq('title_id', t.id);
                titlesToDelete.push(t.id);
            } else {
                titlesToKeep.set(normName, t);
            }
        }
    }

    // EJECUTAR BORRADOS
    if (titlesToDelete.length > 0) {
        console.log(`🧹 Borrando ${titlesToDelete.length} registros duplicados/sueltos...`);
        const { error } = await supabase.from('titles').delete().in('id', titlesToDelete);
        if (error) console.error("❌ Error borrando:", error.message);
    }

    console.log("🏁 Consolidación finalizada.");
}

consolidate().catch(console.error);

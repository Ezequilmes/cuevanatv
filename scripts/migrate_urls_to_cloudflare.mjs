import "dotenv/config";
import { createClient } from "@supabase/supabase-js";

const { SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY } = process.env;

if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    console.error("❌ Faltan credenciales de Supabase en el .env");
    process.exit(1);
}

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

async function migrateUrls() {
    console.log("🔍 Buscando URLs viejas de DuckDNS en Supabase...");

    // 1. Migrar tabla 'titles' (Películas/Series principales)
    const { data: titles, error: titleErr } = await supabase
        .from('titles')
        .select('id, playable_url')
        .like('playable_url', '%duckdns.org%');

    if (titleErr) {
        console.error("❌ Error leyendo titles:", titleErr.message);
    } else {
        console.log(`🎬 Encontrados ${titles.length} títulos para actualizar.`);
        for (const title of titles) {
            const newUrl = title.playable_url.replace('cuevana-tv-arg.duckdns.org', 'video.cuevanatv.store').replace('http://', 'https://');
            await supabase.from('titles').update({ playable_url: newUrl }).eq('id', title.id);
        }
    }

    // 2. Migrar tabla 'servers' (Capítulos de series)
    const { data: servers, error: serverErr } = await supabase
        .from('servers')
        .select('id, playable_url')
        .like('playable_url', '%duckdns.org%');

    if (serverErr) {
        console.error("❌ Error leyendo servers:", serverErr.message);
    } else {
        console.log(`📺 Encontrados ${servers.length} capítulos para actualizar.`);
        for (const server of servers) {
            const newUrl = server.playable_url.replace('cuevana-tv-arg.duckdns.org', 'video.cuevanatv.store').replace('http://', 'https://');
            await supabase.from('servers').update({ playable_url: newUrl }).eq('id', server.id);
        }
    }

    console.log("✅ Migración de URLs completada con éxito.");
}

migrateUrls();

import "dotenv/config";
import { createClient } from "@supabase/supabase-js";

const { SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY } = process.env;

if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    console.error("❌ Faltan credenciales de Supabase en el .env");
    process.exit(1);
}

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

async function fixAll() {
    console.log("🚀 INICIANDO REPARACIÓN MASIVA DE URLS (DuckDNS -> Cloudflare)...");

    // 1. Corregir tabla 'titles' (Películas y cabeceras de series)
    const { data: titles, error: tErr } = await supabase
        .from('titles')
        .select('id, playable_url')
        .or('playable_url.ilike.%duckdns.org%,playable_url.ilike.%localhost%,playable_url.ilike.%127.0.0.1%');

    if (tErr) console.error("❌ Error en titles:", tErr.message);
    else {
        console.log(`🎬 Encontrados ${titles.length} títulos con URL vieja.`);
        for (const t of titles) {
            const newUrl = t.playable_url.replace(/cuevana-tv-arg\.duckdns\.org|localhost|127\.0\.0\.1/gi, 'video.cuevanatv.store').replace('http://', 'https://');
            await supabase.from('titles').update({ playable_url: newUrl }).eq('id', t.id);
        }
    }

    // 2. Corregir tabla 'servers' (Capítulos de series)
    const { data: servers, error: sErr } = await supabase
        .from('servers')
        .select('id, playable_url')
        .or('playable_url.ilike.%duckdns.org%,playable_url.ilike.%localhost%,playable_url.ilike.%127.0.0.1%');

    if (sErr) console.error("❌ Error en servers:", sErr.message);
    else {
        console.log(`📺 Encontrados ${servers.length} capítulos con URL vieja.`);
        for (const s of servers) {
            const newUrl = s.playable_url.replace(/cuevana-tv-arg\.duckdns\.org|localhost|127\.0\.0\.1/gi, 'video.cuevanatv.store').replace('http://', 'https://');
            await supabase.from('servers').update({ playable_url: newUrl }).eq('id', s.id);
        }
    }

    console.log("✅ TODAS LAS URLS HAN SIDO ACTUALIZADAS A video.cuevanatv.store");
}

fixAll();

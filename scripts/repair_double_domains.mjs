import "dotenv/config";
import { createClient } from "@supabase/supabase-js";

const { SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY } = process.env;
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

function cleanUrl(rawUrl, isLive = false) {
    if (!rawUrl) return "";
    if (isLive || rawUrl.toLowerCase().includes(".m3u8")) return rawUrl.replace('http://', 'https://');
    const VIDEO_DOMAIN = "video.cuevanatv.store";
    try {
        let decoded = decodeURIComponent(rawUrl);
        let processed = decoded.replace(/\\/g, '/').trim();
        // Eliminar múltiples capas de protocolos/dominios
        while (processed.includes('http')) {
            processed = processed.replace(/^https?:\/\/[^\/]+\//i, '');
            processed = processed.replace(/^https?%3A\/\/[^\/]+\//i, '');
            if (!processed.startsWith('http')) break;
        }
        processed = processed.replace(/^(D:|E:)\/(pelis|Peliculas)\//i, '');
        processed = processed.replace(/^(D:|E:)\//i, '');
        processed = processed.replace(/^\/+/, '');
        return `https://${VIDEO_DOMAIN}/${processed}`;
    } catch (e) { return rawUrl; }
}

async function repair() {
    console.log("🛠️ Iniciando reparación de dominios duplicados...");

    // 1. Tablas a procesar
    const tables = ['titles', 'servers'];
    let totalFixed = 0;

    for (const table of tables) {
        console.log(`🔍 Escaneando tabla: ${table}`);
        const { data, error } = await supabase.from(table).select('id, playable_url');
        if (error) { console.error(`Error en ${table}:`, error); continue; }

        for (const item of data) {
            if (!item.playable_url) continue;

            // Detectar si tiene duplicados o caracteres raros
            const isCorrupt = item.playable_url.includes('%3A') ||
                             (item.playable_url.match(/video\.cuevanatv\.store/g) || []).length > 1 ||
                             item.playable_url.includes('duckdns.org');

            if (isCorrupt) {
                const fixed = cleanUrl(item.playable_url);
                if (fixed !== item.playable_url) {
                    await supabase.from(table).update({ playable_url: fixed }).eq('id', item.id);
                    totalFixed++;
                }
            }
        }
    }

    console.log(`✅ Reparación finalizada. Registros corregidos: ${totalFixed}`);
}

repair();

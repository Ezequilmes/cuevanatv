import "dotenv/config";
import { createClient } from "@supabase/supabase-js";

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

const VIDEO_DOMAIN = "video.cuevanatv.store";
const MAIN_DOMAIN = "panel.cuevanatv.store";

/**
 * Función de limpieza unificada igual a la del servidor principal
 */
function cleanUrl(rawUrl, isLive = false) {
    if (!rawUrl) return "";
    let processedUrl = rawUrl;
    if (processedUrl.includes('duckdns.org') || processedUrl.includes('localhost') || processedUrl.includes('127.0.0.1') || processedUrl.includes(MAIN_DOMAIN)) {
        processedUrl = processedUrl.replace(/^https?:\/\/[^\/]+/i, `https://${VIDEO_DOMAIN}`);
    }
    if (processedUrl.toLowerCase().includes(".m3u8") || processedUrl.toLowerCase().includes(".m3u") || isLive) {
        return processedUrl;
    }
    try {
        let processed = processedUrl.replace(/\\/g, '/').trim();
        processed = processed.replace(/^(D:|E:)\/pelis\//i, '');
        processed = processed.replace(/^(D:|E:)\/Peliculas\//i, '');
        processed = processed.replace(/^(D:|E:)\//i, '');
        let urlObj;
        if (processed.startsWith('http')) {
            urlObj = new URL(processed);
            urlObj.host = VIDEO_DOMAIN;
        } else {
            processed = processed.replace(/^\/+/, '');
            urlObj = new URL(`https://${VIDEO_DOMAIN}/${processed}`);
        }
        urlObj.protocol = 'https:';
        const segments = urlObj.pathname.split('/').filter(s => s.length > 0);
        urlObj.pathname = '/' + segments.map(s => encodeURIComponent(decodeURIComponent(s))).join('/');
        return urlObj.toString();
    } catch (e) {
        return rawUrl;
    }
}

async function deepPurge() {
    console.log("🚀 Iniciando PURGA PROFUNDA (Detección de DuckDNS y Duplicados)...");

    // 1. REPARAR 'titles' (playable_url Y source_page_url)
    const { data: titles } = await supabase.from('titles').select('*');
    const titlesMap = new Map(); // source_page_url -> title_id

    for (const t of titles) {
        const fixedPlayable = cleanUrl(t.playable_url, t.is_live);
        const fixedSource = t.source_page_url.startsWith('http') || t.source_page_url.includes('duckdns')
            ? cleanUrl(t.source_page_url, t.is_live)
            : t.source_page_url;

        // Si el source normalizado ya existe en nuestro mapa, es un DUPLICADO real
        if (titlesMap.has(fixedSource)) {
            const originalId = titlesMap.get(fixedSource);
            console.log(`🗑️ Detectado duplicado: "${t.title}". Fusionando con ID original: ${originalId}`);

            // Mover servidores al ID original
            await supabase.from('servers').update({ title_id: originalId }).eq('title_id', t.id);
            // Borrar el duplicado
            await supabase.from('titles').delete().eq('id', t.id);
        } else {
            // Actualizar el registro actual con las URLs fijas
            if (fixedPlayable !== t.playable_url || fixedSource !== t.source_page_url) {
                await supabase.from('titles').update({
                    playable_url: fixedPlayable,
                    source_page_url: fixedSource
                }).eq('id', t.id);
                console.log(`✅ Title normalizado: ${t.title}`);
            }
            titlesMap.set(fixedSource, t.id);
        }
    }

    // 2. REPARAR 'servers'
    const { data: servers } = await supabase.from('servers').select('*');
    for (const s of servers) {
        const fixedUrl = cleanUrl(s.playable_url);
        if (fixedUrl !== s.playable_url) {
            await supabase.from('servers').update({ playable_url: fixedUrl }).eq('id', s.id);
            console.log(`✅ Server normalizado: ${s.name}`);
        }
    }

    console.log("🏁 PURGA FINALIZADA. El sistema está limpio de rastro DuckDNS.");
}

deepPurge().catch(console.error);

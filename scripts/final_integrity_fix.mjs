import dotenv from "dotenv";
import path from "path";
import { createClient } from "@supabase/supabase-js";

dotenv.config({ path: path.resolve('D:/magik/Mi app/Cuevanatv/.env') });

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

const MAIN_DOMAIN = "panel.cuevanatv.store";
const VIDEO_DOMAIN = "video.cuevanatv.store";

function cleanUrl(rawUrl, isLive = false) {
    if (!rawUrl) return "";
    const STATIC_DOMAIN = "video.cuevanatv.store";
    let processedUrl = rawUrl.replace(/\\/g, '/').trim();
    if (processedUrl.includes(STATIC_DOMAIN)) {
        try {
            const urlObj = new URL(processedUrl);
            processedUrl = urlObj.pathname;
        } catch(e) {
            processedUrl = processedUrl.replace(/^https?:\/\/[^\/]+/i, '');
        }
    }
    if (processedUrl.includes('duckdns.org') || processedUrl.includes('localhost') || processedUrl.includes('127.0.0.1') || processedUrl.includes(MAIN_DOMAIN)) {
        processedUrl = processedUrl.replace(/^https?:\/\/[^\/]+/i, '');
    }
    if (processedUrl.toLowerCase().includes(".m3u8") || processedUrl.toLowerCase().includes(".m3u") || isLive) {
        return rawUrl;
    }
    let finalPath = "";
    const cleanPath = processedUrl.replace(/^\/+/, '');
    if (cleanPath.match(/^(E:\/Peliculas|Peliculas)/i)) {
        finalPath = "Peliculas/" + cleanPath.replace(/^(E:\/Peliculas\/|Peliculas\/)/i, '');
    }
    else if (cleanPath.match(/^(F:\/juegos|juegos)/i)) {
        finalPath = "juegos/" + cleanPath.replace(/^(F:\/juegos\/|juegos\/)/i, '');
    }
    else {
        finalPath = "Principal/" + cleanPath.replace(/^(D:\/pelis\/|Principal\/|D:\/)/i, '');
    }
    try {
        const urlObj = new URL(`https://${STATIC_DOMAIN}/${finalPath}`);
        const segments = urlObj.pathname.split('/').filter(s => s.length > 0);
        urlObj.pathname = '/' + segments.map(s => encodeURIComponent(decodeURIComponent(s))).join('/');
        return urlObj.toString();
    } catch (e) {
        return rawUrl;
    }
}

async function fix() {
    console.log("🚀 INICIANDO REPARACIÓN DE INTEGRIDAD Y DEDUP...");

    // 1. SERVERS
    const { data: servers } = await supabase.from('servers').select('*');
    const serverMap = new Map(); // finalUrl -> originalRecord
    const toDelete = [];

    for (const s of servers || []) {
        const fixed = cleanUrl(s.playable_url);
        if (fixed !== s.playable_url) {
            await supabase.from('servers').update({ playable_url: fixed }).eq('id', s.id);
            s.playable_url = fixed;
        }

        const key = `${s.title_id}|${s.season_number}|${s.episode_number}|${fixed}`;
        if (serverMap.has(key)) {
            toDelete.push(s.id);
        } else {
            serverMap.set(key, s);
        }
    }

    if (toDelete.length > 0) {
        console.log(`🧹 Borrando ${toDelete.length} servidores duplicados...`);
        await supabase.from('servers').delete().in('id', toDelete);
    }

    // 2. TITLES
    const { data: titles } = await supabase.from('titles').select('*');
    for (const t of titles || []) {
        if (!t.playable_url) continue;
        const fixed = cleanUrl(t.playable_url, t.is_live);
        if (fixed !== t.playable_url) {
            await supabase.from('titles').update({ playable_url: fixed }).eq('id', t.id);
            console.log(`✅ Title [${t.title}] corregido.`);
        }
    }

    console.log("🏁 REPARACIÓN FINALIZADA.");
}

fix().catch(console.error);

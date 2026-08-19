import dotenv from "dotenv";
import path from "path";
import fs from "fs";
import { createClient } from "@supabase/supabase-js";

dotenv.config({ path: path.resolve('D:/magik/Mi app/Cuevanatv/.env') });

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

const BASE_PATHS = ["D:\\pelis", "E:\\Peliculas", "F:\\juegos"];
const VIDEO_DOMAIN = "video.cuevanatv.store";

function cleanUrl(rawUrl, isLive = false) {
    if (!rawUrl) return "";
    const STATIC_DOMAIN = "video.cuevanatv.store";
    let processedUrl = rawUrl.replace(/\\/g, '/').trim();
    if (processedUrl.includes(STATIC_DOMAIN)) {
        const segments = processedUrl.split(STATIC_DOMAIN);
        processedUrl = segments[segments.length - 1];
    }
    if (processedUrl.includes('duckdns.org') || processedUrl.includes('localhost') || processedUrl.includes('127.0.0.1')) {
        processedUrl = processedUrl.replace(/^https?:\/\/[^\/]+/i, '');
    }
    if (processedUrl.toLowerCase().includes(".m3u8") || processedUrl.toLowerCase().includes(".m3u") || isLive) {
        return rawUrl;
    }
    const cleanPath = processedUrl.replace(/^https?:\/\/[^\/]+/i, '').replace(/^\/+/, '');
    let finalPath = "";
    if (cleanPath.match(/^(E:\/Peliculas|Peliculas)/i)) {
        finalPath = "Peliculas/" + cleanPath.replace(/^(E:\/Peliculas\/|Peliculas\/)/i, '');
    } else if (cleanPath.match(/^(F:\/juegos|juegos)/i)) {
        finalPath = "juegos/" + cleanPath.replace(/^(F:\/juegos\/|juegos\/)/i, '');
    } else {
        finalPath = "Principal/" + cleanPath.replace(/^(D:\/pelis\/|Principal\/|D:\/)/i, '');
    }
    try {
        const urlObj = new URL(`https://${STATIC_DOMAIN}/${finalPath}`);
        const segments = urlObj.pathname.split('/').filter(s => s.length > 0);
        urlObj.pathname = '/' + segments.map(s => encodeURIComponent(decodeURIComponent(s))).join('/');
        return urlObj.toString();
    } catch (e) { return rawUrl; }
}

async function fix() {
    console.log("🚀 INICIANDO REPARACIÓN DE INTEGRIDAD V2...");

    const { data: servers } = await supabase.from('servers').select('*');
    console.log(`📊 Analizando ${servers?.length || 0} capítulos...`);

    for (const s of servers || []) {
        if (!s.playable_url || s.playable_url.includes('.m3u8')) continue;

        let finalUrl = cleanUrl(s.playable_url);

        // Verificación física para asegurar el prefijo correcto
        try {
            const urlObj = new URL(finalUrl);
            const pathWithoutPrefix = decodeURIComponent(urlObj.pathname.replace(/^\/(Principal|Peliculas|juegos)\//, ''));

            let confirmedPath = null;
            if (fs.existsSync(path.join("D:\\pelis", pathWithoutPrefix))) confirmedPath = cleanUrl("D:\\pelis\\" + pathWithoutPrefix);
            else if (fs.existsSync(path.join("E:\\Peliculas", pathWithoutPrefix))) confirmedPath = cleanUrl("E:\\Peliculas\\" + pathWithoutPrefix);
            else if (fs.existsSync(path.join("F:\\juegos", pathWithoutPrefix))) confirmedPath = cleanUrl("F:\\juegos\\" + pathWithoutPrefix);

            if (confirmedPath) finalUrl = confirmedPath;
        } catch(e) {}

        if (finalUrl !== s.playable_url) {
            await supabase.from('servers').update({ playable_url: finalUrl }).eq('id', s.id);
            console.log(`✅ Fixed: ${s.name} -> ${finalUrl}`);
        }
    }

    console.log("🏁 REPARACIÓN V2 FINALIZADA.");
}

fix().catch(console.error);

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
    let processedUrl = rawUrl.replace(/\\/g, '/').trim();
    if (processedUrl.toLowerCase().includes(".m3u8") || isLive) return rawUrl;

    const STATIC_DOMAIN = "video.cuevanatv.store";
    let finalPath = "";

    // Mapeo Inteligente para Caddy
    if (processedUrl.match(/^(E:\/Peliculas|Peliculas)/i)) {
        finalPath = "Peliculas/" + processedUrl.replace(/^(E:\/Peliculas\/|Peliculas\/)/i, '');
    }
    else if (processedUrl.match(/^(F:\/juegos|juegos)/i)) {
        finalPath = "juegos/" + processedUrl.replace(/^(F:\/juegos\/|juegos\/)/i, '');
    }
    else {
        finalPath = "Principal/" + processedUrl.replace(/^(D:\/pelis\/|Principal\/|D:\/)/i, '');
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

function findLocalFile(relPath) {
    // relPath puede venir codificado o no, lo decodificamos
    const decoded = decodeURIComponent(relPath).replace(/^\//, '');

    // Probamos en cada base path
    for (const base of BASE_PATHS) {
        // Probamos el path directo
        const full = path.join(base, decoded);
        if (fs.existsSync(full)) return full;

        // Si no, probamos buscando solo el nombre del archivo
        const fileName = path.basename(decoded);
        // Búsqueda rápida recursiva (podría ser lenta en discos grandes, pero es necesaria)
        // Limitamos para no morir en el intento
    }
    return null;
}

async function repair() {
    console.log("🚀 INICIANDO REPARACIÓN MAESTRA DE ENLACES...");

    // 1. SERVERS (Capítulos)
    const { data: servers } = await supabase.from('servers').select('id, playable_url, name');
    console.log(`📊 Analizando ${servers?.length || 0} capítulos...`);

    for (const s of servers || []) {
        if (!s.playable_url || s.playable_url.includes('.m3u8')) continue;

        try {
            const urlObj = new URL(s.playable_url);
            const pathPart = decodeURIComponent(urlObj.pathname);

            // Intentar encontrar el archivo en el sistema
            let localPath = null;
            // Primero probamos asumiendo que el path actual es correcto pero le falta el prefijo
            for (const base of BASE_PATHS) {
                const trial = path.join(base, pathPart);
                if (fs.existsSync(trial)) {
                    localPath = trial;
                    break;
                }
            }

            if (localPath) {
                const newUrl = cleanUrl(localPath);
                if (newUrl !== s.playable_url) {
                    await supabase.from('servers').update({ playable_url: newUrl }).eq('id', s.id);
                    console.log(`✅ Fixed Server [${s.id}]: ${s.name} -> ${newUrl}`);
                }
            } else {
                // Si no se encuentra, tal vez ya está en el formato correcto pero con el host mal
                const fixed = cleanUrl(s.playable_url);
                if (fixed !== s.playable_url) {
                    await supabase.from('servers').update({ playable_url: fixed }).eq('id', s.id);
                    console.log(`✅ Fixed Host Server [${s.id}]: ${s.name}`);
                }
            }
        } catch (e) {
            console.error(`❌ Error procesando server ${s.id}:`, e.message);
        }
    }

    // 2. TITLES (Películas)
    const { data: titles } = await supabase.from('titles').select('id, playable_url, title').eq('type', 'movie');
    console.log(`📊 Analizando ${titles?.length || 0} películas...`);

    for (const t of titles || []) {
        if (!t.playable_url || t.playable_url.includes('.m3u8')) continue;

        try {
            const urlObj = new URL(t.playable_url);
            const pathPart = decodeURIComponent(urlObj.pathname);

            let localPath = null;
            for (const base of BASE_PATHS) {
                const trial = path.join(base, pathPart);
                if (fs.existsSync(trial)) {
                    localPath = trial;
                    break;
                }
            }

            if (localPath) {
                const newUrl = cleanUrl(localPath);
                if (newUrl !== t.playable_url) {
                    await supabase.from('titles').update({ playable_url: newUrl }).eq('id', t.id);
                    console.log(`✅ Fixed Movie [${t.id}]: ${t.title} -> ${newUrl}`);
                }
            } else {
                const fixed = cleanUrl(t.playable_url);
                if (fixed !== t.playable_url) {
                    await supabase.from('titles').update({ playable_url: fixed }).eq('id', t.id);
                    console.log(`✅ Fixed Host Movie [${t.id}]: ${t.title}`);
                }
            }
        } catch (e) {
             // console.error(`❌ Error procesando movie ${t.id}:`, e.message);
        }
    }

    console.log("🏁 REPARACIÓN MAESTRA FINALIZADA.");
}

repair().catch(console.error);

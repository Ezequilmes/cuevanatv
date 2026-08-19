import "dotenv/config";
import { createClient } from "@supabase/supabase-js";
import path from "path";
import { fileURLToPath } from "url";
import fs from "fs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// CONFIGURACIÓN DE SUPABASE
const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

const VIDEO_DOMAIN = "video.cuevanatv.store";
const MAIN_DOMAIN = "panel.cuevanatv.store";

function cleanUrl(rawUrl, isLive = false) {
    if (!rawUrl) return "";
    let processedUrl = rawUrl;
    if (processedUrl.includes('duckdns.org') || processedUrl.includes('localhost') || processedUrl.includes('127.0.0.1') || processedUrl.includes(MAIN_DOMAIN)) {
        processedUrl = processedUrl.replace(/^https?:\/\/[^\/]+/i, `https://${VIDEO_DOMAIN}`);
    }
    if (processedUrl.toLowerCase().includes(".m3u8") || processedUrl.toLowerCase().includes(".m3u") || isLive) {
        return processedUrl;
    }
    const STATIC_DOMAIN = "video.cuevanatv.store";
    try {
        let processed = processedUrl.replace(/\\/g, '/').trim();
        processed = processed.replace(/^(D:|E:)\/pelis\//i, '');
        processed = processed.replace(/^(D:|E:)\/Peliculas\//i, '');
        processed = processed.replace(/^(D:|E:)\//i, '');
        let urlObj;
        if (processed.startsWith('http')) {
            urlObj = new URL(processed);
            urlObj.host = STATIC_DOMAIN;
        } else {
            processed = processed.replace(/^\/+/, '');
            urlObj = new URL(`https://${STATIC_DOMAIN}/${processed}`);
        }
        urlObj.protocol = 'https:';
        let cleanPath = urlObj.pathname.replace(/^\/Principal\//i, '/');
        const segments = cleanPath.split('/').filter(s => s.length > 0);
        urlObj.pathname = '/' + segments.map(s => encodeURIComponent(decodeURIComponent(s))).join('/');
        return urlObj.toString();
    } catch (e) {
        return rawUrl;
    }
}

async function runRepair() {
    console.log("🚀 Iniciando REPARACIÓN MAESTRA de la Base de Datos...");

    // 1. REPARACIÓN DE URLS EN TITLES
    console.log("🔗 Reparando URLs en 'titles'...");
    const { data: titles } = await supabase.from('titles').select('id, playable_url, title, category');
    for (const title of titles) {
        const fixedUrl = cleanUrl(title.playable_url);
        if (fixedUrl !== title.playable_url) {
            await supabase.from('titles').update({ playable_url: fixedUrl }).eq('id', title.id);
            console.log(`✅ Fixed Title: ${title.title}`);
        }

        // Fix Drama posters
        if (title.category === "Drama" && !title.poster_url) {
             const newPoster = `https://ui-avatars.com/api/?name=${encodeURIComponent(title.title)}&background=000&color=fff&size=512`;
             await supabase.from('titles').update({ poster_url: newPoster }).eq('id', title.id);
        }
    }

    // 2. REPARACIÓN DE URLS EN SERVERS
    console.log("🔗 Reparando URLs en 'servers'...");
    const { data: servers } = await supabase.from('servers').select('id, playable_url, name');
    for (const server of servers) {
        const fixedUrl = cleanUrl(server.playable_url);
        if (fixedUrl !== server.playable_url) {
            await supabase.from('servers').update({ playable_url: fixedUrl }).eq('id', server.id);
            console.log(`✅ Fixed Server: ${server.name}`);
        }
    }

    // 3. CONSOLIDACIÓN DE SERIES (REGEX)
    console.log("📺 Consolidando Series duplicadas...");
    const seriesRegex = /^(.+?)\s*(?:[sS]?(\d{1,2}))(?:[eE]?(\d{2}))/i;
    const { data: allTitles } = await supabase.from('titles').select('*');

    for (const title of allTitles) {
        const match = title.title.match(seriesRegex);
        if (match) {
            const baseName = match[1].trim();
            const season = parseInt(match[2]);
            const episode = parseInt(match[3]);

            console.log(`🔄 Consolidando: ${title.title} -> ${baseName}`);

            // Buscar/Crear Título Principal
            let { data: mainTitle } = await supabase.from('titles').select('id').eq('title', baseName).eq('type', 'series').maybeSingle();
            if (!mainTitle) {
                const { data: newMain } = await supabase.from('titles').insert([{
                    title: baseName, type: 'series', category: 'Series', published: true,
                    poster_url: `https://ui-avatars.com/api/?name=${encodeURIComponent(baseName)}&background=000&color=fff`
                }]).select().single();
                mainTitle = newMain;
            }

            // Mover a servers
            await supabase.from('servers').upsert([{
                title_id: mainTitle.id, name: `Capítulo ${episode}`,
                playable_url: cleanUrl(title.playable_url),
                season_number: season, episode_number: episode
            }], { onConflict: 'title_id,season_number,episode_number' });

            // Borrar original si no es el principal
            if (title.id !== mainTitle.id) {
                await supabase.from('titles').delete().eq('id', title.id);
            }
        }
    }

    console.log("🏁 Reparación finalizada con éxito.");
}

runRepair().catch(console.error);

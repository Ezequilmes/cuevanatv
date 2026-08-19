import "dotenv/config";
import { createClient } from "@supabase/supabase-js";
import fs from 'fs';
import path from 'path';

const { SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY } = process.env;
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

const BASE_PATHS = ["D:\\pelis", "E:\\Peliculas"];
const VIDEO_DOMAIN = "video.cuevanatv.store";

async function rescue() {
    const { data: main } = await supabase.from('titles').select('id').eq('title', 'Vikingos: Valhalla').maybeSingle();
    if (!main) return console.log("Main title not found");

    console.log("🚀 Rescatando episodios de Vikingos...");

    const episodes = [];
    const scan = (dir) => {
        const items = fs.readdirSync(dir);
        for (const item of items) {
            const full = path.join(dir, item);
            if (fs.statSync(full).isDirectory()) scan(full);
            else if (item.toLowerCase().includes('viking') && /\.[sS]\d+[eE]\d+/i.test(item)) {
                const match = item.match(/[sS](\d+)[eE](\d+)/i);
                const season = parseInt(match[1]);
                const episode = parseInt(match[2]);

                // Construir URL manual segura
                const rel = path.relative(path.dirname(path.dirname(full)), full).replace(/\\/g, '/');
                const encoded = rel.split('/').map(encodeURIComponent).join('/');
                const url = `https://${VIDEO_DOMAIN}/${encoded}`;

                episodes.push({
                    title_id: main.id,
                    name: `Capítulo ${episode}`,
                    playable_url: url,
                    season_number: season,
                    episode_number: episode
                });
            }
        }
    }
    BASE_PATHS.forEach(b => { if(fs.existsSync(b)) scan(b); });

    console.log(`🔍 Encontrados ${episodes.length} episodios físicos.`);

    if (episodes.length > 0) {
        await supabase.from('servers').upsert(episodes, { onConflict: 'title_id,season_number,episode_number' });
        console.log("✅ Servidores actualizados.");
    }
}

rescue();

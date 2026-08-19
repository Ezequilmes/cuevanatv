import dotenv from "dotenv";
import path from "path";
import { createClient } from "@supabase/supabase-js";

dotenv.config({ path: path.resolve('D:/magik/Mi app/Cuevanatv/.env') });

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

const OLD_DOMAIN = "cuevana-tv-arg.duckdns.org";
const NEW_DOMAIN = "video.cuevanatv.store";

async function purge() {
    console.log(`🚀 INICIANDO PURGA DEFINITIVA: [${OLD_DOMAIN}] -> [${NEW_DOMAIN}]`);

    // 1. Limpiar tabla TITLES
    console.log("📂 Procesando tabla 'titles'...");
    const { data: titles } = await supabase.from('titles').select('id, playable_url, source_page_url').ilike('playable_url', `%${OLD_DOMAIN}%`);

    for (const t of titles || []) {
        const newPlayable = t.playable_url.replace(OLD_DOMAIN, NEW_DOMAIN).replace('http://', 'https://');
        const newSource = (t.source_page_url || "").replace(OLD_DOMAIN, NEW_DOMAIN).replace('http://', 'https://');

        await supabase.from('titles').update({
            playable_url: newPlayable,
            source_page_url: newSource
        }).eq('id', t.id);
        console.log(`✅ Title [${t.id}] corregido.`);
    }

    // 2. Limpiar tabla SERVERS
    console.log("📂 Procesando tabla 'servers'...");
    const { data: servers } = await supabase.from('servers').select('id, playable_url').ilike('playable_url', `%${OLD_DOMAIN}%`);

    for (const s of servers || []) {
        const newUrl = s.playable_url.replace(OLD_DOMAIN, NEW_DOMAIN).replace('http://', 'https://');
        await supabase.from('servers').update({ playable_url: newUrl }).eq('id', s.id);
        console.log(`✅ Server [${s.id}] corregido.`);
    }

    console.log("🏁 PURGA FINALIZADA CON ÉXITO. DuckDNS ha sido erradicado de la base de datos.");
}

purge().catch(console.error);

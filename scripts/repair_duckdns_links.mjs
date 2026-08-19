import dotenv from "dotenv";
import path from "path";
import { createClient } from "@supabase/supabase-js";

dotenv.config({ path: path.resolve('D:/magik/Mi app/Cuevanatv/.env') });

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

const OLD_DOMAIN = "cuevana-tv-arg.duckdns.org";
const NEW_DOMAIN = "video.cuevanatv.store";

async function repair() {
    console.log(`🔍 Buscando enlaces con [${OLD_DOMAIN}]...`);

    // 1. Tabla TITLES
    const { data: titles } = await supabase.from('titles')
        .select('id, title, playable_url, source_page_url')
        .or(`playable_url.ilike.%${OLD_DOMAIN}%,source_page_url.ilike.%${OLD_DOMAIN}%`);

    console.log(`📂 Encontrados ${titles?.length || 0} títulos con problemas.`);
    for (const t of titles || []) {
        const updates = {};
        if (t.playable_url?.includes(OLD_DOMAIN)) {
            updates.playable_url = t.playable_url.replace(OLD_DOMAIN, NEW_DOMAIN).replace('http://', 'https://');
        }
        if (t.source_page_url?.includes(OLD_DOMAIN)) {
            updates.source_page_url = t.source_page_url.replace(OLD_DOMAIN, NEW_DOMAIN).replace('http://', 'https://');
        }

        const { error } = await supabase.from('titles').update(updates).eq('id', t.id);
        if (error) console.error(`❌ Error en Title [${t.title}]:`, error.message);
        else console.log(`✅ Title [${t.title}] reparado.`);
    }

    // 2. Tabla SERVERS
    const { data: servers } = await supabase.from('servers')
        .select('id, name, playable_url')
        .ilike('playable_url', `%${OLD_DOMAIN}%`);

    console.log(`📂 Encontrados ${servers?.length || 0} capítulos (servers) con problemas.`);
    for (const s of servers || []) {
        const newUrl = s.playable_url.replace(OLD_DOMAIN, NEW_DOMAIN).replace('http://', 'https://');
        const { error } = await supabase.from('servers').update({ playable_url: newUrl }).eq('id', s.id);
        if (error) console.error(`❌ Error en Server [${s.id}]:`, error.message);
        else console.log(`✅ Server [${s.id}] (${s.name}) reparado.`);
    }

    console.log("🏁 REPARACIÓN FINALIZADA.");
}

repair().catch(console.error);

import dotenv from "dotenv";
import path from "path";
import { createClient } from "@supabase/supabase-js";

dotenv.config({ path: path.resolve('D:/magik/Mi app/Cuevanatv/.env') });

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const supabase = createClient(supabaseUrl, supabaseKey);

async function fix() {
    const correctUrl = "https://video.cuevanatv.store/Peliculas/Lucky.S01E04.2026.WEB-DL.1080p-Dual-Lat/Lucky.S01E04.2026.WEB-DL.1080p-Dual-Lat.mkv";

    // Fix the one with wrong prefix
    await supabase.from('servers').update({ playable_url: correctUrl }).eq('id', 'ce5cf8c4-9459-416c-96ea-6ff9d142804b');

    // Delete the one with double URL
    await supabase.from('servers').delete().eq('id', 'a2abcc0e-de41-4063-bc77-3d66f4b918ea');

    console.log("✅ Lucky S01E04 fixed manually.");
}

fix().catch(console.error);

import "dotenv/config";
import { createClient } from "@supabase/supabase-js";

/**
 * CONFIGURACIÓN DE INFRAESTRUCTURA
 */
const { SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY } = process.env;
const NEW_BASE_URL = "https://video.cuevanatv.store";
const OLD_BASE_DOMAINS = [
    "cuevana-tv-arg.duckdns.org",
    "localhost:80",
    "127.0.0.1:80",
    "localhost",
    "127.0.0.1"
];

if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    console.error("❌ Error: Credenciales de Supabase no encontradas en .env");
    process.exit(1);
}

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

/**
 * PLAN DE MIGRACIÓN SQL (Para ejecutar en el Editor SQL de Supabase)
 *
 * -- 1. Actualizar tabla TITLES
 * UPDATE titles
 * SET playable_url = REPLACE(playable_url, 'cuevana-tv-arg.duckdns.org', 'video.cuevanatv.store')
 * WHERE playable_url LIKE '%duckdns.org%';
 *
 * -- 2. Actualizar tabla SERVERS
 * UPDATE servers
 * SET playable_url = REPLACE(playable_url, 'cuevana-tv-arg.duckdns.org', 'video.cuevanatv.store')
 * WHERE playable_url LIKE '%duckdns.org%';
 */

async function runIntegrityCheckAndHealing() {
    console.log("🛠️ INICIANDO CORAZÓN DE INTEGRIDAD Y AUTO-REPARACIÓN...");
    console.log(`🌐 Túnel Activo Detectado: ${NEW_BASE_URL}`);

    const summary = { fixedTitles: 0, fixedServers: 0, errors: 0 };

    try {
        // --- PROCESAR TABLA TITLES ---
        console.log("🔍 Auditando tabla 'titles'...");
        const { data: titles, error: tErr } = await supabase
            .from('titles')
            .select('id, playable_url')
            .not('playable_url', 'is', null)
            .neq('type', 'live'); // No tocamos canales en vivo externos

        if (tErr) throw tErr;

        for (const item of titles) {
            const needsHealing = OLD_BASE_DOMAINS.some(domain => item.playable_url.includes(domain));
            if (needsHealing) {
                let fixedUrl = item.playable_url;
                OLD_BASE_DOMAINS.forEach(domain => {
                    fixedUrl = fixedUrl.replace(domain, 'video.cuevanatv.store');
                });
                // Forzamos HTTPS
                fixedUrl = fixedUrl.replace('http://', 'https://');

                const { error: updErr } = await supabase
                    .from('titles')
                    .update({ playable_url: fixedUrl })
                    .eq('id', item.id);

                if (!updErr) summary.fixedTitles++;
                else summary.errors++;
            }
        }

        // --- PROCESAR TABLA SERVERS ---
        console.log("🔍 Auditando tabla 'servers'...");
        const { data: servers, error: sErr } = await supabase
            .from('servers')
            .select('id, playable_url')
            .not('playable_url', 'is', null);

        if (sErr) throw sErr;

        for (const item of servers) {
            const needsHealing = OLD_BASE_DOMAINS.some(domain => item.playable_url.includes(domain));
            if (needsHealing) {
                let fixedUrl = item.playable_url;
                OLD_BASE_DOMAINS.forEach(domain => {
                    fixedUrl = fixedUrl.replace(domain, 'video.cuevanatv.store');
                });
                fixedUrl = fixedUrl.replace('http://', 'https://');

                const { error: updErr } = await supabase
                    .from('servers')
                    .update({ playable_url: fixedUrl })
                    .eq('id', item.id);

                if (!updErr) summary.fixedServers++;
                else summary.errors++;
            }
        }

        console.log("--------------------------------------------------");
        console.log("✅ MANTENIMIENTO DE INFRAESTRUCTURA FINALIZADO");
        console.log(`🎬 Películas reparadas: ${summary.fixedTitles}`);
        console.log(`📺 Capítulos reparados: ${summary.fixedServers}`);
        console.log(`❌ Errores encontrados: ${summary.errors}`);
        console.log("--------------------------------------------------");

    } catch (err) {
        console.error("🔥 FALLO CRÍTICO EN EL SISTEMA DE INTEGRIDAD:", err.message);
    }
}

runIntegrityCheckAndHealing();

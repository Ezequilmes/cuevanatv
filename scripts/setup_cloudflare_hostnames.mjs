import "dotenv/config";
import fetch from "node-fetch";

const { CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, CLOUDFLARE_TUNNEL_ID } = process.env;

if (!CLOUDFLARE_API_TOKEN || !CLOUDFLARE_ACCOUNT_ID || !CLOUDFLARE_TUNNEL_ID) {
    console.error("❌ Faltan credenciales de Cloudflare en el .env");
    process.exit(1);
}

async function setupHostnames() {
    const url = `https://api.cloudflare.com/client/v4/accounts/${CLOUDFLARE_ACCOUNT_ID}/cfd_tunnel/${CLOUDFLARE_TUNNEL_ID}/configurations`;

    // Obtenemos la configuración actual para no borrar otros hostnames si existen
    const getRes = await fetch(url, {
        headers: { "Authorization": `Bearer ${CLOUDFLARE_API_TOKEN}` }
    });
    const currentConfig = await getRes.json();

    if (!currentConfig.success) {
        console.error("❌ Error obteniendo configuración del túnel:", currentConfig.errors);
        return;
    }

    const config = currentConfig.result.config || { ingress: [] };

    // Filtramos los existentes para añadir o actualizar los nuestros
    // Importante: El último elemento de ingress DEBE ser el catch-all (404)
    const baseIngress = config.ingress.filter(i => i.hostname !== 'panel.cuevanatv.store' && i.hostname !== 'video.cuevanatv.store' && i.service !== 'http_status:404');

    const newIngress = [
        ...baseIngress,
        {
            hostname: "panel.cuevanatv.store",
            service: "http://localhost:8787"
        },
        {
            hostname: "video.cuevanatv.store",
            service: "http://localhost:80"
        },
        {
            service: "http_status:404"
        }
    ];

    console.log("🚀 Enviando nueva configuración de hostnames a Cloudflare...");

    const putRes = await fetch(url, {
        method: "PUT",
        headers: {
            "Authorization": `Bearer ${CLOUDFLARE_API_TOKEN}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ config: { ingress: newIngress } })
    });

    const result = await putRes.json();

    if (result.success) {
        console.log("✅ Hostnames configurados con éxito:");
        console.log("🔗 https://panel.cuevanatv.store -> http://localhost:8787");
        console.log("🔗 https://video.cuevanatv.store -> http://localhost:80");
    } else {
        console.error("❌ Error configurando hostnames:", result.errors);
    }
}

setupHostnames();

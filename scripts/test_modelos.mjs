// test_modelos.mjs
const apiKey = "AIzaSyDDFAdXT1-4tMpREw-0h3h98ZJfkAEWXYM";

async function verModelos() {
    console.log("⏳ Consultando los servidores de Google...");
    try {
        const respuesta = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey}`);
        const datos = await respuesta.json();

        console.log("\n✅ ESTOS SON LOS NOMBRES EXACTOS HABILITADOS PARA TU CLAVE:");
        if (datos.models) {
            datos.models.forEach(modelo => {
                // Filtramos para ver solo los que sirven para generar texto
                if(modelo.name.includes('gemini') && modelo.supportedGenerationMethods.includes('generateContent')) {
                    // Le sacamos la palabra "models/" para que te quede el nombre limpio
                    console.log(`👉 ${modelo.name.replace('models/', '')}`);
                }
            });
        } else {
            console.log("No se encontraron modelos o la respuesta fue inesperada:", datos);
        }

    } catch (error) {
        console.error("❌ Error de conexión:", error);
    }
}

verModelos();
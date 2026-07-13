import { TelegramClient } from "telegram";
import { StringSession } from "telegram/sessions/index.js";
import input from "input";

// --- CONFIGURACIÓN ---
// Obten estos valores de https://my.telegram.org
const apiId = 0; // REEMPLAZA CON TU API_ID (ej: 1234567)
const apiHash = ""; // REEMPLAZA CON TU API_HASH (ej: "abcdef123456...")

const stringSession = new StringSession(""); // Empezamos con una sesión vacía

(async () => {
  console.log("--------------------------------------------------");
  console.log("🚀 GENERADOR DE SESIÓN GRAMJS PARA CUEVANATV");
  console.log("--------------------------------------------------\n");

  const client = new TelegramClient(stringSession, apiId, apiHash, {
    connectionRetries: 5,
  });

  try {
    await client.start({
      phoneNumber: async () => await input.text("📱 Ingresa tu número (Formato: +54911...): "),
      password: async () => await input.text("🔑 Ingresa tu contraseña (2FA - Si no tienes, dale Enter): "),
      phoneCode: async () => await input.text("🔢 Ingresa el código que te envió Telegram: "),
      onError: (err) => console.log("❌ Error:", err.message),
    });

    console.log("\n--------------------------------------------------");
    console.log("✅ ¡LOGIN EXITOSO!");
    console.log("--------------------------------------------------");
    console.log("\nESTE ES TU STRING_SESSION (CÓPIALO COMPLETO):\n");
    console.log("\x1b[32m%s\x1b[0m", client.session.save()); // Imprime en verde
    console.log("\n--------------------------------------------------");
    console.log("Copia el código de arriba y pégalo en tu archivo .env:");
    console.log("TELEGRAM_STRING_SESSION=tu_codigo_aqui");
    console.log("--------------------------------------------------\n");

    await client.disconnect();
    process.exit(0);
  } catch (error) {
    console.error("\n❌ Error crítico durante la generación:", error.message);
    process.exit(1);
  }
})();

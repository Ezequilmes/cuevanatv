import "dotenv/config";
import express from "express";
import cors from "cors";
import { chromium } from "playwright";
import { createClient } from "@supabase/supabase-js";
import cron from "node-cron";
import path from "path";
import { fileURLToPath } from "url";
import dotenv from "dotenv";
import pkg from 'whatsapp-web.js';
const { Client, LocalAuth } = pkg;
import qrcode from 'qrcode-terminal';
import TelegramBot from 'node-telegram-bot-api';
import { TelegramClient, Api } from "telegram";
import { StringSession } from "telegram/sessions/index.js";
import fs from 'fs';
import multer from 'multer';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// =========================================================
// CONFIGURACIÓN DE SEGURIDAD Y TIMEOUTS (ANTI-CUELGUE)
// =========================================================
const SCRAPER_HARD_TIMEOUT = 60000; // 60 segundos máximo por tarea

const withTimeout = (promise, ms, taskName) => {
  const timeout = new Promise((_, reject) =>
    setTimeout(() => reject(new Error(`[TIMEOUT] ${taskName} excedió los ${ms}ms`)), ms)
  );
  return Promise.race([promise, timeout]);
};

dotenv.config({ path: path.resolve(__dirname, "../.env") });

// =========================================================
// CONFIGURACIÓN DE TELEGRAM BOT
// =========================================================
const telegramToken = process.env.TELEGRAM_TOKEN;
const telegramChannelId = process.env.TELEGRAM_CHAT_ID;
let telegramBot;

if (telegramToken) {
    telegramBot = new TelegramBot(telegramToken, { polling: false });
    console.log("✅ Bot de Telegram inicializado.");
} else {
    console.warn("⚠️ TELEGRAM_TOKEN no encontrado en .env");
}

// =========================================================
// CONFIGURACIÓN DE GRAMJS (USERBOT)
// =========================================================
const apiId = parseInt(process.env.TELEGRAM_API_ID || "0");
const apiHash = process.env.TELEGRAM_API_HASH || "";
const stringSession = new StringSession(process.env.TELEGRAM_STRING_SESSION || "");

const userbot = new TelegramClient(stringSession, apiId, apiHash, {
    connectionRetries: 5,
});

(async () => {
    if (apiId && apiHash) {
        try {
            await withTimeout(userbot.connect(), 15000, "Userbot Connection");
            console.log("✅ Userbot de Telegram conectado.");
        } catch (err) {
            console.warn("⚠️ Userbot no pudo conectar (Timeout/Error):", err.message);
        }
    }
})();

const app = express();
app.use(cors({ origin: "*" }));
app.use(express.json());

// =========================================================
// CONFIGURACIÓN DE MULTER (CARGA DE IMÁGENES SEGURA)
// =========================================================
const uploadDir = path.join(__dirname, "uploads");
if (!fs.existsSync(uploadDir)) fs.mkdirSync(uploadDir, { recursive: true });

const storage = multer.diskStorage({
    destination: (req, file, cb) => cb(null, uploadDir),
    filename: (req, file, cb) => cb(null, `broadcast_${Date.now()}${path.extname(file.originalname)}`)
});
const upload = multer({ storage: storage });

// =========================================================
// CONFIGURACIÓN DE SUPABASE
// =========================================================
const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

const supabase = (supabaseUrl && supabaseKey) ? createClient(supabaseUrl, supabaseKey) : null;
if (supabase) console.log("✅ Conexión con Supabase establecida correctamente.");

// =========================================================
// GESTIÓN DE ERRORES Y LOGS CENTRALIZADA
// =========================================================
async function registrarError(error, contexto) {
    console.error(`❌ ERROR en [${contexto}]:`, error.message);
    if (!supabase) return;
    try {
        await supabase.from('system_logs').insert([{
            context: contexto,
            message: error.message,
            stack: error.stack,
            timestamp: new Date().toISOString()
        }]);
    } catch (e) {
        console.error("⚠️ Fallo crítico al guardar log en Supabase:", e.message);
    }
}

// =========================================================
// UTILIDAD DE NORMALIZACIÓN DE URL (ESPECÍFICA APK)
// =========================================================
function cleanUrl(rawUrl) {
    if (!rawUrl) return "";
    try {
        // 1. Eliminar barras invertidas de Windows y espacios extra
        let processed = rawUrl.replace(/\\/g, '/').trim();

        // 2. Eliminar prefijos de rutas físicas de Windows (D:\ o E:\)
        processed = processed.replace(/^(D:|E:)\/pelis\//i, '');
        processed = processed.replace(/^(D:|E:)\/Peliculas\//i, '');
        processed = processed.replace(/^(D:|E:)\//i, '');

        // 3. Si no es una URL absoluta, convertirla en una relativa al dominio base
        let urlObj;
        if (processed.startsWith('http')) {
            urlObj = new URL(processed);
        } else {
            // Aseguramos que el path sea limpio antes de unir
            processed = processed.replace(/^\/+/, '');
            urlObj = new URL(`http://cuevana-tv-arg.duckdns.org/${processed}`);
        }

        // 4. Forzar dominio y protocolo compatible con APK
        urlObj.protocol = 'http:';
        urlObj.hostname = 'cuevana-tv-arg.duckdns.org';
        urlObj.port = '';

        // 5. Limpieza agresiva del path (Eliminar /Principal/ y normalizar segmentos)
        let cleanPath = urlObj.pathname.replace(/^\/Principal\//i, '/');
        const segments = cleanPath.split('/').filter(s => s.length > 0);
        urlObj.pathname = '/' + segments.map(s => encodeURIComponent(decodeURIComponent(s))).join('/');

        return urlObj.toString();
    } catch (e) {
        console.error("⚠️ Error cleaning URL:", rawUrl, e.message);
        return rawUrl;
    }
}

const BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";
const ACCEPT_LANGUAGE = "es-ES,es;q=0.9,en-US;q=0.8,en;q=0.7";
const HEADLESS = process.env.HEADLESS !== "false";

process.on("unhandledRejection", (r) => { console.error("❌ unhandledRejection:", r); });
process.on("uncaughtException", (e) => { console.error("❌ uncaughtException:", e); });

// =========================================================
// ENDPOINT: DIFUSIÓN MASIVA TELEGRAM
// =========================================================
app.post("/api/telegram/broadcast", async (req, res) => {
    const { message, imageUrl } = req.body;
    if (!telegramBot || !telegramChannelId) return res.status(500).json({ error: "Configuración de Telegram incompleta en el servidor." });
    if (!message) return res.status(400).json({ error: "El mensaje es obligatorio." });

    try {
        let response;
        if (imageUrl) {
            response = await telegramBot.sendPhoto(telegramChannelId, imageUrl, { caption: message, parse_mode: 'HTML' });
        } else {
            response = await telegramBot.sendMessage(telegramChannelId, message, { parse_mode: 'HTML' });
        }
        res.json({ success: true, messageId: response.message_id });
    } catch (error) {
        res.status(500).json({ error: "Fallo al enviar el mensaje a Telegram." });
    }
});

// =========================================================
// MÓDULO: GENERADOR DE IMÁGENES POR IA (100% GRATIS)
// =========================================================

/**
 * Genera una imagen basada en un prompt y la guarda localmente
 * @param {string} prompt Texto descriptivo de la imagen
 * @returns {Promise<string>} Ruta del archivo local guardado
 */
async function generarImagenIA(prompt) {
    try {
        const promptSanitizado = encodeURIComponent(prompt);
        // Modelo 'flux' vía Pollinations (Gratis y alta calidad)
        const urlIA = `https://image.pollinations.ai/p/${promptSanitizado}?model=flux&width=1024&height=1024&enhance=true`;

        console.log(`🤖 Solicitando imagen por IA para: "${prompt}"...`);
        const response = await fetch(urlIA);
        if (!response.ok) throw new Error("Error en la respuesta del servidor de IA");

        const arrayBuffer = await response.arrayBuffer();
        const buffer = Buffer.from(arrayBuffer);

        const fileName = `ia_${Date.now()}.jpg`;
        const filePath = path.join(uploadDir, fileName);

        fs.writeFileSync(filePath, buffer);
        console.log(`✅ Imagen generada con éxito y guardada en: ${filePath}`);

        return filePath;
    } catch (error) {
        await registrarError(error, "generarImagenIA");
        throw error;
    }
}

// =========================================================
// ENDPOINT: GENERAR E INICIAR DIFUSIÓN AUTOMÁTICA
// =========================================================
app.post("/api/admin/generate-and-broadcast", async (req, res) => {
    const { promptIA, message, groupIds } = req.body;

    if (!promptIA || !message || !groupIds) {
        return res.status(400).json({ error: "Faltan datos (promptIA, message, groupIds)." });
    }

    try {
        // 1. Generar la imagen con IA
        const localImagePath = await generarImagenIA(promptIA);
        const ids = typeof groupIds === 'string' ? JSON.parse(groupIds) : groupIds;

        res.json({
            success: true,
            status: `Imagen generada. Iniciando difusión para ${ids.length} grupos.`,
            localPath: localImagePath
        });

        // 2. Ejecución en segundo plano (Lógica de Userbot)
        (async () => {
            for (const id of ids) {
                try {
                    await userbot.sendFile(id, {
                        file: localImagePath,
                        caption: message,
                        parseMode: 'html'
                    });
                    const delay = Math.floor(Math.random() * (75000 - 45000 + 1) + 45000);
                    await new Promise(r => setTimeout(r, delay));
                } catch (err) {
                    console.error(`❌ Error enviando imagen IA a ${id}:`, err.message);
                }
            }

            // 3. Limpieza final
            if (fs.existsSync(localImagePath)) {
                fs.unlinkSync(localImagePath);
                console.log("🧹 Imagen temporal de la IA eliminada.");
            }
        })();

    } catch (error) {
        res.status(500).json({ error: `Fallo en el proceso de IA: ${error.message}` });
    }
});

// =========================================================
// ENDPOINTS USERBOT: GUERRILLA MARKETING
// =========================================================
app.get("/api/telegram/search-groups", async (req, res) => {
    const { query } = req.query;
    if (!query) return res.status(400).json({ error: "Query requerida" });
    try {
        const result = await userbot.invoke(new Api.contacts.Search({ q: query.toString(), limit: 20 }));
        const groups = result.chats
            .filter(chat => chat.className === 'Channel' || chat.className === 'Chat')
            .map(chat => ({ id: chat.id.toString(), title: chat.title, username: chat.username || null, participants: chat.participantsCount || 0 }));
        res.json({ success: true, groups });
    } catch (error) { res.status(500).json({ error: error.message }); }
});

app.post("/api/telegram/join-group", async (req, res) => {
    const { username } = req.body;
    if (!username) return res.status(400).json({ error: "Username requerido" });
    try {
        await userbot.invoke(new Api.channels.JoinChannel({ channel: username }));
        res.json({ success: true, message: `Unido a ${username}` });
    } catch (error) { res.status(500).json({ error: error.message }); }
});

app.get("/api/telegram/my-groups", async (req, res) => {
    try {
        const dialogs = await userbot.getDialogs({});
        const groups = dialogs.filter(d => d.isGroup || d.isChannel).map(d => ({ id: d.id.toString(), title: d.title, username: d.entity.username || null }));
        res.json({ success: true, groups });
    } catch (error) { res.status(500).json({ error: error.message }); }
});

app.post("/api/telegram/guerrilla-broadcast", async (req, res) => {
    const { groupIds, message, imageUrl } = req.body;
    if (!groupIds || !Array.isArray(groupIds) || !message) return res.status(400).json({ error: "Datos incompletos" });
    res.json({ success: true, status: "Proceso de envío masivo iniciado." });

    for (const id of groupIds) {
        try {
            if (imageUrl) {
                await userbot.sendFile(id, { file: imageUrl, caption: message, parseMode: 'html' });
            } else {
                await userbot.sendMessage(id, { message: message, parseMode: 'html' });
            }
            const randomDelay = Math.floor(Math.random() * (75000 - 45000 + 1) + 45000);
            await new Promise(resolve => setTimeout(resolve, randomDelay));
        } catch (err) { console.error(`❌ Error guerrilla a ${id}:`, err.message); }
    }
});

// NUEVO: DIFUSIÓN DE IMAGEN A GRUPOS (USERBOT + MULTER)
app.post("/api/admin/broadcast-image", upload.single("imagen"), async (req, res) => {
  const { message, groupIds } = req.body;
  const file = req.file;

  if (!file || !groupIds || !message) {
    if (file) fs.unlinkSync(file.path);
    return res.status(400).json({ error: "Faltan datos obligatorios (imagen, mensaje o grupos)." });
  }

  const ids = JSON.parse(groupIds);
  res.json({ success: true, status: `Proceso iniciado para ${ids.length} grupos.` });

  // Ejecución en segundo plano para no bloquear el dashboard
  (async () => {
    for (const id of ids) {
      try {
        await userbot.sendFile(id, { file: file.path, caption: message, parseMode: 'html' });
        const delay = Math.floor(Math.random() * (75000 - 45000 + 1) + 45000);
        await new Promise(r => setTimeout(r, delay));
      } catch (err) { console.error(`❌ Error broadcast-image a ${id}:`, err.message); }
    }
    fs.unlinkSync(file.path); // Limpieza final
    console.log("✅ Broadcast de imagen finalizado y archivo temporal borrado.");
  })();
});

// NUEVO: NOTIFICACIÓN MASIVA A USUARIOS REGISTRADOS
app.post("/api/admin/broadcast-users", async (req, res) => {
  const { message, channel } = req.body; // channel: 'whatsapp', 'telegram' o 'both'
  if (!message) return res.status(400).json({ error: "El mensaje es obligatorio." });

  try {
    const { data: users, error } = await supabase.from("app_users").select("email, whatsapp");
    if (error) throw error;

    res.json({ success: true, status: `Notificando a ${users.length} usuarios vía ${channel}.` });

    (async () => {
      for (const user of users) {
        try {
          // Envío vía WhatsApp
          if ((channel === 'whatsapp' || channel === 'both') && user.whatsapp) {
            const chatId = `${user.whatsapp.replace(/\D/g, '')}@c.us`;
            await client.sendMessage(chatId, message);
            await registrarLogWhatsApp(user.email, message, 'saliente');
          }

          // Envío vía Telegram (Bot Oficial como prioridad para usuarios directos)
          if ((channel === 'telegram' || channel === 'both') && telegramBot) {
              // Asumimos que guardamos el telegram_id en algun lado o usamos el email como fallback si el bot tiene mapeo
              // Por ahora, si no tenemos ID directo, este canal requiere implementación de mapeo ID-User
          }

          const delay = Math.floor(Math.random() * (5000 - 2000 + 1) + 2000);
          await new Promise(r => setTimeout(r, delay));
        } catch (err) { console.error(`❌ Error notificando a ${user.email}:`, err.message); }
      }
      console.log("✅ Notificación masiva finalizada.");
    })();
  } catch (err) { res.status(500).json({ error: err.message }); }
});

// =========================================================
// TAREA PREMIUM: REGISTRO SEGURO Y BLOQUEO POR HARDWARE
// =========================================================
app.post("/api/create-user", async (req, res) => {
  const { email, password, whatsapp, deviceId, deviceModel } = req.body;
  if (!email || !password || !deviceId) return res.status(400).json({ error: "Faltan datos obligatorios." });

  try {
    if (!supabase) throw new Error("Servidor no conectado a la base de datos.");
    const { data: existingDevice } = await supabase.from("user_devices").select("id").eq("device_id", deviceId).maybeSingle();
    if (existingDevice) return res.status(403).json({ error: "DEVICE_BLOCKED" });

    const trialDate = new Date();
    trialDate.setDate(trialDate.getDate() + 3);

    const { data: newUser, error: userError } = await supabase.from("app_users").insert([{ email, password, whatsapp: whatsapp || "", active: true, days_remaining: 3, fecha_vencimiento: trialDate.toISOString(), limite_pantallas: 1 }]).select().single();
    if (userError) throw userError;

    const { error: deviceError } = await supabase.from("user_devices").insert([{ user_id: newUser.id, device_id: deviceId, device_model: deviceModel || "Unknown Device" }]);
    if (deviceError) throw deviceError;

    res.json(newUser);
  } catch (err) { res.status(500).json({ error: err.message }); }
});

// =========================================================
// FUNCIÓN MAESTRA DE LIMPIEZA SUPABASE (INFALIBLE)
// =========================================================
async function limpiarCanalesViejos(categoria, urlsActivas) {
    if (!supabase) return;
    try {
        const { data: guardados } = await supabase
            .from("titles")
            .select("id, source_page_url")
            .eq("category", categoria);

        if (!guardados || guardados.length === 0) return;

        const idsParaBorrar = guardados
            .filter(item => !urlsActivas.includes(item.source_page_url))
            .map(item => item.id);

        if (idsParaBorrar.length > 0) {
            await supabase.from("titles").delete().in("id", idsParaBorrar);
            console.log(`🧹 LIMPIEZA SUPABASE: Se borraron ${idsParaBorrar.length} canales caídos de '${categoria}'.`);
        }
    } catch (e) {
        console.error(`⚠️ Error limpiando '${categoria}':`, e.message);
    }
}

// =========================================================
// MÓDULO: TVLIBR3.COM
// =========================================================
async function extractorTvLibr3(url) {
  let browser = null;
  const logic = async () => {
    browser = await chromium.launch({
        headless: process.env.HEADLESS === 'false' ? false : true,
        args: ['--start-maximized', '--no-sandbox', '--disable-setuid-sandbox']
    });
    const context = await browser.newContext({ userAgent: BROWSER_UA });
    context.on('page', async popup => { await popup.close().catch(()=>{}); });
    const page = await context.newPage();

    // Interceptamos .m3u8 pero mantenemos el iframe como base estable
    const m3u8Set = new Set();
    page.on('request', request => { if (request.url().includes('.m3u8')) m3u8Set.add(request.url()); });

    await page.goto(url, { waitUntil: "domcontentloaded", timeout: 25000 }).catch(() => {});
    await page.waitForTimeout(4000);

    let iframeSrc = await page.evaluate(() => {
        const iframe = document.querySelector('div#dontfoid iframe, .player iframe');
        return iframe ? iframe.src : null;
    }).catch(() => null);

    if (!iframeSrc) {
        try { await page.locator('text=/Opci[oó]n 1/i').first().click({ timeout: 5000 }); } catch(e) {}
        await page.waitForTimeout(2000);
        iframeSrc = await page.evaluate(() => {
            const iframe = document.querySelector('div#dontfoid iframe, .player iframe');
            return iframe ? iframe.src : null;
        }).catch(() => null);
    }

    const titleRaw = await page.evaluate(() => {
        const h1 = document.querySelector('h1');
        return h1 ? h1.innerText : document.title;
    }).catch(() => "Canal TVLibr3");

    const title = titleRaw.replace(/Online en VIVO y en directo/gi, '').trim();
    const imageUrl = `https://ui-avatars.com/api/?name=${encodeURIComponent(title)}&background=000&color=fff`;

    const playable = Array.from(m3u8Set)[0] || iframeSrc || "";

    return {
        source_page_url: url,
        title: title,
        description: "",
        magnet_link: null,
        iframe_srcs: iframeSrc ? [iframeSrc] : [],
        m3u8_links: Array.from(m3u8Set),
        playable_url: playable,
        is_live: true,
        category: 'Canales 24/7',
        type: 'live',
        published: true,
        poster_url: imageUrl
    };
  };

  try { return await withTimeout(logic(), SCRAPER_HARD_TIMEOUT, "extractorTvLibr3"); }
  catch (err) {
    console.error(`❌ Error en extractorTvLibr3 para ${url}:`, err.message);
    return { source_page_url: url, title: "Error de Carga", description: "", iframe_srcs: [], m3u8_links: [], playable_url: "" };
  }
  finally { if (browser) await browser.close().catch(() => {}); }
}

async function ejecutarSincronizacionTvLibr3() {
  const baseUrl = "https://tvlibr3.com/";
  let browser = null;
  try {
    browser = await chromium.launch({
        headless: process.env.HEADLESS === 'false' ? false : true,
        args: ['--start-maximized', '--no-sandbox', '--disable-setuid-sandbox']
    });
    const context = await browser.newContext({ userAgent: BROWSER_UA });
    const page = await context.newPage();
    await page.goto(baseUrl, { waitUntil: "domcontentloaded", timeout: 30000 });
    const links = await page.$$eval("a", a => a.map(l => l.href));
    const uniqueLinks = [...new Set(links)].filter(href => href.includes("tvlibr3.com/en-vivo/"));
    await browser.close();
    browser = null;

    const canalesExtraidos = [];
    for (const url of uniqueLinks) {
        try {
            const data = await extractorTvLibr3(url);
            if (data && data.playable_url && data.title !== "Error de Carga") {
                canalesExtraidos.push(data);
            }
        } catch (err) {
            console.error(`⚠️ Fallo individual omitido para ${url}:`, err.message);
        }
    }

    if (canalesExtraidos.length > 0 && supabase) {
      await supabase.from("titles").upsert(canalesExtraidos, { onConflict: "source_page_url" });
      return { success: true, count: canalesExtraidos.length };
    }
    return { success: true, count: 0 };
  } catch (err) { return { success: false, error: err.message }; }
  finally { if (browser) await browser.close().catch(() => {}); }
}

// =========================================================
// MÓDULO: CANALES ARGENTINOS (TVLIBRE-ONLINE.COM) - Lógica de Agenda
// =========================================================
// =========================================================
// MÓDULO: CANALES ARGENTINOS (NUEVO: TELELIBREE.COM)
// =========================================================
async function scrapeCanalesArgentinos() {
  const url = "https://telelibree.com/"; // NUEVA URL BASE
  let browser = null;

  const logic = async () => {
    console.log(`[1/3] 🚀 Iniciando extractor de Canales Argentinos (TeleLibre)...`);
    browser = await chromium.launch({
        headless: process.env.HEADLESS === 'false' ? false : true,
        args: ['--start-maximized', '--no-sandbox', '--disable-setuid-sandbox']
    });
    const context = await browser.newContext({ userAgent: BROWSER_UA });
    const page = await context.newPage();

    try {
        console.log(`[2/3] 🌐 Navegando a: ${url}`);
        await page.goto(url, { waitUntil: "domcontentloaded", timeout: 25000 });
        await page.waitForTimeout(4000);

        console.log(`[3/3] 🔍 Extrayendo catálogo de canales...`);
        const canales = await page.evaluate(() => {
          const results = [];
          const enlaces = Array.from(document.querySelectorAll('a[href*="/en-vivo/"]'));

          enlaces.forEach(link => {
            const img = link.querySelector('img');
            let title = "";

            if (img && img.alt) {
                title = img.alt.trim();
            } else if (link.innerText.trim().length > 0) {
                title = link.innerText.trim();
            } else {
                const parts = link.href.split('/').filter(Boolean);
                title = parts[parts.length - 1].replace(/-/g, ' ').toUpperCase();
            }

            if (title && link.href && !link.href.includes('whatsapp') && !link.href.includes('facebook')) {
              let rawUrl = link.href.trim();

              results.push({
                title: title,
                source_page_url: rawUrl,
                playable_url: rawUrl,
                is_live: true,
                category: 'Canales Argentinos',
                type: 'live',
                published: true,
                poster_url: img && img.src ? img.src : `https://ui-avatars.com/api/?name=${encodeURIComponent(title)}&background=000&color=fff`
              });
            }
          });
          return results;
        });

        const uniqueCanales = canales.filter((v, i, a) => a.findIndex(t => (t.source_page_url === v.source_page_url)) === i);
        console.log(`✅ Extracción finalizada: Encontrados ${uniqueCanales.length} Canales Argentinos.`);
        return uniqueCanales;
    } finally {
        await page.close().catch(() => {});
        await context.close().catch(() => {});
    }
  };

  try { return await withTimeout(logic(), SCRAPER_HARD_TIMEOUT, "scrapeCanalesArgentinos"); }
  catch (err) {
    await registrarError(err, "scrapeCanalesArgentinos");
    return null;
  }
  finally { if (browser) await browser.close().catch(() => {}); }
}

async function ejecutarSincronizacionCanalesArgentinos() {
  try {
    const canales = await scrapeCanalesArgentinos();
    if (!supabase) return { success: false, error: "Supabase no conectado." };

    if (canales.length > 0) {
      console.log(`📤 Subiendo Canales Argentinos a Supabase (${canales.length} canales)...`);
      const { error } = await supabase.from("titles").upsert(canales, { onConflict: "source_page_url" });
      if (error) throw error;

      // Usamos el limpiador infalible para borrar los que ya no existen
      const urlsActivas = canales.map(c => c.source_page_url);
      await limpiarCanalesViejos("Canales Argentinos", urlsActivas);

      return { success: true, count: canales.length };
    } else {
      await limpiarCanalesViejos("Canales Argentinos", []);
    }

    return { success: true, count: 0 };
  } catch (err) { return { success: false, error: err.message }; }
}

// =========================================================
// TAREA 1: EXTRACCIÓN DE AGENDA (INFALIBLE)
// =========================================================
async function scrapeAgenda() {
  const url = "https://streamtp.sbs/eventos.html";
  let browser = null;

  const logic = async () => {
    console.log(`[1/4] 🚀 Iniciando extractor avanzado de Agenda...`);
    browser = await chromium.launch({
        headless: process.env.HEADLESS === 'false' ? false : true,
        args: ['--start-maximized', '--no-sandbox', '--disable-setuid-sandbox']
    });
    const context = await browser.newContext({ userAgent: BROWSER_UA });
    const page = await context.newPage();

    try {
        console.log(`[2/4] 🌐 Navegando a: ${url}`);
        await page.goto(url, { waitUntil: "domcontentloaded", timeout: 25000 });
        await page.waitForTimeout(5000);

        console.log(`[3/4] 🔍 Leyendo inputs de enlace y atributos ocultos...`);

        const eventos = await page.evaluate(() => {
          const results = [];
          const seenUrls = new Set();

          const inputsLinks = document.querySelectorAll('input.iframe-link');

          inputsLinks.forEach(input => {
            const rawUrl = input.value.trim();
            if (!rawUrl || seenUrls.has(rawUrl)) return;
            seenUrls.add(rawUrl);

            const playableUrl = rawUrl.replace('global1.php', 'global2.php');

            const eventoContenedor = input.closest('.event, .match-item, tr');
            let eventName = "";

            if (eventoContenedor) {
                eventName = eventoContenedor.getAttribute('data-title') || "";
            }

            const itemEnlaceContenedor = input.closest('.event-link-item, .card-body, tr');
            let channelName = "DIRECTO";

            if (itemEnlaceContenedor) {
                const badgeServer = itemEnlaceContenedor.querySelector('.badge-server, span.meta-badge.badge-server');
                if (badgeServer) {
                    channelName = badgeServer.textContent.replace(/FHD|HD|MX|"/gi, '').trim().toUpperCase();
                }
            }

            eventName = eventName.replace(/EN VIVO|LIVE|VER|ESPAÑOL|1080P/gi, '').replace(/\s+/g, ' ').trim();

            let finalTitle = channelName;
            if (eventName && eventName.length > 3 && eventName.toUpperCase() !== channelName.toUpperCase()) {
                finalTitle = `${channelName} | ${eventName}`;
            }

            results.push({
              title: finalTitle,
              source_page_url: rawUrl,
              playable_url: playableUrl,
              is_live: true,
              category: 'Eventos Deportivos',
              type: 'live',
              published: true,
              poster_url: `https://ui-avatars.com/api/?name=${encodeURIComponent(channelName)}&background=000&color=fff`
            });
          });
          return results;
        });

        console.log(`[4/4] ✅ Extracción finalizada: Encontrados ${eventos.length} eventos en vivo.`);
        return eventos;
    } finally {
        await page.close().catch(() => {});
        await context.close().catch(() => {});
    }
  };

  try { return await withTimeout(logic(), SCRAPER_HARD_TIMEOUT, "scrapeAgenda"); }
  catch (err) {
    await registrarError(err, "scrapeAgenda");
    return [];
  }
  finally { if (browser) await browser.close().catch(() => {}); }
}

async function ejecutarSincronizacionAgenda() {
  try {
    const eventos = await scrapeAgenda();
    if (!supabase) return { success: false, error: "Supabase no conectado." };

    if (eventos.length > 0) {
      console.log(`📤 Subiendo agenda a Supabase (${eventos.length} partidos)...`);
      const { error } = await supabase.from("titles").upsert(eventos, { onConflict: "source_page_url" });
      if (error) throw error;

      const urlsActivas = eventos.map(e => e.source_page_url);
      await limpiarCanalesViejos("Eventos Deportivos", urlsActivas);

      return { success: true, count: eventos.length };
    } else {
      await limpiarCanalesViejos("Eventos Deportivos", []);
    }

    return { success: true, count: 0 };
  } catch (err) { return { success: false, error: err.message }; }
}

// =========================================================
// TAREA 2: EXTRACCIÓN AUTOMÁTICA DE CANALES 24/7
// =========================================================
async function scrapeCanalesRoot(urlObjetivo) {
  const url = urlObjetivo || "https://streamtp.sbs/";
  let browser = null;

  const logic = async () => {
    console.log(`[1/4] 🚀 Iniciando nuevo scraper 24/7 en: ${url}`);
    browser = await chromium.launch({
        headless: process.env.HEADLESS === 'false' ? false : true,
        args: ['--start-maximized', '--no-sandbox', '--disable-setuid-sandbox']
    });
    const context = await browser.newContext({ userAgent: BROWSER_UA });
    const page = await context.newPage();

    await page.goto(url, { waitUntil: "domcontentloaded", timeout: 20000 });
    await page.waitForTimeout(4000);

    const canales = await page.evaluate(() => {
      const results = [];
      const bloquesCanales = Array.from(document.querySelectorAll('div.card-body, .card, div.channel-card, li, .col-md-3'));

      bloquesCanales.forEach(bloque => {
        const hElement = bloque.querySelector('h2, h1, h3, .channel-title');
        const textoTarjeta = bloque.innerText.toUpperCase();
        const estaActivo = textoTarjeta.includes('ACTIVO') || bloque.querySelector('.status-active, .green-dot');
        const linkBtn = bloque.querySelector('a');

        if (hElement && linkBtn && estaActivo) {
          let title = hElement.textContent.replace('●', '').replace('Activo', '').replace('Inactivo', '').trim();
          let rawUrl = linkBtn.href ? linkBtn.href.trim() : null;

          if (rawUrl && rawUrl.includes('http') && !rawUrl.includes('eventos.html')) {
            results.push({
              title: title,
              source_page_url: rawUrl,
              playable_url: rawUrl.replace('global1.php', 'global2.php'),
              is_live: true,
              category: 'Canales 24/7',
              type: 'live',
              published: true,
              poster_url: `https://ui-avatars.com/api/?name=${encodeURIComponent(title)}&background=000&color=fff`
            });
          }
        }
      });
      return results;
    });
    return canales;
  };

  try { return await withTimeout(logic(), SCRAPER_HARD_TIMEOUT, "scrapeCanalesRoot"); }
  catch (err) { console.error("❌ Error en Scraper 24/7:", err.message); return []; }
  finally { if (browser) await browser.close().catch(() => {}); }
}

async function ejecutarSincronizacionCanales247(urlObjetivo = null) {
  try {
    const canalesActivos = await scrapeCanalesRoot(urlObjetivo);
    if (!supabase) return { success: false, error: "Supabase no conectado." };

    if (canalesActivos.length > 0) {
      console.log(`📤 Sincronizando ${canalesActivos.length} canales con Supabase...`);
      const { error: upsertError } = await supabase.from("titles").upsert(canalesActivos, { onConflict: "source_page_url" });
      if (upsertError) throw upsertError;

      const urlsActivas = canalesActivos.map(c => c.source_page_url);
      await limpiarCanalesViejos("Canales 24/7", urlsActivas);

      return { success: true, count: canalesActivos.length };
    } else {
      await limpiarCanalesViejos("Canales 24/7", []);
    }
    return { success: true, count: 0 };
  } catch (err) { console.error("❌ Error Sync 24/7:", err.message); return { success: false, error: err.message }; }
}

// =========================================================
// TAREA 3: SCRAPEO PROFUNDO (ROUTER PRINCIPAL)
// =========================================================
async function scrapeData(url) {
  if (url && url.includes("tvlibr3.com")) return await extractorTvLibr3(url);
  let browser = null;
  const logic = async () => {
    browser = await chromium.launch({
        headless: process.env.HEADLESS === 'false' ? false : true,
        args: ['--start-maximized', '--no-sandbox', '--disable-setuid-sandbox']
    });
    const context = await browser.newContext({ userAgent: BROWSER_UA });
    const page = await context.newPage();
    const m3u8Set = new Set();

    try {
        page.on('request', request => { if (request.url().includes('.m3u8')) m3u8Set.add(request.url()); });
        await page.goto(url, { waitUntil: "domcontentloaded", timeout: 20000 });
        await page.waitForTimeout(5000);

        try {
            await page.mouse.click(640, 400);
            await page.waitForTimeout(2000);
            await page.mouse.click(640, 400);
        } catch (e) { /* ignore mouse errors */ }

        const extraData = await page.evaluate(() => {
            const h1 = document.querySelector('h1.details_title');
            const finalTitle = h1 ? h1.innerText.trim() : document.title.replace(' - PelisPanda', '').trim();
            let desc = "";
            const metaDesc = document.querySelector('meta[name="description"]');
            if (metaDesc && metaDesc.content) desc = metaDesc.content.trim();
            let magnet = null;
            const downloadBtns = document.querySelectorAll('a.btn.btn-primary.dwnld.dwnlds, a.btn-primary.dwnld');
            for (let btn of downloadBtns) { if (btn.href && btn.href.startsWith('magnet:')) { magnet = btn.href; break; } }
            return { title: finalTitle, description: desc, magnet };
        }).catch(() => ({ title: "Sin Título", description: "", magnet: null }));

        const iframes = await page.$$eval("iframe", (all) => all.map(f => f.src)).catch(() => []);

        return { source_page_url: url, title: extraData.title, description: extraData.description, magnet_link: extraData.magnet, iframe_srcs: iframes.filter(src => src && !src.includes("ads")), m3u8_links: Array.from(m3u8Set) };
    } finally {
        await page.close().catch(() => {});
        await context.close().catch(() => {});
    }
  };

  try { return await withTimeout(logic(), SCRAPER_HARD_TIMEOUT, "scrapeData"); }
  catch (err) {
    await registrarError(err, `scrapeData: ${url}`);
    if (browser) await browser.close().catch(() => {});
    throw err;
  } finally {
    if (browser) await browser.close().catch(() => {});
  }
}

// =========================================================
// ENDPOINTS DE LA API GENERAL
// =========================================================
async function trackUserPresence(req) {
  const idDelUsuario = req.body.userId || req.body.token || req.body.id;
  if (supabase && idDelUsuario) {
    try { await supabase.from('app_users').update({ ultima_conexion: new Date().toISOString() }).eq('id', idDelUsuario); } catch (e) {}
  }
}

app.post("/api/check-status", async (req, res) => {
  const { id } = req.body;
  if (!id) return res.status(400).json({ error: "ID requerido" });
  try {
    trackUserPresence(req);
    const { data, error } = await supabase.from("app_users").select("id,email,active,days_remaining,fecha_vencimiento,limite_pantallas,bypass_qr").eq("id", id).maybeSingle();
    if (error) throw error;
    if (data && (data.bypass_qr === true || data.active === true)) { data.active = true; data.days_remaining = 999; }
    res.json(data);
  } catch (err) { res.status(500).json({ error: err.message }); }
});

// =========================================================
// ENDPOINT: FEED INTELIGENTE (AGRUPADO PARA SERIES)
// =========================================================
app.post("/api/get-feed", async (req, res) => {
  try {
    trackUserPresence(req);

    // DETECCIÓN DE VERSIÓN PARA ACTUALIZACIÓN FORZADA
    const appVersion = req.headers['x-app-version'] || req.body.version;
    const apkPath = "D:/pelis/apk/update.json";
    let minVersion = 0;

    if (fs.existsSync(apkPath)) {
        const updateData = JSON.parse(fs.readFileSync(apkPath, 'utf8'));
        minVersion = updateData.versionCode || 0;
    }

    // Si la versión es antigua y queremos forzar (o si no envía versión), bloqueamos el feed
    if (!appVersion || parseInt(appVersion) < minVersion) {
        console.log(`⚠️ Bloqueando acceso a versión antigua: ${appVersion || 'Desconocida'}. Requerida: ${minVersion}`);

        // Enviamos un Feed de "Emergencia" que obliga a actualizar
        return res.json([{
            id: "force_update",
            title: "⚠️ ACTUALIZACIÓN OBLIGATORIA",
            description: "Tu versión de CuevanaTV es demasiado antigua. Para seguir disfrutando del contenido, por favor descarga la nueva versión usando el código QR o el botón de actualización.",
            poster_url: "https://i.imgur.com/VEfStXp.png", // Tu logo o un QR
            category: "SISTEMA",
            type: "live",
            is_live: true,
            playable_url: "http://cuevana-tv-arg.duckdns.org/apk/app-release.apk"
        }]);
    }

    // 1. Obtenemos todos los títulos publicados
    const { data: titles, error: titleError } = await supabase
      .from("titles")
      .select("id, title, description, poster_url, source_page_url, category, type, is_live, playable_url")
      .eq("published", true)
      .order("title", { ascending: true });

    if (titleError) throw titleError;

    // 2. Obtenemos todos los servidores/capítulos para poder agruparlos
    const { data: allServers, error: serverError } = await supabase
      .from("servers")
      .select("*")
      .order("episode_number", { ascending: true });

    if (serverError) throw serverError;

    // 3. Formateamos la respuesta según lo que espera el APK
    const formattedFeed = titles.map(title => {
      // Normalizamos la URL principal del título
      const cleanedTitle = {
        ...title,
        playable_url: cleanUrl(title.playable_url)
      };

      // Si es una serie, buscamos sus capítulos para anidarlos
      if (title.type === 'series' || title.category === 'Series') {
        const episodesRaw = allServers.filter(s => s.title_id === title.id);

        // FILTRADO SENIOR ANTI-DUPLICADOS: Agrupamos por temporada y episodio
        const uniqueEpisodesMap = new Map();

        episodesRaw.forEach(s => {
          const season = s.season_number || 1;
          const episode = s.episode_number || 1;
          const key = `${season}_${episode}`;

          if (!uniqueEpisodesMap.has(key)) {
            uniqueEpisodesMap.set(key, {
              id: s.id,
              name: s.name || `Capítulo ${episode}`,
              playable_url: cleanUrl(s.playable_url), // LIMPIEZA APLICADA AQUÍ
              season: season,
              episode: episode,
              episode_number: episode, // APK usa episode_number para la UI
              season_number: season
            });
          }
        });

        const sortedEpisodes = Array.from(uniqueEpisodesMap.values()).sort((a, b) => {
          if (a.season !== b.season) return a.season - b.season;
          return a.episode - b.episode;
        });

        return {
          ...cleanedTitle,
          episodes: sortedEpisodes // El APK ahora recibirá una lista limpia y ordenada
        };
      }
      return cleanedTitle;
    });

    res.json(formattedFeed);
  } catch (err) {
    console.error("❌ Error en /api/get-feed:", err.message);
    res.status(500).json({ error: err.message });
  }
});

// =========================================================
// ENDPOINT: HARD RESET Y RESINCRO DE SERIES LOCALES (SEGURIZADO)
// =========================================================
app.post("/api/reset-and-sync-series", async (req, res) => {
    const { directoryPath } = req.body;

    if (!directoryPath || !fs.existsSync(directoryPath)) {
        return res.status(400).json({ error: "Ruta de directorio inválida o no proporcionada." });
    }

    try {
        console.log("-------------------------------------------------------");
        console.log("🧹 PASO 1: Limpiando base de datos (Categoría: Series)...");

        const { error: delError } = await supabase
            .from('titles')
            .delete()
            .eq('category', 'Series');

        if (delError) throw new Error(`Fallo al borrar: ${delError.message}`);
        console.log("✅ Limpieza de Supabase completada.");

        console.log("📂 PASO 2: Escaneando archivos locales y parseando metadatos...");
        const seriesMap = new Map();

        const scanDir = (dir) => {
            const items = fs.readdirSync(dir);
            for (const item of items) {
                const fullPath = path.join(dir, item);
                const stat = fs.statSync(fullPath);
                if (stat.isDirectory()) {
                    scanDir(fullPath);
                } else if (item.match(/\.(mp4|mkv|avi|mov)$/i)) {
                    const match = item.match(/^(.+?)\.[sS](\d+)[eE](\d+)/i);
                    if (match) {
                        const [_, rawName, season, episode] = match;
                        const showName = rawName.replace(/\./g, ' ').trim();
                        if (!seriesMap.has(showName)) seriesMap.set(showName, []);
                        seriesMap.get(showName).push({
                            season: parseInt(season),
                            episode: parseInt(episode),
                            fullPath: fullPath
                        });
                    }
                }
            }
        };

        scanDir(directoryPath);
        console.log(`📊 Escaneo finalizado: ${seriesMap.size} series detectadas.`);

        console.log("🚀 PASO 3: Reconstruyendo catálogo en Supabase...");
        let totalCreated = 0;

        for (const [showName, episodes] of seriesMap.entries()) {
            const { data: titleData, error: titleError } = await supabase
                .from('titles')
                .insert([{
                    title: showName,
                    type: 'series',
                    category: 'Series',
                    published: true,
                    description: `Sincronizada automáticamente desde ${directoryPath}`,
                    poster_url: `https://ui-avatars.com/api/?name=${encodeURIComponent(showName)}&background=000&color=fff&size=512`
                }])
                .select()
                .single();

            if (titleError) { console.error(`❌ Error título ${showName}:`, titleError.message); continue; }

            const serversPayload = episodes.map(ep => {
                // FIX SENIOR: Eliminamos el prefijo /Principal/ para que mapee directo a D:\pelis
                const relativePath = path.relative(directoryPath, ep.fullPath).replace(/\\/g, '/');
                const encodedPath = relativePath.split('/')
                    .filter(segment => segment.length > 0)
                    .map(segment => encodeURIComponent(segment))
                    .join('/');

                return {
                    title_id: titleData.id,
                    name: `Capítulo ${ep.episode}`,
                    playable_url: `http://cuevana-tv-arg.duckdns.org/${encodedPath}`,
                    season_number: ep.season,
                    episode_number: ep.episode,
                    priority: 0
                };
            });

            const { error: serverError } = await supabase
                .from('servers')
                .upsert(serversPayload, {
                    onConflict: 'title_id,season_number,episode_number',
                    ignoreDuplicates: false
                });

            if (serverError) console.error(`❌ Error capítulos ${showName}:`, serverError.message);
            else totalCreated++;
        }

        res.json({ success: true, seriesSincronizadas: totalCreated });

    } catch (err) {
        console.error("🔥 FALLO CRÍTICO:", err.message);
        res.status(500).json({ error: err.message });
    }
});

app.post("/api/sync-agenda", async (req, res) => {
  try { res.json(await ejecutarSincronizacionAgenda()); } catch (err) { res.status(500).json({ error: err.message }); }
});

app.post("/api/sync-channels-247", async (req, res) => {
  const { url } = req.body;
  try { res.json(await ejecutarSincronizacionCanales247(url)); } catch (err) { res.status(500).json({ error: err.message }); }
});

app.post("/api/sync-tvlibr3", async (req, res) => {
  res.json({ success: true, message: "Sincronización TvLibr3 iniciada." });
  ejecutarSincronizacionTvLibr3();
});

// NUEVO: ENDPOINT PARA CANALES ARGENTINOS
app.post("/api/sync-canales-argentinos", async (req, res) => {
  try { res.json(await ejecutarSincronizacionCanalesArgentinos()); } catch (err) {
    await registrarError(err, "POST /api/sync-canales-argentinos");
    res.status(500).json({ error: err.message });
  }
});

// ENDPOINT: PUBLICACIÓN EN INSTAGRAM (PROXY AL MOTOR PYTHON PORT 5000)
app.post("/api/instagram/publish", upload.single("image"), async (req, res) => {
    const { caption, ig_user, ig_pass } = req.body;
    const file = req.file;

    if (!file) return res.status(400).json({ error: "Imagen requerida (FormData 'image')" });

    const pythonStaticDir = "C:/Users/Admin/Pictures/cuevana/static";
    const tempFileName = `temp_story_${Date.now()}.jpg`;
    const tempFilePath = path.join(pythonStaticDir, tempFileName);

    try {
        console.log("📲 Guardando imagen en carpeta estática del motor Python...");
        fs.copyFileSync(file.path, tempFilePath);

        const pythonRes = await fetch("http://localhost:5000/publicar_historia", {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                video_file: tempFileName,
                caption: caption || '¡Mira el partido hoy en CuevanaTV!',
                ig_user: ig_user || 'cuevanatvarg',
                ig_pass: ig_pass || 'Amarilla34339356'
            })
        });

        const result = await pythonRes.json();
        if (fs.existsSync(file.path)) fs.unlinkSync(file.path);

        if (pythonRes.ok) {
            res.json(result);
        } else {
            throw new Error(result.log || "Fallo en el motor de Instagram.");
        }
    } catch (err) {
        if (file && fs.existsSync(file.path)) fs.unlinkSync(file.path);
        await registrarError(err, "POST /api/instagram/publish");
        res.status(500).json({ error: err.message });
    }
});

/**
 * NUEVO: Publicar en Instagram desde una URL (para portadas existentes)
 */
app.post("/api/instagram/publish-url", async (req, res) => {
    const { imageUrl, caption, ig_user, ig_pass } = req.body;

    if (!imageUrl) return res.status(400).json({ error: "URL de imagen requerida" });

    const pythonStaticDir = "C:/Users/Admin/Pictures/cuevana/static";
    const tempFileName = `temp_poster_${Date.now()}.jpg`;
    const tempFilePath = path.join(pythonStaticDir, tempFileName);

    try {
        console.log(`📲 Descargando imagen desde URL para Instagram: ${imageUrl}`);
        const response = await fetch(imageUrl);
        if (!response.ok) throw new Error("No se pudo descargar la imagen de la URL proporcionada.");

        const arrayBuffer = await response.arrayBuffer();
        const buffer = Buffer.from(arrayBuffer);
        fs.writeFileSync(tempFilePath, buffer);

        const pythonRes = await fetch("http://localhost:5000/publicar_historia", {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                video_file: tempFileName,
                caption: caption || '¡Mira esto en CuevanaTV!',
                ig_user: ig_user || 'cuevanatvarg',
                ig_pass: ig_pass || 'Amarilla34339356'
            })
        });

        const result = await pythonRes.json();
        if (pythonRes.ok) {
            res.json(result);
        } else {
            throw new Error(result.log || "Fallo en el motor de Instagram.");
        }
    } catch (err) {
        await registrarError(err, "POST /api/instagram/publish-url");
        res.status(500).json({ error: err.message });
    }
});

/**
 * NUEVO: Publicar en Instagram (Post Fijo) desde una URL
 */
app.post("/api/instagram/publish-url-post", async (req, res) => {
    const { imageUrl, caption, ig_user, ig_pass } = req.body;
    if (!imageUrl) return res.status(400).json({ error: "URL de imagen requerida" });

    const pythonStaticDir = "C:/Users/Admin/Pictures/cuevana/static";
    const tempFileName = `temp_post_${Date.now()}.jpg`;
    const tempFilePath = path.join(pythonStaticDir, tempFileName);

    try {
        const response = await fetch(imageUrl);
        if (!response.ok) throw new Error("No se pudo descargar la imagen.");
        const buffer = Buffer.from(await response.arrayBuffer());
        fs.writeFileSync(tempFilePath, buffer);

        const pythonRes = await fetch("http://localhost:5000/publicar_post", {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                video_file: tempFileName,
                caption: caption || '¡Nuevo estreno en CuevanaTV!',
                ig_user: ig_user || 'cuevanatvarg',
                ig_pass: ig_pass || 'Amarilla34339356'
            })
        });

        const result = await pythonRes.json();
        res.status(pythonRes.ok ? 200 : 500).json(result);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post("/api/sync-movie", async (req, res) => {
  const { url } = req.body;
  if (!url) return res.status(400).json({ error: "URL requerida" });
  try {
    const data = await scrapeData(url);
    if (supabase) {
      await supabase.from("titles").upsert({ title: data.title, description: data.description, fallback_magnet: data.magnet_link, source_page_url: data.source_page_url, playable_url: data.m3u8_links[0] || data.iframe_srcs[0] || "", type: 'movie', published: true, category: "Novedades" }, { onConflict: "source_page_url" });
    }
    res.json(data);
  } catch (err) { res.status(500).json({ error: err.message }); }
});

app.post("/api/sync-live", async (req, res) => {
  const { url } = req.body;
  if (!url) return res.status(400).json({ error: "URL requerida" });
  try {
    const data = await scrapeData(url);
    const playUrl = data.m3u8_links[0] || data.iframe_srcs[0] || "";
    if (supabase) {
      await supabase.from("titles").upsert({ title: data.title, source_page_url: data.source_page_url, playable_url: playUrl, type: 'live', is_live: true, published: true, category: "En Vivo", poster_url: data.poster_url || "https://ui-avatars.com/api/?name=LIVE&background=FF0000&color=fff" }, { onConflict: "source_page_url" });
    }
    res.json({ success: true, title: data.title });
  } catch (err) { res.status(500).json({ error: err.message }); }
});

app.post("/api/refresh-all-live", async (req, res) => {
  ejecutarSincronizacionAgenda();
  ejecutarSincronizacionCanales247();
  ejecutarSincronizacionTvLibr3();
  ejecutarSincronizacionCanalesArgentinos(); // AGREGADO AL REFRESH GENERAL
  res.json({ success: true, message: "Actualización manual iniciada" });
});

// --- MOTOR DE PROMPTS CUEVANATV IA (SENIOR V3) ---
function generarPromptDeportivo(informacionEvento, estiloVisual) {
    // 1. Extracción de Equipos (Regex inteligente para "Equipo A vs Equipo B")
    const match = informacionEvento.match(/^(.+?)\s+vs\s+([^|*]+)/i);
    const equipoA = match ? match[1].trim() : "Equipos Estelares";
    const equipoB = match ? match[2].trim() : "Gran Final";

    // 2. Diccionario de Estilos Visuales (Keywords de iluminación y atmósfera)
    const estilosIA = {
        "epic": `Cinematic football pitch at night, intense fire flames surrounding the grass, sparks of red and gold, dramatic lighting, foggy atmosphere, blurred flags of ${equipoA} and ${equipoB} on the stands.`,
        "stadium": `Hyper-realistic wide angle of a football stadium, vibrant green grass, bright floodlights, misty atmosphere, giant holographic flags of ${equipoA} and ${equipoB} floating in the sky.`,
        "neon": `Cyberpunk style sports stadium, neon magenta and cyan beams, futuristic digital grass, electric rain, glowing wireframe silhouettes of players from ${equipoA} and ${equipoB}.`,
        "minimal": `Minimalist dark premium aesthetic, deep black background, soft moonlight on a football ball, elegant bokeh, accent colors based on ${equipoA} and ${equipoB} flags.`
    };

    const basePrompt = estilosIA[estiloVisual] || estilosIA["epic"];
    return `${basePrompt} Portrait orientation, 9:16 aspect ratio, mobile wallpaper style, cinematic 8k, photorealistic, NO TEXT, NO LETTERS, NO NUMBERS, EMPTY CENTER space for overlay.`;
}

// =========================================================
// ENDPOINT: ENRIQUECIMIENTO DE PROMPTS CON IA (FLAGS + PLAYERS)
// =========================================================
app.post("/api/admin/enrich-prompt", async (req, res) => {
    const { matchTitle, style } = req.body;
    if (!matchTitle || matchTitle.includes('--')) {
        return res.status(400).json({ error: "Título del partido no válido" });
    }

    try {
        console.log(`🧠 Enriqueciendo prompt con IA Gratis (Pollinations) para: ${matchTitle} (${style})`);

        const systemPrompt = `Actúa como un experto en Ingeniería de Prompts para generación de imágenes (Stable Diffusion).
        Tu objetivo es crear un prompt altamente detallado en INGLÉS para un partido de fútbol: "${matchTitle}".
        Instrucciones:
        1. Identifica los equipos o países.
        2. Incluye elementos visuales como banderas, escudos o colores.
        3. Describe jugadores estrella en pose heroica.
        4. Adapta la atmósfera al estilo: "${style}".
        5. OBLIGATORIO incluir al final: "Portrait orientation, 9:16 aspect ratio, cinematic lighting, hyper-realistic, 8k, photorealistic, NO TEXT, NO LETTERS, NO NUMBERS, EMPTY CENTER space for overlay".
        Responde SOLO con el texto del prompt, sin introducciones.`;

        const pollinationsUrl = `https://text.pollinations.ai/${encodeURIComponent(systemPrompt)}`;
        const response = await fetch(pollinationsUrl);

        if (!response.ok) throw new Error("IA Gratis no disponible");

        const enrichedPrompt = await response.text();

        console.log("✅ Prompt enriquecido generado con IA Gratis.");
        res.json({ success: true, prompt: enrichedPrompt.trim() });
    } catch (err) {
        console.error("❌ Error en IA Gratis (Enrich):", err.message);
        res.status(500).json({ success: false, error: "Fallo al conectar con IA Gratis" });
    }
});

app.post("/api/generate-bg", async (req, res) => {
  const { prompt } = req.body;
  if (!prompt) return res.status(400).json({ error: "Prompt requerido" });

  try {
    const url = `https://image.pollinations.ai/prompt/${encodeURIComponent(prompt)}?width=1080&height=1920&nologo=true&seed=${Math.floor(Math.random() * 100000)}`;
    const response = await fetch(url);
    if (!response.ok) throw new Error("IA no disponible temporalmente");

    const arrayBuffer = await response.arrayBuffer();
    const base64Image = Buffer.from(arrayBuffer).toString('base64');
    res.json({ success: true, image: `data:image/jpeg;base64,${base64Image}` });
  } catch (err) {
    console.error("Error IA:", err.message);
    res.status(500).json({ error: err.message });
  }
});

// =========================================================
// MÓDULO: BOT DE WHATSAPP Y ENTRADAS IA
// =========================================================
const client = new Client({
    authStrategy: new LocalAuth(),
    puppeteer: { handleSIGINT: false, args: ['--no-sandbox', '--disable-setuid-sandbox'] }
});

async function registrarLogWhatsApp(email, mensaje, tipo) {
    if (!supabase) return;
    try { await supabase.from('whatsapp_logs').insert([{ user_email: email, mensaje: mensaje, tipo: tipo }]); } catch (e) {
        await registrarError(e, "registrarLogWhatsApp");
    }
}

client.on('qr', (qr) => { qrcode.generate(qr, { small: true }); });
client.on('ready', () => { console.log('✅ [WHATSAPP] Bot conectado de forma segura.'); });

app.post("/api/whatsapp/send", async (req, res) => {
    const { email, message, whatsapp } = req.body;
    if (!message || (!email && !whatsapp)) return res.status(400).json({ error: "Faltan datos." });
    try {
        let targetWhatsapp = whatsapp;
        if (!targetWhatsapp && email) {
            const { data } = await supabase.from('app_users').select('whatsapp').eq('email', email).maybeSingle();
            targetWhatsapp = data?.whatsapp;
        }
        if (!targetWhatsapp) throw new Error("No se encontró número.");
        const chatId = `${targetWhatsapp.replace(/\D/g, '')}@c.us`;
        await client.sendMessage(chatId, message);
        await registrarLogWhatsApp(email || targetWhatsapp, message, 'saliente');
        res.json({ success: true });
    } catch (err) {
        await registrarError(err, "POST /api/whatsapp/send");
        res.status(500).json({ error: err.message });
    }
});

const PORT = process.env.PORT || 8787;
// =========================================================
// ENDPOINT: ESTADO DEL SISTEMA (HEARTBEAT)
// =========================================================
app.get("/api/admin/health", async (req, res) => {
    try {
        const { data: logCount } = await supabase.from('system_logs').select('id', { count: 'exact', head: true });
        const { data: titleCount } = await supabase.from('titles').select('id', { count: 'exact', head: true });

        res.json({
            status: "online",
            timestamp: new Date().toISOString(),
            database: supabase ? "connected" : "error",
            stats: {
                logs: logCount || 0,
                titles: titleCount || 0
            },
            env: {
                headless: process.env.HEADLESS || "true",
                port: PORT
            }
        });
    } catch (err) {
        res.status(500).json({ status: "error", message: err.message });
    }
});

// =========================================================
// ENDPOINT: GESTIÓN DE ACTUALIZACIONES APK
// =========================================================
app.post("/api/admin/update-apk-version", async (req, res) => {
    const { versionCode, versionName, changelog, downloadUrl } = req.body;
    const apkPath = "D:/pelis/apk/update.json";

    try {
        const updateData = {
            versionCode: parseInt(versionCode),
            versionName: versionName,
            downloadUrl: downloadUrl,
            changelog: changelog
        };

        fs.writeFileSync(apkPath, JSON.stringify(updateData, null, 2));
        console.log(`✅ Actualización APK publicada: v${versionName} (${versionCode})`);
        res.json({ success: true, message: "Actualización publicada correctamente." });
    } catch (err) {
        console.error("❌ Error al guardar update.json:", err.message);
        res.status(500).json({ error: "No se pudo guardar la versión." });
    }
});

app.get("/api/admin/get-apk-version", async (req, res) => {
    const apkPath = "D:/pelis/apk/update.json";
    try {
        if (fs.existsSync(apkPath)) {
            const data = fs.readFileSync(apkPath, 'utf8');
            res.json(JSON.parse(data));
        } else {
            res.status(404).json({ error: "No hay versión configurada." });
        }
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

/**
 * NUEVO: Obtener portadas de títulos publicados para Estudio Creativo
 */
app.get("/api/admin/get-posters", async (req, res) => {
    if (!supabase) return res.status(500).json({ error: "Supabase no conectado." });
    try {
        const { data, error } = await supabase
            .from("titles")
            .select("id, title, poster_url, category, type, is_live")
            .eq("published", true)
            .neq("type", "live")
            .or("is_live.is.null,is_live.eq.false")
            .order("created_at", { ascending: false });

        if (error) throw error;
        res.json(data);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Tareas Automatizadas mediante Cron
cron.schedule('*/20 * * * *', async () => {
    try { await ejecutarSincronizacionAgenda(); } catch (e) { await registrarError(e, "Cron: Sincronización Agenda"); }
});

// AGREGADO CANALES ARGENTINOS AL CRON DE CADA 2 HORAS
cron.schedule('0 */2 * * *', async () => {
    try {
        await ejecutarSincronizacionCanales247();
        await ejecutarSincronizacionTvLibr3();
        await ejecutarSincronizacionCanalesArgentinos();
    } catch (e) { await registrarError(e, "Cron: Sincronización Canales 24/7 y Arg"); }
});

// Embudo Automático de Ventas
cron.schedule('0 */6 * * *', async () => {
    if (!supabase || !client.info) return;
    try {
        const ahora = new Date();
        const { data: nuevos } = await supabase.from('app_users').select('id, whatsapp, email').eq('bot_step', 'nuevo');
        for (const user of nuevos || []) {
            if (!user.whatsapp) continue;
            const msg = `*¡Bienvenido a CuevanaTV!* 🎬\n\nGracias por registrarte. Ya tenés tus *3 días de prueba* activos.\n\n📥 *Instalación rápida:* Descargá la app "Downloader" en tu TV y poné el código: *2931858*.\n\n¿Pudiste instalarla bien? Cualquier duda avisame, che.`;
            await enviarMensajeConDelay(user.whatsapp, msg, user.email, 'bienvenida_enviada');
        }

        const ayer = new Date(ahora.getTime() - (24 * 60 * 60 * 1000)).toISOString();
        const { data: seguimientos } = await supabase.from('app_users').select('id, whatsapp, email').eq('bot_step', 'bienvenida_enviada').lt('last_msg_date', ayer);
        for (const user of seguimientos || []) {
            const msg = `¡Hola! Paso por acá para preguntarte si pudiste probar la app y si todo funciona bien. ¿Te quedó alguna duda con la instalación?`;
            await enviarMensajeConDelay(user.whatsapp, msg, user.email, 'seguimiento_enviado');
        }

        const { data: vencidos } = await supabase.from('app_users').select('id, whatsapp, email').eq('days_remaining', 0).neq('bot_step', 'pago_solicitado');
        for (const user of vencidos || []) {
            const msg = `*¡Tu prueba de CuevanaTV terminó!* 🍿\n\n¿Qué te pareció la calidad? Si querés seguir disfrutando de todo el catálogo sin cortes, el abono mensual es de *$5000 ARS*.\n\n💳 *Alias:* 34339356 (Ezequiel Mazzera)\n\n¡No te pierdas los estrenos de hoy! Mandame el comprobante por acá y te lo activo al toque.`;
            await enviarMensajeConDelay(user.whatsapp, msg, user.email, 'pago_solicitado');
        }
    } catch (e) { await registrarError(e, "Cron: Embudo Automático"); }
});

async function enviarMensajeConDelay(whatsapp, mensaje, email, nuevoStep) {
    try {
        const chatId = `${whatsapp.replace(/\D/g, '')}@c.us`;
        const chat = await client.getChatById(chatId);
        await chat.sendStateTyping();
        await new Promise(r => setTimeout(r, 3000));
        await client.sendMessage(chatId, mensaje);
        await supabase.from('app_users').update({ bot_step: nuevoStep, last_msg_date: new Date().toISOString() }).eq('email', email);
        await registrarLogWhatsApp(email, mensaje, 'saliente');
        await new Promise(r => setTimeout(r, Math.random() * 10000 + 10000));
    } catch (e) {}
}

const cooldownsIA = new Map();

client.on('message', async (msg) => {
    if (msg.fromMe || msg.isStatus || msg.broadcast || msg.from.includes('@g.us')) return;

    const contact = await msg.getContact();
    const phone = contact.number;

    const lastAction = cooldownsIA.get(phone) || 0;
    const now = Date.now();
    if (now - lastAction < 10000) return;
    cooldownsIA.set(phone, now);

    if (!supabase) return;

    try {
        const personalNumber = '5491132664036@c.us';

        if (msg.body.toLowerCase().includes("quiero pagar el abono")) {
            const paymentInfo = `*¡Excelente elección!* 🚀\n\nPara activar tu cuenta Premium, realizá la transferencia a estos datos:\n\n*Alias:* 34339356\n*Monto:* $5000\n\n*Importante:* Por favor, pasame la foto del comprobante por acá así te activo la cuenta de inmediato.`;
            await msg.reply(paymentInfo);
            return;
        }

        if (msg.hasMedia) {
            const media = await msg.downloadMedia();
            await client.sendMessage(personalNumber, media, { caption: `🚨 *NUEVO PAGO RECIBIDO*\nDel número: ${msg.from}` });
            await msg.reply("¡Comprobante recibido! En unos minutos te confirmo la activación.");
            return;
        }

        const { data: user } = await supabase.from('app_users').select('email').or(`whatsapp.eq.${phone},whatsapp.eq.+${phone},whatsapp.like.%${phone}%`).maybeSingle();
        const userEmail = user ? user.email : `Invitado_${phone}`;

        if (user) {
            await supabase.from('app_users').update({ bot_step: 'hablando_con_ia', last_msg_date: new Date().toISOString() }).eq('email', userEmail);
        }

        console.log(`📩 Mensaje de ${userEmail}: ${msg.body}`);
        await registrarLogWhatsApp(userEmail, msg.body, 'entrante');

        // CHAT CON IA GRATIS (Pollinations)
        const systemPrompt = `Eres el asistente virtual de soporte de CuevanaTV.
        Tu objetivo es ayudar a instalar la app y motivar a pagar el abono mensual de $5000 ARS.
        Responde de forma amable y directa. El usuario dijo: "${msg.body}"`;

        const pollinationsUrl = `https://text.pollinations.ai/${encodeURIComponent(systemPrompt)}`;
        const response = await fetch(pollinationsUrl);

        if (!response.ok) throw new Error("IA Gratis no disponible");

        const aiResponse = await response.text();

        const chatWa = await msg.getChat();
        await chatWa.sendStateTyping();
        const delayMs = Math.max(3000, aiResponse.length * 40);
        await new Promise(resolve => setTimeout(resolve, delayMs));

        await client.sendMessage(msg.from, aiResponse);
        await chatWa.clearState();
        await registrarLogWhatsApp(userEmail, aiResponse, 'saliente');

    } catch (err) {
        await registrarError(err, "WhatsApp Bot: client.on('message')");
    }
});

// =========================================================
// MÓDULO DE AUDITORÍA: VERIFICADOR DE ENLACES CAÍDOS (SENIOR V4 - BATCHED)
// =========================================================
app.get("/api/admin/audit-links", async (req, res) => {
    let completed = false;
    const isFullScan = req.query.full === 'true';
    const limitCount = isFullScan ? 1000 : 40;

    // Timeout global: 35s para escaneo rápido, 120s para escaneo total
    const timeoutMs = isFullScan ? 120000 : 35000;
    const globalTimeout = setTimeout(() => {
        if (!completed) {
            completed = true;
            console.warn("⚠️ Auditoría interrumpida por timeout global.");
            res.status(504).json({ error: "El servidor tardó demasiado. Intenta con menos carga." });
        }
    }, timeoutMs);

    try {
        console.log(`🔍 [AUDIT] Iniciando escaneo por lotes (Limit: ${limitCount})...`);
        // FIX: Especificamos la relación titles!servers_titleId_fkey para evitar ambigüedad en Supabase
        const { data: episodes, error } = await supabase
            .from("servers")
            .select("id, name, playable_url, titles!servers_titleId_fkey(title)")
            .order('id', { ascending: false })
            .limit(limitCount);

        if (error) throw error;
        if (!episodes || episodes.length === 0) {
            clearTimeout(globalTimeout);
            completed = true;
            return res.json([]);
        }

        const results = [];
        const auditTask = async (ep) => {
            const controller = new AbortController();
            const id = setTimeout(() => controller.abort(), 5000); // 5s por link

            try {
                const response = await fetch(ep.playable_url, {
                    method: 'HEAD',
                    signal: controller.signal
                });
                clearTimeout(id);
                return {
                    id: ep.id,
                    serie: ep.titles?.title || "Desconocida",
                    capitulo: ep.name,
                    status: response.ok ? "🟢 ONLINE" : "🔴 CAÍDO",
                    url: ep.playable_url
                };
            } catch (err) {
                clearTimeout(id);
                return {
                    id: ep.id,
                    serie: ep.titles?.title || "Desconocida",
                    capitulo: ep.name,
                    status: "🔴 OFFLINE / TIMEOUT",
                    url: ep.playable_url
                };
            }
        };

        const batchSize = 5;
        for (let i = 0; i < episodes.length; i += batchSize) {
            if (completed) break;
            const currentBatch = episodes.slice(i, i + batchSize);
            const batchResults = await Promise.all(currentBatch.map(auditTask));
            results.push(...batchResults);
            console.log(`[AUDIT] Lote completado: ${results.length}/${episodes.length}`);
        }

        if (!completed) {
            completed = true;
            clearTimeout(globalTimeout);
            console.log(`✅ [AUDIT] Finalizada con ${results.length} resultados.`);
            res.json(results);
        }
    } catch (err) {
        if (!completed) {
            completed = true;
            clearTimeout(globalTimeout);
            console.error("❌ [AUDIT] Fallo crítico:", err.message);
            res.status(500).json({ error: err.message });
        }
    }
});

// NUEVO: REPARADOR DE ENLACES (SENIOR V5 - NATIVE URL API)
app.post("/api/admin/repair-link", async (req, res) => {
    const { episodeId } = req.body;
    try {
        const { data: ep, error: fetchErr } = await supabase
            .from("servers")
            .select("*")
            .eq("id", episodeId)
            .single();

        if (fetchErr || !ep) throw new Error("No se encontró el episodio");

        // FIX SENIOR: "Liberador de Barras" y eliminación de prefijo /Principal/
        let rawUrl = decodeURIComponent(ep.playable_url);
        const urlObj = new URL(rawUrl);

        urlObj.protocol = 'http:';
        urlObj.hostname = 'cuevana-tv-arg.duckdns.org';
        urlObj.port = '';

        // Limpiamos el pathname: eliminamos /Principal/ si existe y reconstruimos
        let cleanPath = urlObj.pathname.replace(/^\/Principal\//i, '/');
        const segments = cleanPath.split('/').filter(s => s.length > 0);
        urlObj.pathname = '/' + segments.map(s => encodeURIComponent(s)).join('/');

        const fixedUrl = urlObj.toString();

        console.log(`🔧 [REPAIR] Individual: ${ep.playable_url} -> ${fixedUrl}`);

        const { error: updateErr } = await supabase
            .from("servers")
            .update({ playable_url: fixedUrl })
            .eq("id", episodeId);

        if (updateErr) throw updateErr;

        res.json({ success: true, fixedUrl });
    } catch (err) {
        console.error("❌ [REPAIR] Error:", err.message);
        res.status(500).json({ error: err.message });
    }
});

// NUEVO: REPARADOR MASIVO DE ENLACES (SENIOR V5 - BATCHED & ERROR PROTECTED)
app.post("/api/admin/repair-all-links", async (req, res) => {
    try {
        console.log("🛠️ [REPAIR-ALL] Iniciando REPARACIÓN MASIVA V5...");
        const { data: episodes, error } = await supabase.from("servers").select("id, playable_url");
        if (error) throw error;

        let fixedCount = 0;
        const total = episodes.length;
        const batchSize = 25;

        for (let i = 0; i < total; i += batchSize) {
            const batch = episodes.slice(i, i + batchSize);

            const batchPromises = batch.map(async (ep) => {
                try {
                    if (!ep.playable_url) return;

                    const fixedUrl = cleanUrl(ep.playable_url);

                    if (fixedUrl && fixedUrl !== ep.playable_url) {
                        const { error: updErr } = await supabase
                            .from("servers")
                            .update({ playable_url: fixedUrl })
                            .eq("id", ep.id);

                        if (!updErr) fixedCount++;
                    }
                } catch (itemError) {
                    console.warn(`⚠️ [REPAIR-ALL] Saltando ID ${ep.id} por error de formato.`);
                    return;
                }
            });

            await Promise.all(batchPromises);
            if (i % 100 === 0) console.log(`🛠️ [REPAIR-ALL] Progreso: ${i}/${total}`);
        }

        console.log(`✅ [REPAIR-ALL] Finalizado. Total reparados: ${fixedCount}`);
        res.json({ success: true, count: fixedCount });
    } catch (err) {
        console.error("❌ [REPAIR-ALL] Fallo crítico:", err.message);
        res.status(500).json({ error: err.message });
    }
});


// =========================================================
// MÓDULO DE INTEGRIDAD: AUDITORÍA, LIMPIEZA Y CONSISTENCIA (SENIOR)
// =========================================================
app.post("/api/admin/integrity-audit", async (req, res) => {
    const SUMMARY = {
        movies_checked: 0,
        episodes_checked: 0,
        deleted_ghosts: 0,
        duplicates_removed: 0,
        urls_fixed: 0
    };

    const BASE_PATHS = ["D:\\pelis", "E:\\Peliculas"];
    const TARGET_DOMAIN = "cuevana-tv-arg.duckdns.org";

    try {
        console.log("🚀 [INTEGRITY] Iniciando Auditoría Maestra...");

        // 1. CARGAR DATOS
        const { data: movies } = await supabase.from("titles").select("id, title, playable_url, type").eq("published", true).neq("type", "series");
        const { data: episodes } = await supabase.from("servers").select("id, name, playable_url, title_id");

        const allRecords = [
            ...(movies || []).map(m => ({ ...m, _table: 'titles' })),
            ...(episodes || []).map(e => ({ ...e, _table: 'servers' }))
        ];

        // Mapas para detección de duplicados
        const uniquePaths = new Map();
        const toDelete = [];
        const toUpdate = [];

        // 2. ANALIZAR CADA REGISTRO
        for (const record of allRecords) {
            if (record._table === 'titles') SUMMARY.movies_checked++;
            else SUMMARY.episodes_checked++;

            try {
                if (!record.playable_url) continue;

                // A) TRADUCCIÓN A RUTA FÍSICA Y LIMPIEZA DE FANTASMAS
                let url;
                try {
                    // Parseamos la URL original
                    url = new URL(record.playable_url);
                } catch (e) {
                    toDelete.push({ id: record.id, table: record._table });
                    SUMMARY.deleted_ghosts++;
                    continue;
                }

                // Limpiamos el pathname: eliminamos /Principal/ y decodificamos para buscar en disco
                let cleanPathname = url.pathname.replace(/^\/Principal\//i, '/');
                let relPath = decodeURIComponent(cleanPathname).replace(/^\//, '');

                let fileExists = false;
                let physicalPath = "";

                for (const base of BASE_PATHS) {
                    // Convertimos slashes web a slashes de sistema (Windows)
                    const fullPath = path.join(base, relPath.replace(/\//g, path.sep));
                    if (fs.existsSync(fullPath)) {
                        fileExists = true;
                        physicalPath = fullPath;
                        break;
                    }
                }

                if (!fileExists) {
                    toDelete.push({ id: record.id, table: record._table });
                    SUMMARY.deleted_ghosts++;
                    continue;
                }

                // B) DETECCIÓN DE DUPLICADOS (Misma ruta física en disco)
                // Usamos minúsculas para evitar duplicados por diferencia de casing en Windows
                const pathKey = physicalPath.toLowerCase();
                if (uniquePaths.has(pathKey)) {
                    toDelete.push({ id: record.id, table: record._table });
                    SUMMARY.duplicates_removed++;
                    continue;
                }
                uniquePaths.set(pathKey, record.id);

                // C) VERIFICACIÓN DE CONSISTENCIA DE URL (Normalización total)
                const finalUrl = cleanUrl(record.playable_url);

                if (record.playable_url !== finalUrl) {
                    toUpdate.push({ id: record.id, table: record._table, url: finalUrl });
                    SUMMARY.urls_fixed++;
                }

            } catch (err) {
                console.error(`⚠️ Error analizando registro ${record.id}:`, err.message);
            }
        }

        // 3. EJECUCIÓN POR LOTES (BATCHING DE 50)
        console.log(`📦 [INTEGRITY] Aplicando cambios: ${toDelete.length} borrados, ${toUpdate.length} actualizaciones...`);

        const batchSize = 50;

        // Procesar Borrados
        for (let i = 0; i < toDelete.length; i += batchSize) {
            const batch = toDelete.slice(i, i + batchSize);
            const titlesIds = batch.filter(b => b.table === 'titles').map(b => b.id);
            const serversIds = batch.filter(b => b.table === 'servers').map(b => b.id);

            if (titlesIds.length > 0) await supabase.from('titles').delete().in('id', titlesIds);
            if (serversIds.length > 0) await supabase.from('servers').delete().in('id', serversIds);
        }

        // Procesar Actualizaciones
        for (let i = 0; i < toUpdate.length; i += batchSize) {
            const batch = toUpdate.slice(i, i + batchSize);
            const promises = batch.map(item =>
                supabase.from(item.table).update({ playable_url: item.url }).eq('id', item.id)
            );
            await Promise.all(promises);
        }

        console.log("✅ [INTEGRITY] Auditoría finalizada con éxito.");
        res.json({ status: "success", summary: SUMMARY });

    } catch (err) {
        console.error("🔥 [INTEGRITY] Fallo crítico:", err.message);
        res.status(500).json({ error: err.message });
    }
});

app.post("/api/admin/restart-system", (req, res) => {
    console.log("🔄 Recibida orden de reinicio desde Admin Panel...");
    res.json({ success: true, message: "Reiniciando servidor. Vuelve a intentar en 5 segundos." });
    setTimeout(() => {
        process.exit(0);
    }, 1500);
});

// =========================================================
// ENDPOINTS DE AUDITORÍA Y COMPATIBILIDAD APK
// =========================================================

/**
 * Endpoint de Debug para inspeccionar el payload exacto enviado a la APK.
 */
app.get("/api/test-data", async (req, res) => {
    try {
        const { data: movies, error } = await supabase
            .from("titles")
            .select("title, playable_url, category")
            .eq("type", "movie")
            .limit(5);

        if (error) throw error;

        const sample = movies.map(m => ({
            original: m.playable_url,
            cleaned: cleanUrl(m.playable_url),
            title: m.title
        }));

        res.json({
            message: "Muestra de datos normalizados para la APK",
            sample: sample
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

/**
 * Endpoint para obtener películas con URLs normalizadas.
 */
app.get("/api/movies", async (req, res) => {
    try {
        const { data, error } = await supabase
            .from("titles")
            .select("*")
            .eq("type", "movie")
            .eq("published", true)
            .order("created_at", { ascending: false });

        if (error) throw error;

        const cleaned = data.map(item => ({
            ...item,
            playable_url: cleanUrl(item.playable_url)
        }));

        res.json(cleaned);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

/**
 * Endpoint para obtener episodios de una serie con URLs normalizadas.
 */
app.get("/api/episodes", async (req, res) => {
    const { titleId } = req.query;
    if (!titleId) return res.status(400).json({ error: "titleId es requerido" });

    try {
        const { data, error } = await supabase
            .from("servers")
            .select("*")
            .eq("title_id", titleId)
            .order("season_number", { ascending: true })
            .order("episode_number", { ascending: true });

        if (error) throw error;

        const cleaned = data.map(ep => ({
            ...ep,
            playable_url: cleanUrl(ep.playable_url)
        }));

        res.json(cleaned);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

/**
 * NUEVO: Generar Copy Viral para Marketing (IA GRATIS - Pollinations)
 */
app.post("/api/admin/generate-viral-copy", async (req, res) => {
    const { title, category, type } = req.body;
    if (!title) return res.status(400).json({ error: "Título requerido" });

    try {
        console.log(`🧠 Generando Copy Viral con IA Gratis para: ${title}`);

        const systemPrompt = `Actúa como experto en Marketing Viral para "CuevanaTV".
        Escribe un CAPTION y un HOOK potente para el contenido: "${title}" (${category}, ${type}).
        Instrucciones:
        1. Estilo Relatable/Pattern Interrupt.
        2. Español Latino.
        3. Responde SOLO en formato JSON: {"hook": "...", "caption": "...", "hashtags": "#CuevanaTV #Viral ..."}.
        4. Incluye siempre que descarguen la APK.
        SIN MARKDOWN, SOLO EL JSON.`;

        const pollinationsUrl = `https://text.pollinations.ai/${encodeURIComponent(systemPrompt)}`;
        const response = await fetch(pollinationsUrl);

        if (!response.ok) throw new Error("IA Gratis no disponible");

        const textResponse = await response.text();

        // Limpieza de posibles tags de markdown si la IA los incluye
        const cleanJson = textResponse.replace(/```json|```/g, '').trim();
        const viralData = JSON.parse(cleanJson);

        res.json({ success: true, data: viralData });
    } catch (err) {
        console.error("❌ Error en Viral Copy IA Gratis:", err.message);
        res.status(500).json({ success: false, error: "Fallo al generar copy viral con IA Gratis" });
    }
});

app.listen(PORT, () => { console.log(`🚀 Servidor CuevanaTV Activo en puerto ${PORT}`); });

client.initialize();

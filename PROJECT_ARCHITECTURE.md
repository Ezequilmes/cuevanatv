# Documentación Técnica: Arquitectura de Túneles y Despliegue (FRP + DuckDNS)

Este documento detalla la infraestructura definitiva de CuevanaTV para la exposición del servidor local al dominio público de forma persistente y automatizada.

---

## 1. Infraestructura VPS Automatizada (`frps`)

Para garantizar una conexión estable, se utiliza un servidor VPS (Linux) que actúa como puente mediante **frps**. El despliegue se realiza de forma totalmente automatizada.

### Script de Despliegue: `deploy_frps.sh`
Ubicado en la VPS, este script realiza las siguientes tareas:
- **Auto-detección de IP:** Obtiene la IP pública de la VPS automáticamente.
- **Gestión de Dependencias:** Instala Docker y Docker Compose si no están presentes.
- **Configuración Dockerizada:** Genera un entorno basado en la imagen oficial `fatedier/frps` con políticas de reinicio automático.
- **Seguridad:** Configura un `auth.token` para validar las conexiones entrantes desde la PC local.

**Comando de ejecución en VPS:**
```bash
# Descarga e inicia el despliegue automático
curl -fsSL https://raw.githubusercontent.com/Ezequilmes/cuevanatv/main/scripts/deploy_frps.sh -o deploy_frps.sh && chmod +x deploy_frps.sh && ./deploy_frps.sh
```

---

## 2. Configuración del Cliente Local (`frpc.toml`)

El cliente local redirige las peticiones desde el dominio de DuckDNS hacia el servidor de Node.js en el puerto **8787**.

### Parámetros Estables
Archivo: `D:\magik\Mi app\Cuevanatv\frpc.toml`

```toml
serverAddr = "IP_DE_TU_VPS" # Se obtiene al finalizar el script de la VPS
serverPort = 7000
auth.token = "TU_TOKEN_SEGURO"

[[proxies]]
name = "cuevana-app"
type = "http"
localIP = "127.0.0.1"
localPort = 8787
customDomains = ["cuevana-tv-arg.duckdns.org"]
```

---

## 3. Flujo de Arranque Local Automatizado

Para evitar errores de sincronización o "túneles vacíos", el sistema utiliza un script maestro de PowerShell que garantiza el orden correcto de encendido.

### Script Maestro: `start_cuevanatv.ps1`
**Secuencia de Arranque:**
1. **Inicio de Backend:** Lanza `sync_api_server.mjs` utilizando PM2 para asegurar que el proceso se mantenga activo.
2. **Verificación de Salud:** Realiza un escaneo de red local hasta confirmar que el puerto **8787** está aceptando conexiones.
3. **Sincronización de Túnel:** Solo cuando el servidor local responde, se inicia `frpc.exe` utilizando la configuración de DuckDNS.

**Cómo iniciar el sistema:**
```powershell
# Ejecutar desde la raíz del proyecto
.\start_cuevanatv.ps1
```

---

## 4. Normalización de Datos (`cleanUrl`)

El backend ha sido refactorizado para ser agnóstico a la infraestructura de red. La función `cleanUrl` en `sync_api_server.mjs` ahora:
- Utiliza **HTTPS** por defecto.
- Fuerza el dominio **cuevana-tv-arg.duckdns.org** en todos los metadatos entregados a la APK.
- Mantiene la compatibilidad con rutas de archivos locales (Windows) y enlaces IPTV externos.

---

> [!NOTE]  
> Esta arquitectura reemplaza por completo el uso de Cloudflare Tunnel y elimina la necesidad de monitorear archivos de log dinámicos (`tunnel_emergency.log`).

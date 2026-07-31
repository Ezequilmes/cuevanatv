# Automatización Total: Despliegue de frps en VPS

Sigue estos pasos para levantar tu servidor de túneles en menos de 1 minuto sin configurar IPs manualmente.

## 1. Comando de una sola línea (Ejecutar en la VPS)

Copia y pega este comando en tu terminal de Linux (Ubuntu/Debian recomendado):

```bash
curl -fsSL https://raw.githubusercontent.com/Ezequilmes/cuevanatv/main/scripts/deploy_frps.sh -o deploy_frps.sh && chmod +x deploy_frps.sh && ./deploy_frps.sh
```

*(Nota: Si aún no has subido el script a GitHub, puedes crear el archivo manualmente con el contenido de abajo).*

## 2. Contenido del Script `deploy_frps.sh`

Este script detecta la IP, instala dependencias, crea los archivos e inicia el servicio:

```bash
#!/bin/bash

# 1. Detectar IP Pública automáticamente
IP_PUBLICA=$(curl -s https://ifconfig.me)
TOKEN_SEGURO="TU_TOKEN_SEGURO_AQUI" # Cambia esto si deseas

echo "=========================================="
echo "   DESPLIEGUE AUTOMATIZADO DE FRPS        "
echo "   IP Detectada: $IP_PUBLICA"
echo "=========================================="

# 2. Instalar Docker y Docker Compose si no existen
if ! command -v docker &> /dev/null; then
    echo "[1/4] Instalando Docker..."
    sudo apt update && sudo apt install -y docker.io docker-compose
fi

# 3. Crear directorio de trabajo
mkdir -p /opt/frp && cd /opt/frp

# 4. Generar frps.toml
echo "[2/4] Generando frps.toml..."
cat <<EOF > frps.toml
bindPort = 7000
vhostHTTPPort = 80
vhostHTTPSPort = 443

# Autenticación
auth.token = "$TOKEN_SEGURO"

# Panel de control
dashboardPort = 7500
dashboardUser = "admin"
dashboardPwd = "admin_password_cambiame"
EOF

# 5. Generar docker-compose.yml
echo "[3/4] Generando docker-compose.yml..."
cat <<EOF > docker-compose.yml
version: '3.8'
services:
  frps:
    image: fatedier/frps:latest
    container_name: frps
    restart: always
    network_mode: host
    volumes:
      - ./frps.toml:/etc/frp/frps.toml
EOF

# 6. Iniciar contenedor
echo "[4/4] Iniciando contenedor Docker..."
sudo docker-compose up -d

echo "=========================================="
echo "   ¡DESPLIEGUE COMPLETADO CON ÉXITO!      "
echo "   IP VPS: $IP_PUBLICA"
echo "   Puerto: 7000"
echo "   Token:  $TOKEN_SEGURO"
echo "=========================================="
```

## 3. Configuración Local (`frpc.toml`)

Copia este bloque en el archivo `frpc.toml` de tu PC local, reemplazando `LA_IP_DE_TU_VPS` por la que imprimió el script arriba:

```toml
serverAddr = "LA_IP_DE_TU_VPS"
serverPort = 7000
auth.token = "TU_TOKEN_SEGURO_AQUI"

[[proxies]]
name = "cuevana-app"
type = "http"
localIP = "127.0.0.1"
localPort = 8787
customDomains = ["cuevana-tv-arg.duckdns.org"]
```

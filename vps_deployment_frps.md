# Despliegue de frps en VPS con Docker

Sigue estas instrucciones para configurar tu servidor de túneles permanente.

## 1. Archivo `frps.toml` (Configuración del Servidor)
Crea este archivo en la VPS (puedes guardarlo en `/opt/frp/frps.toml`):

```toml
bindPort = 7000
vhostHTTPPort = 80
vhostHTTPSPort = 443

# Panel de control opcional (para ver conexiones)
dashboardPort = 7500
dashboardUser = "admin"
dashboardPwd = "CAMBIA_ESTO_POR_ALGO_SEGURO"

# Token de autenticación (debe coincidir con frpc.toml)
auth.token = "TU_TOKEN_SEGURO"
```

## 2. Archivo `docker-compose.yml`
Crea este archivo en la misma carpeta que `frps.toml`:

```yaml
version: '3.8'

services:
  frps:
    image: fatedier/frps:latest
    container_name: frps
    restart: always
    network_mode: host
    volumes:
      - ./frps.toml:/etc/frp/frps.toml
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

## 3. Comandos de Despliegue en la VPS

Ejecuta estos comandos en tu servidor Linux:

```bash
# 1. Crear directorio y navegar a él
mkdir -p /opt/frp && cd /opt/frp

# 2. (Opcional) Instalar Docker y Docker Compose si no están
sudo apt update && sudo apt install -y docker.io docker-compose

# 3. Iniciar el servidor frps en segundo plano
sudo docker-compose up -d

# 4. Verificar que el contenedor esté corriendo
sudo docker ps

# 5. Ver logs para confirmar que los puertos están bindeados
sudo docker logs -f frps
```

---

> [!CAUTION]
> **Seguridad:** Asegúrate de que los puertos **7000, 80, 443 y 7500** estén abiertos en el Firewall de tu VPS (ej: Security Groups en AWS/Oracle Cloud o `ufw` en Ubuntu).

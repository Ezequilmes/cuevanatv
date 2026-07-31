module.exports = {
  apps: [
    {
      name: "CloudflareTunnel",
      script: "D:/magik/Mi app/Cuevanatv/cloudflared.exe",
      args: "tunnel run --protocol http2 --token eyJhIjoiMTQyNGU3NWU0ODYwM2E2YjlkNWEyMjhkMDhiNjIxOWYiLCJ0IjoiMGIwZWZlZmItYzUyNi00ZmQ5LTljOTgtM2IxYmU0YzQwNWVmIiwicyI6IlpEa3hOemhrTlRNdFl6ZzNNaTAwTURJeUxUbGpNVGt0WTJVME1EZzRNbVV3WkRJMyJ9",
      watch: false,
      autorestart: true,
      restart_delay: 5000
    },
    {
      name: "FileBrowser",
      script: "filebrowser.exe",
      args: "-a 127.0.0.1 -p 8085 -r D:\\pelis --noauth",
      cwd: "D:\\pelis",
      watch: false,
      autorestart: true,
      restart_delay: 5000
    },
    {
      name: "CaddyServer",
      script: "caddy.exe",
      args: "run",
      cwd: "D:\\pelis",
      watch: false,
      autorestart: true,
      restart_delay: 5000
    },
    {
      name: "BotMaestro",
      script: "python",
      args: "bot_maestro.py",
      cwd: "D:\\pelis",
      watch: false,
      autorestart: true,
      restart_delay: 10000,
      env: {
        PYTHONIOENCODING: "utf-8"
      }
    },
    {
      name: "SyncAPIServer",
      script: "node",
      args: "sync_api_server.mjs",
      cwd: "D:\\magik\\Mi app\\Cuevanatv\\scripts",
      watch: false,
      autorestart: true,
      restart_delay: 5000
    },
    {
      name: "AdminPanel",
      script: "cmd.exe",
      args: "/c start_admin.bat",
      cwd: "D:\\magik\\Mi app\\Cuevanatv",
      watch: false,
      autorestart: true,
      restart_delay: 5000
    },
    {
      name: "InstagramReelsStudio",
      script: "python",
      args: "app.py",
      cwd: "C:\\Users\\Admin\\Pictures\\cuevana",
      watch: false,
      autorestart: true,
      restart_delay: 5000,
      env: {
        PYTHONIOENCODING: "utf-8"
      }
    }
  ]
};

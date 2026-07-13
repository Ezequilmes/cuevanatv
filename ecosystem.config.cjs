module.exports = {
  apps: [
    {
      name: "FileBrowser",
      script: "filebrowser.exe",
      args: "-a 127.0.0.1 -p 8085 -r D:\\pelis --noauth",
      cwd: "D:\\pelis",
      watch: false,
      autorestart: true,
      restart_delay: 5000,
      exp_backoff_restart_delay: 100
    },
    {
      name: "CaddyServer",
      script: "caddy.exe",
      args: "run",
      cwd: "D:\\pelis",
      watch: false,
      autorestart: true,
      restart_delay: 5000,
      exp_backoff_restart_delay: 100
    },
    {
      name: "BotMaestro",
      script: "python",
      args: "bot_maestro.py",
      cwd: "D:\\pelis",
      watch: false,
      autorestart: true,
      restart_delay: 10000, // Mayor espera para red
      exp_backoff_restart_delay: 100
    },
    {
      name: "SyncAPIServer",
      script: "node",
      args: "sync_api_server.mjs",
      cwd: "D:\\magik\\Mi app\\Cuevanatv\\scripts",
      watch: false,
      autorestart: true,
      restart_delay: 5000,
      exp_backoff_restart_delay: 100
    },
    {
      name: "AdminPanel",
      script: "npm",
      args: "run dev",
      cwd: "D:\\magik\\Mi app\\Cuevanatv\\admin-panel",
      watch: false,
      autorestart: true,
      restart_delay: 5000,
      exp_backoff_restart_delay: 100
    },
    {
      name: "InstagramReelsStudio",
      script: "python",
      args: "app.py",
      cwd: "C:\\Users\\Admin\\Pictures\\cuevana",
      watch: false,
      autorestart: true,
      restart_delay: 5000,
      exp_backoff_restart_delay: 100
    }
  ]
};

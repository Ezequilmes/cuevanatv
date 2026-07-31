# Limpiar consola
Clear-Host
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   INICIANDO SISTEMA CUEVANA TV (frp)     " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Iniciar el servidor Node.js (sync_api_server.mjs)
Write-Host "[1/3] Iniciando servidor Node.js..." -ForegroundColor Yellow
# Usamos pm2 para mantenerlo vivo, o node directo si prefieres
pm2 start scripts/sync_api_server.mjs --name SyncAPIServer

# 2. Esperar de forma inteligente a que el puerto 8787 esté activo
Write-Host "[2/3] Verificando disponibilidad del puerto 8787..." -ForegroundColor Yellow
$timeout = 30
$elapsed = 0
$serverReady = $false

while ($elapsed -lt $timeout) {
    $connection = Test-NetConnection -ComputerName "127.0.0.1" -Port 8787 -InformationLevel Quiet
    if ($connection) {
        $serverReady = $true
        break
    }
    Start-Sleep -Seconds 1
    $elapsed++
}

if (-not $serverReady) {
    Write-Host "[ERROR] El servidor Node.js no respondió en el puerto 8787 a tiempo." -ForegroundColor Red
    exit 1
}

Write-Host "¡Servidor local activo y respondiendo correctamente!" -ForegroundColor Green

# 3. Iniciar el cliente de frp (frpc)
Write-Host "[3/3] Iniciando túnel frp con DuckDNS..." -ForegroundColor Yellow
if (Test-Path "frpc.exe") {
    # Detenemos cualquier instancia previa de frpc
    taskkill /F /IM frpc.exe /T 2>$null
    Start-Process ".\frpc.exe" -ArgumentList "-c frpc.toml" -NoNewWindow
    Write-Host "==========================================" -ForegroundColor Green
    Write-Host "  ¡SISTEMA EN LÍNEA Y EXPUESTO CON ÉXITO!  " -ForegroundColor Green
    Write-Host "  URL: https://cuevana-tv-arg.duckdns.org  " -ForegroundColor Green
    Write-Host "==========================================" -ForegroundColor Green
} else {
    Write-Host "[ERROR] No se encontró el ejecutable 'frpc.exe' en este directorio." -ForegroundColor Red
}

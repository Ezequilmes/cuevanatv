@echo off
title Control Maestro CuevanaTV (con verificaciones)
echo [1/4] Deteniendo procesos existentes...
taskkill /F /IM node.exe /T >nul 2>&1
pm2 stop all >nul 2>&1

echo [2/4] Iniciando aplicaciones...
cd /d "D:\magik\Mi app\Cuevanatv"
pm2 start ecosystem.config.cjs

echo [3/4] Esperando a que el servidor este listo (Puerto 8787)...
powershell -ExecutionPolicy Bypass -File "D:\magik\Mi app\Cuevanatv\scripts\wait_for_port.ps1" -Port 8787 -TimeoutSeconds 60

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] El servidor no arranco correctamente.
    pause
    exit /b 1
)

echo [4/4] Ejecutando Failover para actualizar URLs de tunel...
node final_failover_fix.mjs

echo.
echo [EXITO] Sistema reiniciado y failover completado.
pm2 save
timeout /t 5
exit

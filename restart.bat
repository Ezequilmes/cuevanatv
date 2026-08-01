@echo off
title Control Maestro CuevanaTV
echo Deteniendo procesos existentes...
taskkill /F /IM node.exe /T >nul 2>&1
pm2 stop all >nul 2>&1
echo Iniciando ecosistema silencioso desde configuracion...
cd /d "D:\magik\Mi app\Cuevanatv"
pm2 start ecosystem.config.cjs
echo Guardando configuracion para el arranque...
pm2 save
echo.
echo [EXITO] Las 6 aplicaciones estan corriendo en segundo plano (PM2).
echo Ya puedes cerrar esta ventana. El panel esta en http://localhost:5173
timeout /t 5
exit

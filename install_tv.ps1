Param(
  [Parameter(Mandatory = $true)][string]$TvIp,
  [int]$Port = 5555,
  [switch]$Release
)

function Ensure-Command {
  param([string]$Name)
  $cmd = Get-Command $Name -ErrorAction SilentlyContinue
  return $null -ne $cmd
}

if ($Release) {
  Write-Host "==> Construyendo APK (release firmado)..." -ForegroundColor Cyan
} else {
  Write-Host "==> Construyendo APK (debug)..." -ForegroundColor Cyan
}
if (Test-Path ".\gradlew.bat") {
  if ($Release) {
    & ".\gradlew.bat" assembleRelease
  } else {
    & ".\gradlew.bat" assembleDebug
  }
  if ($LASTEXITCODE -ne 0) { Write-Error "Fallo al construir con gradlew"; exit 1 }
} elseif (Ensure-Command "gradle") {
  if ($Release) {
    gradle assembleRelease
  } else {
    gradle assembleDebug
  }
  if ($LASTEXITCODE -ne 0) { Write-Error "Fallo al construir con gradle"; exit 1 }
} else {
  Write-Error "No se encontró gradlew ni gradle en PATH. Abre el proyecto en Android Studio y ejecuta Build APK(s)."
  exit 1
}

if ($Release) {
  $apk = "app\build\outputs\apk\release\app-release.apk"
} else {
  $apk = "app\build\outputs\apk\debug\app-debug.apk"
}
if (-not (Test-Path $apk)) {
  Write-Error "No se encontró la APK en $apk"
  exit 1
}

if (-not (Ensure-Command "adb")) {
  Write-Error "adb no está disponible en PATH. Instala Platform-Tools o añade adb a PATH."
  exit 1
}

$endpoint = "$TvIp`:$Port"
Write-Host "==> Conectando a $endpoint ..." -ForegroundColor Cyan
adb connect $endpoint | Out-Host

Start-Sleep -Seconds 1
Write-Host "==> Dispositivos ADB:" -ForegroundColor Cyan
adb devices | Out-Host

Write-Host "==> Instalando APK..." -ForegroundColor Cyan
adb install -r "$apk" | Out-Host

Write-Host "==> Lanzando app..." -ForegroundColor Cyan
adb shell monkey -p app.cuevanatv -c android.intent.category.LEANBACK_LAUNCHER 1 | Out-Host

Write-Host "==> Hecho." -ForegroundColor Green

Param(
  [Parameter(Mandatory = $true)][string]$OutDir,
  [switch]$Release
)

function Ensure-Command {
  param([string]$Name)
  $cmd = Get-Command $Name -ErrorAction SilentlyContinue
  return $null -ne $cmd
}

if (-not (Test-Path $OutDir)) {
  try { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null } catch { }
  if (-not (Test-Path $OutDir)) { Write-Error "No se puede crear la carpeta de salida: $OutDir"; exit 1 }
}

$gradlew = ".\gradlew.bat"
$useGradle = $false
if (Test-Path $gradlew) {
  Write-Host "==> Construyendo con gradlew..." -ForegroundColor Cyan
} elseif (Ensure-Command "gradle") {
  Write-Host "==> Construyendo con gradle del sistema..." -ForegroundColor Cyan
  $useGradle = $true
} else {
  Write-Host "==> No hay gradlew ni gradle en PATH." -ForegroundColor Yellow
  Write-Host "Abre Android Studio → Build → Build APK(s) (Debug) o Generate Signed APK (Release)" -ForegroundColor Yellow
  Write-Host "Intentaré recoger el APK generado en outputs/intermediates si existe." -ForegroundColor Yellow
}

if (-not $useGradle -and (Test-Path $gradlew)) {
  if ($Release) { & $gradlew assembleRelease } else { & $gradlew assembleDebug }
  if ($LASTEXITCODE -ne 0) { Write-Error "Fallo al construir con gradlew"; exit 1 }
} elseif ($useGradle) {
  if ($Release) { gradle assembleRelease } else { gradle assembleDebug }
  if ($LASTEXITCODE -ne 0) { Write-Error "Fallo al construir con gradle"; exit 1 }
}

$candidates = @()
if ($Release) {
  $candidates += "app\build\outputs\apk\release"
  $candidates += "app\build\intermediates\apk\release"
} else {
  $candidates += "app\build\outputs\apk\debug"
  $candidates += "app\build\intermediates\apk\debug"
}

$apk = $null
foreach ($dir in $candidates) {
  if (Test-Path $dir) {
    $apk = Get-ChildItem $dir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($apk) { break }
  }
}

if (-not $apk) { Write-Error "No se encontró ninguna APK en: $($candidates -join ', ')"; exit 1 }

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$variant = if ($Release) { "release" } else { "debug" }
$destName = "CuevanaTV_${variant}_$stamp.apk"
$destPath = Join-Path $OutDir $destName

Copy-Item $apk.FullName $destPath -Force
Write-Host "==> APK copiada a: $destPath" -ForegroundColor Green
Write-Host "Copia ese archivo a tu pendrive y luego instálalo en tu TV." -ForegroundColor Green


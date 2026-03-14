Param(
  [Parameter(Mandatory = $true)][string]$OutDir,
  [switch]$UseReleaseKeystore,
  [string]$KeystorePath,
  [string]$KeystorePass,
  [string]$KeyAlias,
  [string]$KeyPass
)

function Find-BuildTools {
  $sdkRoots = @(
    "$env:LOCALAPPDATA\Android\Sdk\build-tools",
    "$env:USERPROFILE\AppData\Local\Android\Sdk\build-tools",
    "$env:ANDROID_HOME\build-tools",
    "$env:ANDROID_SDK_ROOT\build-tools"
  ) | Where-Object { $_ -and (Test-Path $_) }
  foreach ($root in $sdkRoots) {
    $versions = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending
    foreach ($v in $versions) {
      $zipalign = Join-Path $v.FullName "zipalign.exe"
      $apksigner = Join-Path $v.FullName "apksigner.bat"
      if ((Test-Path $zipalign) -and (Test-Path $apksigner)) {
        return @{ ZipAlign = $zipalign; ApkSigner = $apksigner }
      }
    }
  }
  return $null
}

if (-not (Test-Path $OutDir)) {
  try { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null } catch {}
  if (-not (Test-Path $OutDir)) { Write-Error "No se puede crear la carpeta de salida: $OutDir"; exit 1 }
}

$bt = Find-BuildTools
if (-not $bt) {
  Write-Error "No se encontraron build-tools (zipalign/apksigner). Abre Android Studio → SDK Manager → instala Build-Tools."
  exit 1
}

$searchDirs = @(
  "app\build\outputs\apk\release",
  "app\build\outputs\apk\debug",
  "app\build\intermediates\apk\release",
  "app\build\intermediates\apk\debug"
)
$apk = $null
$variant = "debug"
foreach ($d in $searchDirs) {
  if (Test-Path $d) {
    $cand = Get-ChildItem $d -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($cand) {
      $apk = $cand
      $variant = (Split-Path $d -Leaf)
      break
    }
  }
}
if (-not $apk) { Write-Error "No se encontró ninguna APK en: $($searchDirs -join ', ')"; exit 1 }

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$aligned = Join-Path $OutDir ("CuevanaTV_${variant}_aligned_$stamp.apk")
$signed  = Join-Path $OutDir ("CuevanaTV_${variant}_signed_$stamp.apk")

& $bt.ZipAlign -p -v 4 $apk.FullName $aligned
if ($LASTEXITCODE -ne 0) { Write-Error "zipalign falló"; exit 1 }

if ($UseReleaseKeystore) {
  if (-not (Test-Path $KeystorePath)) { Write-Error "Keystore no encontrado: $KeystorePath"; exit 1 }
  if (-not $KeyAlias) { Write-Error "Falta -KeyAlias"; exit 1 }
  if (-not $KeystorePass -or -not $KeyPass) { Write-Error "Faltan contraseñas de keystore/clave"; exit 1 }
  & $bt.ApkSigner sign `
    --ks "$KeystorePath" `
    --ks-pass pass:$KeystorePass `
    --key-pass pass:$KeyPass `
    --ks-key-alias "$KeyAlias" `
    --v1-signing-enabled true `
    --v2-signing-enabled false `
    --out "$signed" "$aligned"
} else {
  $debugKs = Join-Path $env:USERPROFILE ".android\debug.keystore"
  if (-not (Test-Path $debugKs)) {
    Write-Error "No se encontró debug.keystore en $debugKs. Abre Android Studio (compila una vez) para generarlo."
    exit 1
  }
  & $bt.ApkSigner sign `
    --ks "$debugKs" `
    --ks-pass pass:android `
    --key-pass pass:android `
    --ks-key-alias androiddebugkey `
    --v1-signing-enabled true `
    --v2-signing-enabled false `
    --out "$signed" "$aligned"
}

if ($LASTEXITCODE -ne 0) { Write-Error "apksigner falló"; exit 1 }

Write-Host "==> APK alineada: $aligned" -ForegroundColor Cyan
Write-Host "==> APK firmada:  $signed" -ForegroundColor Green
Write-Host "Copia 'APK firmada' a tu pendrive e instálala en tu TV." -ForegroundColor Green

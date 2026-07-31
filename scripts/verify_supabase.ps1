param(
    [string]$Table,
    [string]$Id
)

# Cargar variables de entorno si existe .env
if (Test-Path "../.env") {
    Get-Content "../.env" | ForEach-Object {
        if ($_ -match "^([^#\s][^=]+)=(.+)$") {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            [System.Environment]::SetEnvironmentVariable($name, $value)
        }
    }
}

$supabaseUrl = [System.Environment]::GetEnvironmentVariable("SUPABASE_URL")
$supabaseKey = [System.Environment]::GetEnvironmentVariable("SUPABASE_SERVICE_ROLE_KEY")

if (-not $supabaseUrl -or -not $supabaseKey) {
    Write-Host "Error: SUPABASE_URL o SUPABASE_SERVICE_ROLE_KEY no encontrados en el entorno o .env" -ForegroundColor Red
    exit
}

if (-not $Table -or -not $Id) {
    Write-Host "Uso: .\verify_supabase.ps1 -Table <tabla> -Id <id>" -ForegroundColor Cyan
    exit
}

$url = "$supabaseUrl/rest/v1/$Table?id=eq.$Id&select=playable_url"
$headers = @{
    'apikey' = $supabaseKey
    'Authorization' = "Bearer $supabaseKey"
    'Accept' = 'application/vnd.pgrst.object+json'
}

Write-Host "Consultando $Table para ID $Id..." -ForegroundColor Gray

try {
    $res = Invoke-RestMethod -Uri $url -Headers $headers
    if ($res -and $res.playable_url) {
        Write-Host "Playable URL: $($res.playable_url)" -ForegroundColor Green
    } else {
        Write-Host "No se encontró playable_url o el registro no existe (Respuesta vacía)." -ForegroundColor Yellow
    }
} catch {
    Write-Host "Error en la consulta: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $respBody = $reader.ReadToEnd()
        Write-Host "Detalle: $respBody" -ForegroundColor Red
    }
}

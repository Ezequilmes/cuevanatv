$oldDomain = "cuevana-tv-arg.duckdns.org"
$newDomain = "strikes-crucial-integer-chance.trycloudflare.com"
$apiKey = "sb_publishable_JmQBsLcvMBc-vFn0-lAXIg_3pQLaip7"
$headers = @{
    'apikey' = $apiKey
    'Authorization' = "Bearer $apiKey"
}

Write-Host "Buscando servidores con el dominio viejo..." -ForegroundColor Cyan
$servers = Invoke-RestMethod -Uri "https://hflcacrgwxszejlkcxsh.supabase.co/rest/v1/servers?playable_url=like.*$oldDomain*" -Headers $headers -Method Get

if ($null -eq $servers -or $servers.Count -eq 0) {
    Write-Host "No se encontraron servidores para actualizar." -ForegroundColor Yellow
    exit
}

Write-Host "Se encontraron $($servers.Count) servidores. Actualizando..." -ForegroundColor Cyan

foreach ($server in $servers) {
    $newUrl = $server.playable_url.Replace($oldDomain, $newDomain)
    $id = $server.id

    Write-Host "Actualizando ID: $id" -ForegroundColor Gray
    try {
        $body = @{ playable_url = $newUrl } | ConvertTo-Json
        Invoke-RestMethod -Uri "https://hflcacrgwxszejlkcxsh.supabase.co/rest/v1/servers?id=eq.$id" `
            -Headers $headers `
            -Method Patch `
            -Body $body `
            -ContentType "application/json"
    } catch {
        Write-Host "Error al actualizar ID $id : $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "Proceso completado." -ForegroundColor Green

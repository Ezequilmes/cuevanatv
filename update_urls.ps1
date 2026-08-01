$oldDomain = "strikes-crucial-integer-chance.trycloudflare.com"
$newDomain = "cuevana-tv-arg.duckdns.org"
$apiKey = "sb_publishable_JmQBsLcvMBc-vFn0-lAXIg_3pQLaip7"
$headers = @{
    'apikey' = $apiKey
    'Authorization' = "Bearer $apiKey"
}

Write-Host "Buscando servidores con el dominio viejo ($oldDomain)..." -ForegroundColor Cyan
$servers = Invoke-RestMethod -Uri "https://hflcacrgwxszejlkcxsh.supabase.co/rest/v1/servers?playable_url=like.*$oldDomain*" -Headers $headers -Method Get

if ($null -eq $servers -or $servers.Count -eq 0) {
    Write-Host "No se encontraron servidores para actualizar." -ForegroundColor Yellow
} else {
    Write-Host "Se encontraron $($servers.Count) servidores. Actualizando..." -ForegroundColor Cyan

    foreach ($server in $servers) {
        $newUrl = $server.playable_url.Replace($oldDomain, $newDomain)
        # Forzar http ya que el usuario lo prefiere así por ahora (según su cleanUrl)
        $newUrl = $newUrl.Replace("https://", "http://")
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
}

Write-Host "Proceso de servidores completado." -ForegroundColor Green

Write-Host "Buscando títulos con el dominio viejo..." -ForegroundColor Cyan
$titles = Invoke-RestMethod -Uri "https://hflcacrgwxszejlkcxsh.supabase.co/rest/v1/titles?playable_url=like.*$oldDomain*" -Headers $headers -Method Get

if ($null -eq $titles -or $titles.Count -eq 0) {
    Write-Host "No se encontraron títulos para actualizar." -ForegroundColor Yellow
} else {
    Write-Host "Se encontraron $($titles.Count) títulos. Actualizando..." -ForegroundColor Cyan

    foreach ($title in $titles) {
        $newUrl = $title.playable_url.Replace($oldDomain, $newDomain)
        $newUrl = $newUrl.Replace("https://", "http://")
        $id = $title.id

        Write-Host "Actualizando ID: $id" -ForegroundColor Gray
        try {
            $body = @{ playable_url = $newUrl } | ConvertTo-Json
            Invoke-RestMethod -Uri "https://hflcacrgwxszejlkcxsh.supabase.co/rest/v1/titles?id=eq.$id" `
                -Headers $headers `
                -Method Patch `
                -Body $body `
                -ContentType "application/json"
        } catch {
            Write-Host "Error al actualizar ID $id : $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

Write-Host "Proceso total completado." -ForegroundColor Green

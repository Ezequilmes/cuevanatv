param([int]$Port = 8787, [int]$TimeoutSeconds = 60)
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
while ($stopwatch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
    if (Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue) {
        Write-Host "✅ Port $Port is now listening." -ForegroundColor Green
        return
    }
    Write-Host "⏳ Waiting for port $Port..." -ForegroundColor Gray
    Start-Sleep -Seconds 2
}
Write-Host "❌ Timeout waiting for port $Port." -ForegroundColor Red
exit 1

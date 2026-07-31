$headers = @{
    'apikey' = 'sb_publishable_JmQBsLcvMBc-vFn0-lAXIg_3pQLaip7'
    'Authorization' = 'Bearer sb_publishable_JmQBsLcvMBc-vFn0-lAXIg_3pQLaip7'
    'Content-Type' = 'application/json'
}
$body = @{ playable_url = 'https://resolutions-immune-workers-cottages.trycloudflare.com/test' } | ConvertTo-Json
Invoke-RestMethod -Uri 'https://hflcacrgwxszejlkcxsh.supabase.co/rest/v1/servers?id=eq.e28912a2-d52f-488c-a9ea-227e3c79dbce' -Headers $headers -Method Patch -Body $body

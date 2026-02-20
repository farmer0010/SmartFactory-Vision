$ErrorActionPreference = "Stop"

Write-Host "=== Multi-Process Scale-Out Verification (Running App) ===" -ForegroundColor Cyan

# 4. Trigger 4 Concurrent Requests (cam1, cam2, cam3, cam4)
Write-Host "`nSpawning workers for cam1, cam2, cam3, cam4..." -ForegroundColor Yellow
$img = "C:\Users\김주영\.gemini\antigravity\brain\bb635850-28ce-4eb4-9a05-e4bc1c899cd0\test_object_1770880411139.png" 

# We send requests in parallel using Start-Job
$jobs = @()
foreach ($id in @("cam1", "cam2", "cam3", "cam4")) {
    $jobs += Start-Job -ScriptBlock {
        param($camId, $imgPath)
        $out = curl.exe -s -o NUL -w "%{http_code}" -X POST -F "image=@$imgPath" -F "camId=$camId" "http://localhost:8080/api/stream/frame"
        return "$camId=$out"
    } -ArgumentList $id, $img
}

Write-Host "Waiting for jobs to complete..."
$results = $jobs | Receive-Job -Wait
Write-Host "Job Results: "
$results | ForEach-Object { Write-Host $_ }

# 5. Check Process Count
Start-Sleep -Seconds 2
$pythonProcs = Get-Process "python" -ErrorAction SilentlyContinue
$count = if ($pythonProcs) { @($pythonProcs).Count } else { 0 }

Write-Host "`nActive Python Processes: $count" -ForegroundColor Cyan
if ($count -ge 4) {
    Write-Host "[SUCCESS] Found $count Python processes (Expected >= 4)" -ForegroundColor Green
} else {
    Write-Host "[FAILURE] Found only $count Python processes. Expected 4." -ForegroundColor Red
}

write-host "Done."

# Uploads the fake patients (made by Synthea) into the FHIR server.
# Run from this folder:  .\load-patients.ps1
# Each file is a FHIR "transaction Bundle" = many records sent in one POST.

$fhir = "http://localhost:8090/fhir"
$dir  = Join-Path $PSScriptRoot "fhir-data\fhir"

# 1. Wait until the server answers (it takes 1-2 minutes to start)
Write-Host "Waiting for FHIR server..."
while ($true) {
    try { Invoke-RestMethod "$fhir/metadata" -TimeoutSec 5 | Out-Null; break } catch { Start-Sleep 5 }
}

# 2. Hospitals and doctors first, because patient records point to them
$files  = Get-ChildItem $dir -Filter "*Information*.json"
$files += Get-ChildItem $dir -Filter "*.json" | Where-Object { $_.Name -notlike "*Information*" }

foreach ($f in $files) {
    try {
        Invoke-RestMethod -Method Post -Uri $fhir -ContentType "application/fhir+json" `
            -InFile $f.FullName -TimeoutSec 300 | Out-Null
        Write-Host "OK    $($f.Name)"
    } catch {
        Write-Host "FAIL  $($f.Name): $($_.Exception.Message)"
    }
}

# 3. Count what is in the server now
$count = (Invoke-RestMethod "$fhir/Patient?_summary=count").total
Write-Host "Patients in FHIR server: $count"

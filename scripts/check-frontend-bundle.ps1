$ErrorActionPreference = 'Stop'
$bundle = Join-Path $PSScriptRoot '..\frontend\dist'
if (-not (Test-Path $bundle)) { throw 'Frontend build output is missing. Run npm run build first.' }
$forbidden = 'GROQ_API_KEY|DATABASE_PASSWORD|S3_SECRET_KEY|SESSION_SECRET|ENCRYPTION_MASTER_KEY|ADZUNA_APP_KEY|USAJOBS_API_KEY|SMARTRECRUITERS_API_KEY'
$match = Get-ChildItem -Path $bundle -Recurse -File | Select-String -Pattern $forbidden
if ($match) { $match | ForEach-Object { Write-Error "Server-only variable name found in bundle: $($_.Path)" }; exit 1 }
Write-Output 'PASS: no server-only variable names found in the frontend bundle.'


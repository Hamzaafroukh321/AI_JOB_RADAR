$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$paths = @('backend/src', 'frontend/src', 'frontend/public', '.github', 'scripts') | ForEach-Object { Join-Path $root $_ } | Where-Object { Test-Path $_ }
$patterns = @(
  'gsk_[A-Za-z0-9]{20,}',
  '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----',
  '(?i)(groq|adzuna|usajobs|smartrecruiters)[_-]?(api[_-]?)?key\s*[:=]\s*["''][^"'']+["'']'
)
$matches = foreach ($pattern in $patterns) { Get-ChildItem $paths -Recurse -File | Select-String -Pattern $pattern }
if ($matches) { $matches | ForEach-Object { Write-Error "Potential secret: $($_.Path):$($_.LineNumber)" }; exit 1 }
Write-Output 'PASS: repository source scan found no credential-shaped values.'

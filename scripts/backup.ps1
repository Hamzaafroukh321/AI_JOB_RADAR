param(
  [Parameter(Mandatory = $true)][string]$BackupDirectory,
  [string]$DatabaseUrl = $env:DATABASE_URL,
  [string]$PostgresContainer = "",
  [string]$ObjectStorageAlias = "",
  [string]$ObjectStorageBucket = "",
  [string]$ObjectStorageContainerImage = "",
  [string]$ObjectStorageEndpoint = "http://host.docker.internal:9000",
  [string]$ObjectStorageEnvFile = ""
)
$ErrorActionPreference = "Stop"
if (-not $PostgresContainer -and [string]::IsNullOrWhiteSpace($DatabaseUrl)) { throw "DATABASE_URL is required." }
$resolved = [System.IO.Path]::GetFullPath($BackupDirectory)
New-Item -ItemType Directory -Force -Path $resolved | Out-Null
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$databaseFile = Join-Path $resolved "database-$timestamp.dump"
if ($PostgresContainer) {
  $containerFile = "/tmp/database-$timestamp.dump"
  & docker exec $PostgresContainer sh -c 'pg_dump --format=custom --no-owner --no-acl --file="$1" -U "$POSTGRES_USER" -d "$POSTGRES_DB"' sh $containerFile
  if ($LASTEXITCODE -ne 0) { throw "Containerized pg_dump failed." }
  try {
    & docker cp "${PostgresContainer}:$containerFile" $databaseFile
    if ($LASTEXITCODE -ne 0) { throw "Copying the database backup from the container failed." }
  } finally {
    & docker exec $PostgresContainer rm -f $containerFile | Out-Null
  }
} else {
  & pg_dump --format=custom --no-owner --no-acl --file=$databaseFile $DatabaseUrl
  if ($LASTEXITCODE -ne 0) { throw "pg_dump failed." }
}
$objectPath = $null
if ($ObjectStorageContainerImage -and $ObjectStorageBucket) {
  if (-not (Test-Path -LiteralPath $ObjectStorageEnvFile -PathType Leaf)) {
    throw "ObjectStorageEnvFile is required for containerized object storage backup."
  }
  $objectPath = Join-Path $resolved "objects-$timestamp"
  $containerPath = "/backup/objects-$timestamp"
  & docker run --rm --env-file $ObjectStorageEnvFile -e "S3_BUCKET=$ObjectStorageBucket" -e "S3_ENDPOINT=$ObjectStorageEndpoint" --mount "type=bind,source=$resolved,target=/backup" --entrypoint /bin/sh $ObjectStorageContainerImage -c 'mc alias set backup "$S3_ENDPOINT" "$S3_ACCESS_KEY" "$S3_SECRET_KEY" >/dev/null && mc mirror "backup/$S3_BUCKET" "$1"' sh $containerPath | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Containerized object storage mirror failed." }
} elseif ($ObjectStorageAlias -and $ObjectStorageBucket) {
  $objectPath = Join-Path $resolved "objects-$timestamp"
  & mc mirror "$ObjectStorageAlias/$ObjectStorageBucket" $objectPath | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Object storage mirror failed." }
}
$manifest = [ordered]@{
  createdAtUtc = (Get-Date).ToUniversalTime().ToString("o")
  databaseFile = [System.IO.Path]::GetFileName($databaseFile)
  databaseSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $databaseFile).Hash.ToLowerInvariant()
  objectDirectory = if ($objectPath) { [System.IO.Path]::GetFileName($objectPath) } else { $null }
}
$manifestFile = Join-Path $resolved "manifest-$timestamp.json"
$manifest | ConvertTo-Json | Set-Content -LiteralPath $manifestFile -Encoding UTF8
Write-Output $manifestFile

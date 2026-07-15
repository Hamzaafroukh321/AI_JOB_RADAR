param(
  [Parameter(Mandatory = $true)][string]$ManifestFile,
  [Parameter(Mandatory = $true)][string]$TargetDatabaseUrl,
  [switch]$ConfirmRestore,
  [string]$PostgresContainer = "",
  [string]$ObjectStorageAlias = "",
  [string]$ObjectStorageBucket = "",
  [string]$ObjectStorageContainerImage = "",
  [string]$ObjectStorageEndpoint = "http://host.docker.internal:9000",
  [string]$ObjectStorageEnvFile = ""
)
$ErrorActionPreference = "Stop"
if (-not $ConfirmRestore) { throw "Restore is destructive. Re-run with -ConfirmRestore." }
$manifestPath = [System.IO.Path]::GetFullPath($ManifestFile)
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
$root = Split-Path -Parent $manifestPath
$databaseFile = Join-Path $root $manifest.databaseFile
if (-not (Test-Path -LiteralPath $databaseFile -PathType Leaf)) { throw "Database backup is missing." }
$actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $databaseFile).Hash.ToLowerInvariant()
if ($actual -ne $manifest.databaseSha256) { throw "Database backup checksum mismatch." }
if ($PostgresContainer) {
  $containerFile = "/tmp/$($manifest.databaseFile)"
  & docker cp $databaseFile "${PostgresContainer}:$containerFile"
  if ($LASTEXITCODE -ne 0) { throw "Copying the database backup into the container failed." }
  try {
    & docker exec $PostgresContainer sh -c 'pg_restore --clean --if-exists --no-owner --no-acl -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$1"' sh $containerFile
    if ($LASTEXITCODE -ne 0) { throw "Containerized pg_restore failed." }
  } finally {
    & docker exec $PostgresContainer rm -f $containerFile | Out-Null
  }
} else {
  & pg_restore --clean --if-exists --no-owner --no-acl --dbname=$TargetDatabaseUrl $databaseFile
  if ($LASTEXITCODE -ne 0) { throw "pg_restore failed." }
}
if ($manifest.objectDirectory -and $ObjectStorageContainerImage -and $ObjectStorageBucket) {
  if (-not (Test-Path -LiteralPath $ObjectStorageEnvFile -PathType Leaf)) {
    throw "ObjectStorageEnvFile is required for containerized object storage restore."
  }
  $objects = Join-Path $root $manifest.objectDirectory
  if (-not (Test-Path -LiteralPath $objects -PathType Container)) { throw "Object backup is missing." }
  $containerPath = "/backup/$($manifest.objectDirectory)"
  & docker run --rm --env-file $ObjectStorageEnvFile -e "S3_BUCKET=$ObjectStorageBucket" -e "S3_ENDPOINT=$ObjectStorageEndpoint" --mount "type=bind,source=$root,target=/backup,readonly" --entrypoint /bin/sh $ObjectStorageContainerImage -c 'mc alias set restore "$S3_ENDPOINT" "$S3_ACCESS_KEY" "$S3_SECRET_KEY" >/dev/null && mc mirror --overwrite "$1" "restore/$S3_BUCKET"' sh $containerPath | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Containerized object storage restore failed." }
} elseif ($manifest.objectDirectory -and $ObjectStorageAlias -and $ObjectStorageBucket) {
  $objects = Join-Path $root $manifest.objectDirectory
  if (-not (Test-Path -LiteralPath $objects -PathType Container)) { throw "Object backup is missing." }
  & mc mirror --overwrite $objects "$ObjectStorageAlias/$ObjectStorageBucket" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Object storage restore failed." }
}
Write-Output "Restore completed and checksum was verified."

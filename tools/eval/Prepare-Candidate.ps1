param(
    [Parameter(Mandatory = $true)][string]$CandidateKey
)
$ErrorActionPreference = 'Stop'
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$catalogue = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'candidates.json') -Raw | ConvertFrom-Json
$selected = @($catalogue.candidates | Where-Object { $_.key -eq $CandidateKey })
if ($selected.Count -ne 1) { throw 'Choose a candidate key from candidates.json.' }
$candidate = $selected[0]
if ($candidate.revision -notmatch '^[a-f0-9]{40}$' -or $candidate.sha256 -notmatch '^[a-f0-9]{64}$') { throw 'Invalid pin.' }
$directory = Join-Path $workspace '.gradle/models'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$destination = Join-Path $directory $candidate.file
$temporary = "$destination.partial"
function Test-CandidateFile([string]$Path) {
    if (!(Test-Path -LiteralPath $Path)) { return $false }
    return (Get-Item -LiteralPath $Path).Length -eq $candidate.bytes -and (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -eq $candidate.sha256
}
if (!(Test-CandidateFile $destination)) {
    $url = 'https://huggingface.co/' + $candidate.repository + '/resolve/' + $candidate.revision + '/' + [Uri]::EscapeDataString($candidate.file)
    & curl.exe --fail --location --retry 3 --continue-at - --output $temporary $url
    if ($LASTEXITCODE -ne 0) { throw 'Download failed; the partial file was kept for resuming.' }
    if (!(Test-CandidateFile $temporary)) { throw 'Model checksum or length mismatch; model will not be used.' }
    Move-Item -LiteralPath $temporary -Destination $destination -Force
}
Write-Output "Verified candidate: $CandidateKey"
Write-Output $destination

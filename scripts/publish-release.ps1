# Builds the signed release APK and publishes it for the in-app updater.
# Staff phones read <UPDATE_BASE_URL>version.json, so bump versionCode/versionName in app/build.gradle.kts first.
# Only the release-site folder is uploaded (APK + version.json), never the app source. It lives in its own
# Vercel project (beam-ops-downloads) so changelog deploys can't remove the APK.
# First time: run `npx.cmd vercel login`, then this script (it creates the Vercel project).
# The upload is sent as one archive (--archive=tgz), because Vercel's file-by-file upload kept aborting on the large APK.
param([string]$Notes = '')
$ErrorActionPreference = 'Stop'

# Windows PowerShell 5.1 adds a byte-order mark with -Encoding UTF8, which Vercel and the app's JSON reader reject
function Write-Utf8($path, $text) { [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false))) }
$root = Split-Path -Parent $PSScriptRoot
$gradle = Get-Content (Join-Path $root 'app\build.gradle.kts') -Raw

$versionCode = [int]([regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
$versionName = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$baseUrl = [regex]::Match($gradle, 'UPDATE_BASE_URL",\s*"\\"([^"\\]+)\\"').Groups[1].Value
if (-not $versionCode -or -not $versionName -or -not $baseUrl) { throw 'Could not read the version or UPDATE_BASE_URL from app/build.gradle.kts' }
$token = ([uri]$baseUrl).AbsolutePath.Trim('/')

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
Push-Location $root
try { & .\gradlew.bat :app:assembleRelease } finally { Pop-Location }
$built = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $built)) { throw 'The release APK was not built' }

$site = Join-Path $root 'release-site'
$folder = Join-Path $site $token
New-Item -ItemType Directory -Force $folder | Out-Null
Get-ChildItem $folder -Filter *.apk | Remove-Item
$apkName = "BeamOps-$versionName.apk"
Copy-Item $built (Join-Path $folder $apkName) -Force
$sha = (Get-FileHash (Join-Path $folder $apkName) -Algorithm SHA256).Hash.ToLower()

# What changed, read from CHANGELOG.md: every release after 1.0, newest first, as plain-language lines
function Compare-Version($a, $b) {
    $x = $a.Split('.') | ForEach-Object { [int]$_ }; $y = $b.Split('.') | ForEach-Object { [int]$_ }
    for ($i = 0; $i -lt [Math]::Max($x.Count, $y.Count); $i++) {
        $p = if ($i -lt $x.Count) { $x[$i] } else { 0 }; $q = if ($i -lt $y.Count) { $y[$i] } else { 0 }
        if ($p -ne $q) { return [Math]::Sign($p - $q) }
    }
    return 0
}
$history = @()
$current = $null
foreach ($line in (Get-Content (Join-Path $root 'CHANGELOG.md') -Encoding UTF8)) {
    if ($line -match '^## \[(\d+(?:\.\d+)*)\]') {
        $current = $null
        $v = $Matches[1]
        if ((Compare-Version $v '1.0') -gt 0 -and (Compare-Version $v $versionName) -le 0) {
            $current = [ordered]@{ version = $v; changes = @() }
            $history += $current
        }
    } elseif ($current -and $line -match '^- \*\*(.+?)\*\*\s*(.*?)\s*(\{[^}]*\})?\s*$') {
        $current.changes += [ordered]@{ title = $Matches[1].TrimEnd('.'); text = $Matches[2] }
    }
}
$history = @($history | Where-Object { $_.changes.Count -gt 0 } | Sort-Object { [version]$_.version } -Descending)
if (-not $history) { throw "CHANGELOG.md has no entries for $versionName. Add them before publishing." }

$info = [ordered]@{ versionCode = $versionCode; versionName = $versionName; apk = $apkName; sha256 = $sha; notes = $Notes; history = @($history) }
Write-Utf8 (Join-Path $folder 'version.json') ($info | ConvertTo-Json -Depth 6)
$vercelJson = @'
{ "cleanUrls": true, "headers": [ { "source": "/(.*)", "headers": [ { "key": "Cache-Control", "value": "no-cache" }, { "key": "X-Robots-Tag", "value": "noindex" } ] } ] }
'@
Write-Utf8 (Join-Path $site 'vercel.json') $vercelJson
Write-Utf8 (Join-Path $site 'index.html') '<!doctype html><title>BeamOps</title>'

# The page to send to people: <UPDATE_BASE_URL> itself, with a Download button, install steps and a QR code
$size = '{0:N0} MB' -f ((Get-Item (Join-Path $folder $apkName)).Length / 1MB)
$page = Get-Content (Join-Path $PSScriptRoot 'download-page.html') -Raw -Encoding UTF8
$encode = { param($s) [System.Net.WebUtility]::HtmlEncode($s) }
$list = { param($release) '<ul>' + (($release.changes | ForEach-Object { "<li><b>$(& $encode $_.title).</b> $(& $encode $_.text)</li>" }) -join '') + '</ul>' }
# The newest release is shown in full, earlier ones fold away
$whatsNew = ''
if ($Notes) { $whatsNew += "<p style=`"margin:0 0 8px`">$(& $encode $Notes)</p>" }
$whatsNew += & $list $history[0]
$older = ($history | Select-Object -Skip 1 | ForEach-Object { "<details><summary>Version $($_.version)</summary>$(& $list $_)</details>" }) -join ''
if ($older) { $whatsNew += "<p class=`"small`" style=`"margin:12px 0 4px`">Earlier versions</p>$older" }
$page = $page.Replace('{{APK}}', (& $encode $apkName)).Replace('{{VERSION}}', (& $encode $versionName)).Replace('{{SIZE}}', $size)
$page = $page.Replace('{{DATE}}', (Get-Date -Format 'd MMM yyyy')).Replace('{{WHATS_NEW}}', $whatsNew)
Write-Utf8 (Join-Path $folder 'index.html') $page
Copy-Item (Join-Path $root 'app/src/main/res/drawable-nodpi/beam_logo.png') (Join-Path $folder 'logo.png') -Force

# release-site gets its own Vercel project link; otherwise the CLI finds the repo's .vercel (the changelog project)
# and deploys the whole repo to the changelog site instead
if (-not (Test-Path (Join-Path $site '.vercel\project.json'))) {
    Push-Location $site
    try {
        npx.cmd --yes vercel link --yes --project beam-ops-downloads
        if ($LASTEXITCODE -ne 0) { throw 'Could not link or create the beam-ops-downloads Vercel project.' }
    } finally { Pop-Location }
}

Push-Location $site
try {
    npx.cmd --yes vercel deploy --prod --yes --archive=tgz
    if ($LASTEXITCODE -ne 0) { throw "The Vercel deploy failed, so nothing new was published." }
} finally { Pop-Location }
Write-Host "Published BeamOps $versionName (code $versionCode). Phones will offer it the next time they open the app."
Write-Host "Send people this page: $baseUrl"

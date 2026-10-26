# Builds the signed release APK and publishes it for the in-app updater.
# Staff phones read <UPDATE_BASE_URL>version.json, so bump versionCode/versionName in app/build.gradle.kts first.
# Only the release-site folder is uploaded (APK + version.json), never the app source. It lives in its own
# Vercel project (beam-ops-downloads) so changelog deploys can't remove the APK.
# First time: run `npx.cmd vercel login`, then this script (it creates the Vercel project).
param([string]$Notes = '')
$ErrorActionPreference = 'Stop'
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
$apkName = "BeamOPS-$versionName.apk"
Copy-Item $built (Join-Path $folder $apkName) -Force
$sha = (Get-FileHash (Join-Path $folder $apkName) -Algorithm SHA256).Hash.ToLower()

$info = [ordered]@{ versionCode = $versionCode; versionName = $versionName; apk = $apkName; sha256 = $sha; notes = $Notes }
$info | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $folder 'version.json')
@'
{ "cleanUrls": true, "headers": [ { "source": "/(.*)", "headers": [ { "key": "Cache-Control", "value": "no-cache" }, { "key": "X-Robots-Tag", "value": "noindex" } ] } ] }
'@ | Set-Content -Encoding UTF8 (Join-Path $site 'vercel.json')
'<!doctype html><title>Beam OPS</title>' | Set-Content -Encoding UTF8 (Join-Path $site 'index.html')

Push-Location $site
try { npx.cmd --yes vercel deploy --prod --yes } finally { Pop-Location }
Write-Host "Published Beam OPS $versionName (code $versionCode). Phones will offer it the next time they open the app."

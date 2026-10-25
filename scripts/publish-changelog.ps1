# Publishes CHANGELOG.md to Vercel. Only the changelog-site folder is uploaded, never the app source.
# First time: run `npx vercel login`, then this script (it will link/create the Vercel project).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$site = Join-Path $root 'changelog-site'
Copy-Item (Join-Path $root 'CHANGELOG.md') (Join-Path $site 'CHANGELOG.md') -Force
Push-Location $site
try {
    npx --yes vercel deploy --prod --yes
} finally {
    Pop-Location
}

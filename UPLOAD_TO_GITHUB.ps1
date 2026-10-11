# WiFiWalk source publisher for Windows PowerShell.
# Prerequisite: Git for Windows (https://git-scm.com/download/win).
$ErrorActionPreference = 'Stop'
$repo = 'https://github.com/Jot18/WiFiWalk.git'
$source = Split-Path -Parent $MyInvocation.MyCommand.Path
$work = Join-Path ([System.IO.Path]::GetTempPath()) ('wifiwalk-publish-' + [guid]::NewGuid().ToString('N'))
try {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git for Windows is not installed. Install it from https://git-scm.com/download/win and run this script again.' }
    Write-Host 'Cloning your WiFiWalk GitHub repository...'
    & git clone $repo $work
    if ($LASTEXITCODE -ne 0) { throw 'Git clone failed. Check your GitHub permissions and internet connection.' }
    Write-Host 'Copying app source into the repository...'
    & robocopy $source $work /E /XD '.git' '.gradle' 'build' /XF 'UPLOAD_TO_GITHUB.ps1' | Out-Null
    if ($LASTEXITCODE -gt 7) { throw 'Copying source failed.' }
    & git -C $work add -A
    if ($LASTEXITCODE -ne 0) { throw 'git add failed.' }
    & git -C $work diff --cached --quiet
    if ($LASTEXITCODE -eq 0) { Write-Host 'Files already match GitHub. No commit required.'; return }
    Write-Host 'Creating source commit...'
    & git -C $work -c user.name='WiFiWalk Contributor' -c user.email='wifiwalk@users.noreply.github.com' commit -m 'Add WiFiWalk v0.1 Android source'
    if ($LASTEXITCODE -ne 0) { throw 'Commit failed.' }
    Write-Host 'Uploading source to GitHub... Sign in if prompted.'
    & git -C $work push origin main
    if ($LASTEXITCODE -ne 0) { throw 'Push failed. Make sure you have permission to write to Jot18/WiFiWalk.' }
    Write-Host ''
    Write-Host 'SUCCESS: WiFiWalk source uploaded.' -ForegroundColor Green
    Write-Host 'Build status: https://github.com/Jot18/WiFiWalk/actions'
    Write-Host 'Download WiFiWalk-debug from successful workflow run Artifacts.'
} finally {
    if (Test-Path $work) { Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue }
}

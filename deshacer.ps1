# Reactiva el debloat de base (+ perfil si lo pasas) y el inicio original.
# Uso:  .\deshacer.ps1 -TvHost 192.168.1.50
#       .\deshacer.ps1 -TvHost 192.168.1.50 -Perfil nombre-de-tu-tele
param(
    [Parameter(Mandatory = $true)][string]$TvHost,
    [string]$Perfil = ''
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = $PSScriptRoot
$env:TV_HOST = $TvHost
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'Falta tools\venv' }

$files = @(Join-Path $repo 'perfiles\_base\debloat.txt')
if ($Perfil) {
    $p = Join-Path $repo "perfiles\$Perfil\debloat.txt"
    if (Test-Path $p) { $files += $p }
}
$stock = 'com.google.android.tvlauncher'
if ($Perfil) {
    $pf = Join-Path $repo "perfiles\$Perfil\perfil.txt"
    if (Test-Path $pf) {
        Get-Content $pf | ForEach-Object {
            if ($_ -match '^\s*lanzador_original=(.+)$') { $stock = $Matches[1].Trim() }
        }
    }
}

$pkgs = foreach ($f in $files) {
    Get-Content $f | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ }
}
$cmds = @($pkgs | ForEach-Object { "pm enable $_" })
$cmds += @(
    "pm enable $stock",
    "cmd package set-home-activity --user 0 $stock",
    'settings put global window_animation_scale 1.0',
    'settings put global transition_animation_scale 1.0',
    'settings put global animator_duration_scale 1.0'
)
Write-Host "Reactivando paquetes y el inicio original ($stock)."
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch
Write-Host "Hecho. Pulsa Inicio. FastLauncher puede seguir instalado; para quitarlo:"
Write-Host "  `$env:TV_HOST='$TvHost'; tools\venv\Scripts\python.exe -I tools\tv.py 'pm uninstall com.fastlauncher.tv'"

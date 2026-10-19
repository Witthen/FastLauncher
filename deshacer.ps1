# Vuelve a activar los paquetes de un perfil y restaura el inicio original.
# Uso:  .\deshacer.ps1 -TvHost 192.168.1.50 -Perfil toshiba-emir-shandao
param(
    [Parameter(Mandatory = $true)][string]$TvHost,
    [Parameter(Mandatory = $true)][string]$Perfil
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = $PSScriptRoot
$list = Join-Path $repo "perfiles\$Perfil\debloat.txt"
if (-not (Test-Path $list)) { throw "No existe $list" }
$stock = 'com.google.android.tvlauncher'
$pf = Join-Path $repo "perfiles\$Perfil\perfil.txt"
if (Test-Path $pf) {
    Get-Content $pf | ForEach-Object {
        if ($_ -match '^\s*lanzador_original=(.+)$') { $stock = $Matches[1].Trim() }
    }
}
Write-Host "Esto reactiva las apps del perfil y pone otra vez el inicio de fábrica ($stock)."
& (Join-Path $repo 'tools\restore.ps1') -TvHost $TvHost -Stock $stock $list
Write-Host "Hecho. Pulsa Inicio en el mando. FastLauncher puede seguir instalado; si quieres quitarlo:"
Write-Host "  `$env:TV_HOST='$TvHost'; tools\venv\Scripts\python.exe -I tools\tv.py 'pm uninstall com.fastlauncher.tv'"

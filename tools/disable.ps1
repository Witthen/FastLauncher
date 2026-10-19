# Desactiva paquetes de un debloat.txt (pm disable-user). Nunca desinstala.
# Si el paquete no esta instalado, lo salta.
# Uso: tools\disable.ps1 -TvHost 192.168.1.50 perfiles\_base\debloat.txt
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$List,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$List = (Resolve-Path $List).Path
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'Falta tools\venv. Ejecuta instalar.ps1 o crea el venv.' }
if (-not $TvHost) { throw 'Pasa -TvHost <ip> o $env:TV_HOST' }
$env:TV_HOST = $TvHost

$installed = & $py -I (Join-Path $repo 'tools\tv.py') 'pm list packages'
$pkgs = Get-Content $List | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ }
$cmds = @()
foreach ($p in $pkgs) {
    if ($installed -match [regex]::Escape("package:$p")) {
        $cmds += "pm disable-user --user 0 $p"
    } else {
        Write-Host "  (no esta) $p"
    }
}
if ($cmds.Count -eq 0) {
    Write-Host "Nada que desactivar en $List"
    return
}
$cmds += 'echo debloat-ok'
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch

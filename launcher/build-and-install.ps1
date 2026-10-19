# Uso interno. Para instalar, desde la raíz del repo:
#   .\instalar.ps1 -TvHost 192.168.1.50
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$StockLauncher = 'com.google.android.tvlauncher',
    [string]$Perfil = $env:FASTLAUNCHER_PERFIL
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'Pasa -TvHost <ip> o usa ..\instalar.ps1' }
$root = Split-Path -Parent $PSScriptRoot
$args = @{ TvHost = $TvHost }
if ($Perfil) { $args['Perfil'] = $Perfil }
& (Join-Path $root 'instalar.ps1') @args

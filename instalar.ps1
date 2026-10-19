# FastLauncher: instalacion guiada (Windows).
# Solo Android TV / Google TV. No sirve para Fire TV, Samsung Tizen, LG webOS, Roku ni moviles.
#
#   .\instalar.ps1 -TvHost 192.168.1.50
#   .\instalar.ps1 -TvHost 192.168.1.50 -Perfil toshiba-emir-shandao
#   .\instalar.ps1 -TvHost 192.168.1.50 -Perfil toshiba-emir-shandao -Debloat
param(
    [Parameter(Mandatory = $true)]
    [string]$TvHost,
    [string]$Perfil = '',
    [switch]$Debloat
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
[Console]::OutputEncoding = $OutputEncoding

function Write-Paso($n, $t) { Write-Host ""; Write-Host "=== Paso $n : $t ===" -ForegroundColor Cyan }
function Fail($t) { Write-Host ""; Write-Host "ERROR: $t" -ForegroundColor Red; exit 1 }

$repo = $PSScriptRoot
Set-Location $repo
$env:TV_HOST = $TvHost.Trim()

Write-Host ""
Write-Host "FastLauncher  -  inicio para Android TV / Google TV" -ForegroundColor Green
Write-Host "No es compatible con Fire OS, Tizen (Samsung), webOS (LG), Roku ni telefonos." -ForegroundColor Yellow
Write-Host "IP de la tele: $($env:TV_HOST)"

if ($Perfil) {
    $perfilDir = Join-Path $repo "perfiles\$Perfil"
    if (-not (Test-Path $perfilDir)) { Fail "No existe perfiles\$Perfil. Mira perfiles\README.md" }
    if ($Perfil -eq '_plantilla') { Fail "Copia _plantilla a un nombre nuevo; no instales la plantilla." }
    Write-Host "Perfil: $Perfil"
} else {
    Write-Host "Sin perfil: configuracion generica (YouTube + Play Store en el dock)."
}

Write-Paso 1 "Python"
$pyExe = $null
$pyVenvArgs = @()
if (Get-Command py -ErrorAction SilentlyContinue) { $pyExe = 'py'; $pyVenvArgs = @('-3', '-m', 'venv') }
elseif (Get-Command python -ErrorAction SilentlyContinue) { $pyExe = 'python'; $pyVenvArgs = @('-m', 'venv') }
else { Fail "No esta Python. Instala python.org y marca Add python.exe to PATH. Cierra y abre PowerShell." }

$venvPy = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not (Test-Path $venvPy)) {
    Write-Host "Creando entorno en tools\venv ..."
    & $pyExe @pyVenvArgs (Join-Path $repo 'tools\venv')
    if ($LASTEXITCODE -ne 0) { Fail "No se pudo crear el entorno virtual." }
}
& $venvPy -m pip install -q -r (Join-Path $repo 'tools\requirements.txt')
if ($LASTEXITCODE -ne 0) { Fail "No se pudieron instalar las dependencias (pip)." }
$tvpy = Join-Path $repo 'tools\tv.py'

Write-Paso 2 "Conectar con la tele"
Write-Host "Si en la tele sale Permitir depuracion USB, pulsa ACEPTAR y marca Siempre."
& $venvPy -I $tvpy --check
if ($LASTEXITCODE -eq 2) { Fail "Esta tele no es Android TV / Google TV. No instalo nada." }
if ($LASTEXITCODE -ne 0) { Fail "No hay conexion. Misma Wi-Fi, depuracion activada, IP correcta." }

Write-Paso 3 "Java 17 y Android SDK"
function Find-Jdk17 {
    $homes = @()
    if ($env:JAVA_HOME) { $homes += $env:JAVA_HOME }
    foreach ($root in @('C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft', 'C:\Program Files\Java', 'C:\Program Files\Android\Android Studio')) {
        if (Test-Path $root) {
            $homes += Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match 'jdk-17|jbr' -or $_.FullName -match '\\jbr$' } |
                ForEach-Object {
                    if (Test-Path (Join-Path $_.FullName 'bin\java.exe')) { $_.FullName }
                    elseif (Test-Path (Join-Path $_.FullName 'jbr\bin\java.exe')) { Join-Path $_.FullName 'jbr' }
                }
        }
    }
    $jbrStudio = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path "$jbrStudio\bin\java.exe") { $homes += $jbrStudio }
    foreach ($h in $homes) {
        if (-not (Test-Path "$h\bin\java.exe")) { continue }
        $ver = cmd /c "`"$h\bin\java.exe`" -version 2>&1"
        if ("$ver" -match '17\.') { return $h }
    }
    return $null
}
$jdk = Find-Jdk17
if (-not $jdk) {
    Fail "Hace falta Java 17 (no vale el 8). Instala Temurin 17 (Adoptium) o Android Studio y vuelve a ejecutar este script."
}
$env:JAVA_HOME = $jdk
Write-Host "JAVA_HOME = $jdk"

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path (Join-Path $sdk 'platforms\android-34'))) {
    Fail "No esta el Android SDK (plataforma 34). Abre Android Studio, SDK Manager, y marca Android 14 API 34 y Build-Tools 34."
}
$env:ANDROID_HOME = $sdk
Write-Host "ANDROID_HOME = $sdk"

Write-Paso 4 "Compilar FastLauncher"
$launcher = Join-Path $repo 'launcher'
$mainCfg = Join-Path $launcher 'app\src\main\res\values\config.xml'
$cfgBackup = Join-Path $launcher 'config-perfil.bak.xml'
if ($Perfil) {
    $perfilCfg = Join-Path $repo "perfiles\$Perfil\res\values\config.xml"
    if (-not (Test-Path $perfilCfg)) { Fail "El perfil no tiene res\values\config.xml" }
    Copy-Item $mainCfg $cfgBackup -Force
    Copy-Item $perfilCfg $mainCfg -Force
    Write-Host "Usando config de perfiles\$Perfil"
}
Set-Location $launcher
$sdkEsc = $sdk -replace '\\', '\\'
"sdk.dir=$sdkEsc" | Out-File -Encoding ascii (Join-Path $launcher 'local.properties')
try {
    & .\gradlew.bat assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { Fail "Fallo la compilacion (Gradle)." }
} finally {
    if (Test-Path $cfgBackup) {
        Move-Item $cfgBackup $mainCfg -Force
    }
}
$apk = Join-Path $launcher 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { Fail "No se genero el APK." }
Set-Location $repo

Write-Paso 5 "Instalar en la tele"
Write-Host "Desactivo la verificacion de Play Protect para esta instalacion ADB..."
@(
    'settings put global verifier_verify_adb_installs 0',
    'settings put global package_verifier_enable 0'
) | & $venvPy -I $tvpy --batch | Out-Host

& $venvPy -I $tvpy --push $apk /data/local/tmp/fastlauncher.apk
if ($LASTEXITCODE -ne 0) { Fail "No se pudo copiar el APK (disco de la tele lleno?)." }

$installOut = & $venvPy -I $tvpy 'pm install -r -t --user 0 /data/local/tmp/fastlauncher.apk'
Write-Host $installOut
if ("$installOut" -notmatch 'Success') {
    Fail "pm install fallo. Si dice VERIFICATION, acepta el aviso en la tele o desactiva Play Protect un momento."
}

$stock = 'com.google.android.tvlauncher'
$perfilFile = Join-Path $repo "perfiles\$Perfil\perfil.txt"
if ($Perfil -and (Test-Path $perfilFile)) {
    Get-Content $perfilFile | ForEach-Object {
        if ($_ -match '^\s*lanzador_original=(.+)$') { $script:stock = $Matches[1].Trim() }
    }
}

@(
    'rm -f /data/local/tmp/fastlauncher.apk',
    'pm grant com.fastlauncher.tv android.permission.ACCESS_FINE_LOCATION',
    'appops set com.fastlauncher.tv GET_USAGE_STATS allow',
    'cmd notification allow_listener com.fastlauncher.tv/.SessionListener',
    'cmd package set-home-activity --user 0 com.fastlauncher.tv/.MainActivity',
    "pm disable-user --user 0 $stock"
) | & $venvPy -I $tvpy --batch | Out-Host

if ($Debloat) {
    Write-Paso 6 "Desactivar bloat del perfil (no se borra nada)"
    $list = Join-Path $repo "perfiles\$Perfil\debloat.txt"
    if (-not $Perfil -or -not (Test-Path $list)) { Fail "-Debloat necesita un perfil con debloat.txt" }
    Write-Host "Solo se desactiva. Para volver atras: .\deshacer.ps1 -TvHost $($env:TV_HOST) -Perfil $Perfil"
    & (Join-Path $repo 'tools\disable.ps1') -TvHost $env:TV_HOST -List $list
} else {
    Write-Host ""
    Write-Host "No he tocado apps extra. Si quieres desactivar el bloat de un perfil:"
    Write-Host "  .\instalar.ps1 -TvHost $($env:TV_HOST) -Perfil nombre -Debloat"
}

Write-Host ""
Write-Host "Listo. En el mando pulsa el boton INICIO (casita)." -ForegroundColor Green
Write-Host "Si sigue saliendo el inicio viejo, pulsa INICIO otra vez."

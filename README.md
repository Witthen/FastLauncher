# FastLauncher

Pantalla de inicio **ligera** para **Android TV** y **Google TV**.
Sin filas de anuncios, dock a tu gusto, atajos (liberar RAM, Bluetooth) y poco consumo.
No hace falta root.

---

## Compatibilidad (léelo antes)

| Funciona | No funciona |
|---|---|
| Android TV 8 o más | Samsung (Tizen) |
| Google TV 8 o más | LG (webOS) |
| Toshiba, Xiaomi Mi TV, TCL, Sony, etc. **si en Acerca de pone Android TV o Google TV** | Amazon Fire TV / Fire OS |
| | Roku, Vidaa, móviles, tablets, Windows |

Si la caja solo dice “Smart TV”, **esto no vale**. El instalador se para si el sistema no encaja.

El APK de cada versión está en [Releases](https://github.com/Witthen/FastLauncher/releases). Es genérico: **tú eliges las apps del dock** en la tele (mantener OK sobre una app).

---

## Tres formas de instalarlo

| Camino | Para quién | Qué haces tú |
|---|---|---|
| **A. Con una IA** (Cursor, Claude Code, Codex, etc.) | No quieres pelearte con comandos | Preparas la tele y pegas un texto |
| **B. Un solo script** | Windows, ya tienes Python + Java 17 + SDK | Una orden en PowerShell |
| **C. A mano** | Quieres ver cada paso | Copias cada comando |

Los tres acaban igual: FastLauncher como botón Inicio. Primero hay que **preparar la tele** (abajo). El resto elige A, B o C.

---

## 0. Preparar la tele (siempre, con el mando)

La IA y el PC **no pueden** pulsar esto por ti.

1. Abre **Ajustes**.
2. **Preferencias del dispositivo** (a veces **Sistema**).
3. **Acerca de**.
4. Baja a **Versión**, **Build** o **Android TV OS build** y pulsa **OK siete veces**.
   Debe salir “Ya eres desarrollador”.
5. Atrás → **Opciones para desarrolladores**.
6. Activa **Depuración USB**.
7. Si ves **Depuración de red**, **ADB** o **Depuración inalámbrica**, actívala.
8. Anota la **IP**: Ajustes → **Red** / Wi‑Fi → tu red → **Dirección IP** (ejemplo `192.168.1.50`).
9. PC y tele en la **misma Wi‑Fi**. Tele encendida.

Cuando el PC se conecte, la tele pregunta **¿Permitir depuración USB?**
**Aceptar** y marca **Siempre permitir desde este equipo**.

---

## A. Automático con Cursor, Claude Code u otra IA

Sirve cualquier asistente **con terminal** en tu PC (Cursor Agent, Claude Code, Codex, Aider, etc.).

1. Instala en el PC, si no los tienes: [Python 3](https://www.python.org/downloads/) (marca **Add python.exe to PATH**), [Java 17 Temurin](https://adoptium.net/temurin/releases/?version=17) y [Android Studio](https://developer.android.com/studio) (SDK Manager → API 34 + Build-Tools 34).
2. Abre **esta carpeta del repo** en el editor (en Cursor: File → Open Folder).
3. Abre [`PARA_IA.md`](PARA_IA.md), copia el bloque de abajo del todo.
4. Pon tu **marca/modelo** y la **IP** donde pone `< >`.
5. Pégalo en el chat del agente y deja que trabaje.
6. Cuando te avise, **acepta la depuración en la tele**.
7. Responde el **dock** (qué apps quieres al frente). El debloat de base se aplica solo; YouTube y Chromecast no se tocan.

La IA debe usar este repo (`instalar.ps1`, `tools/tv.py`, `perfiles/`), no inventar otro launcher.
Si `--check` dice INCOMPATIBLE, que se detenga.

---

## B. Un script en Windows

Necesitas lo del punto 1 del camino A (Python, Java 17, SDK) y el paso 0 hecho.

PowerShell **dentro de esta carpeta** (la que tiene `instalar.ps1`):

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

Cambia `192.168.1.50` por la IP de **tu** tele:

```powershell
.\instalar.ps1 -TvHost 192.168.1.50
```

Por defecto **optimiza la tele**: desactiva anuncios, el inicio de Google, el asistente de voz y precargas que no son YouTube ni Play Store. **No borra nada** (se puede deshacer). No toca Chromecast, HDMI, Play Store ni las apps que pongas en el dock.

El dock de fábrica es YouTube y Play Store si están instalados. **Tú eliges el resto en la tele**: mantén **OK** sobre una app → Poner en el dock / quitar / mover.

Si detecta un fabricante conocido, añade solo extras de ese chipset (telemetría), no una lista de apps tuyas.

```powershell
.\instalar.ps1 -TvHost 192.168.1.50 -SinDebloat
.\instalar.ps1 -TvHost 192.168.1.50 -Perfil nombre-de-tu-tele
```

`-SinDebloat` deja las apps de Google como están (salvo el inicio, que pasa a FastLauncher). Al terminar, botón **Inicio** (casita).

---

## C. Manual, comando a comando

Misma carpeta del repo. En Windows usa PowerShell. Cambia `192.168.1.50` por tu IP.

### C1. Programas en el PC

- Python 3 en PATH.
- Java **17** (`java -version` debe decir 17).
- Android SDK con plataforma **34**. Ruta típica: `%LOCALAPPDATA%\Android\Sdk`.

### C2. Python del proyecto

```powershell
python -m venv tools\venv
tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
$env:TV_HOST = "192.168.1.50"
```

### C3. Conectar y comprobar el sistema

Acepta el aviso en la tele, luego:

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py --check
```

Tiene que salir `OK: Android TV / Google TV`. Si dice INCOMPATIBLE, **para aquí**.

### C4. (Opcional) Perfil de tu modelo

Sin perfil, el dock de fábrica es YouTube + Play Store. Las otras dos plazas las rellenas tú en la tele (mantener OK).

Para un modelo concreto: copia `perfiles\_plantilla` → `perfiles\nombre-de-tu-tele` y edita el `config.xml` (qué apps quieres de salida en el dock, y el paquete de Live TV si lo sabes). El usuario puede cambiar el dock después sin recompilar.

### C5. Compilar

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
Set-Location launcher
"sdk.dir=$($env:ANDROID_HOME -replace '\\','\\')" | Out-File -Encoding ascii local.properties
.\gradlew.bat assembleDebug --console=plain
Set-Location ..
```

El APK queda en `launcher\app\build\outputs\apk\debug\app-debug.apk`.
Ajusta `JAVA_HOME` si tu Java 17 está en otra carpeta.

### C6. Instalar en la tele

```powershell
$py = "tools\venv\Scripts\python.exe"
$env:TV_HOST = "192.168.1.50"

# Play Protect no debe bloquear el APK
@"
settings put global verifier_verify_adb_installs 0
settings put global package_verifier_enable 0
"@ | & $py -I tools\tv.py --batch

& $py -I tools\tv.py --push launcher\app\build\outputs\apk\debug\app-debug.apk /data/local/tmp/fastlauncher.apk
& $py -I tools\tv.py "pm install -r -t --user 0 /data/local/tmp/fastlauncher.apk"
```

Tiene que decir `Success`. Luego, **una línea por comando** (o el `--batch` de abajo):

```powershell
@"
rm -f /data/local/tmp/fastlauncher.apk
pm grant com.fastlauncher.tv android.permission.ACCESS_FINE_LOCATION
appops set com.fastlauncher.tv GET_USAGE_STATS allow
cmd notification allow_listener com.fastlauncher.tv/.SessionListener
cmd package set-home-activity --user 0 com.fastlauncher.tv/.MainActivity
pm disable-user --user 0 com.google.android.tvlauncher
"@ | & $py -I tools\tv.py --batch
```

Si tu inicio original no es `com.google.android.tvlauncher`, cambia esa última línea (está en `perfiles\...\perfil.txt` como `lanzador_original`).

### C7. Debloat (el script lo hace solo; a mano:)

```powershell
.\tools\disable.ps1 -TvHost 192.168.1.50 perfiles\_base\debloat.txt
.\tools\disable.ps1 -TvHost 192.168.1.50 perfiles\nombre-de-tu-tele\debloat.txt
```

### C8. Verlo

Pulsa **Inicio** en el mando.

---

## Uso del mando

| Qué pulsas | Qué pasa |
|---|---|
| Arriba / Abajo | Dock ↔ lista de apps ↔ botones de arriba |
| Mantener **OK** sobre una app | **Elegir dock**, ocultar, información |
| **Atajos** | Liberar RAM, Bluetooth, sonido, reposo |
| Mantener **Ajustes** | Mostrar apps ocultas |

---

## Otra tele

1. Copia `perfiles\_plantilla` a `perfiles\nombre-de-tu-tele`.
2. Edita `res\values\config.xml`.
3. Camino A, B o C con `-Perfil nombre-de-tu-tele`.

Listar paquetes:

```powershell
$env:TV_HOST = "192.168.1.50"
tools\venv\Scripts\python.exe -I tools\tv.py "pm list packages"
```

Más detalle: [`perfiles/README.md`](perfiles/README.md). Prompt largo para la IA: [`PARA_IA.md`](PARA_IA.md).

---

## Volver al inicio de fábrica

```powershell
.\deshacer.ps1 -TvHost 192.168.1.50
.\deshacer.ps1 -TvHost 192.168.1.50 -Perfil nombre-de-tu-tele
```

Reactiva lo desactivado y el inicio original. No formatea la tele.

---

## Si algo falla

| Qué ves | Qué hacer |
|---|---|
| No se pudo conectar | Misma Wi‑Fi, IP bien, depuración ON, **Aceptar** en la tele |
| INCOMPATIBLE | No es Android TV. No sigas |
| Falta Java 17 | Temurin 17; cierra y abre el terminal |
| Falta SDK 34 | Android Studio → SDK Manager → API 34 |
| INSTALL_FAILED_VERIFICATION | Acepta el aviso o desactiva Play Protect un momento |
| Sigue el inicio viejo | Pulsa Inicio otra vez |

---

## Licencia

MIT. Tipografía Outfit: SIL Open Font License 1.1 (`launcher/FONT-LICENSE-OFL.txt`).

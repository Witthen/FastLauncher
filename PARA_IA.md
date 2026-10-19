# Pegar esto en Cursor, Claude Code u otra IA con terminal

Antes, **tú** en la tele (la IA no puede pulsar el mando):

1. Ajustes → Preferencias del dispositivo / Sistema → Acerca de.
2. Pulsa **OK siete veces** sobre Versión / Build.
3. Opciones para desarrolladores → **Depuración USB** (y ADB de red si aparece).
4. Anota la **IP** (Ajustes → Red → tu Wi‑Fi → Dirección IP).
5. PC y tele en la **misma Wi‑Fi**. Ten esta carpeta del repo abierta en el editor.

Cuando la IA se conecte, en la tele saldrá **¿Permitir depuración USB?** → Aceptar y marcar Siempre.

Rellena las líneas entre `< >` y pega el bloque siguiente.

---

Trabaja sobre el repo **FastLauncher** que ya tengo abierto (no lo reescribas desde cero). Usa `tools/tv.py` y `instalar.ps1` / `perfiles/`.

**Tele:** `<MARCA Y MODELO>`
**IP:** `<192.168.x.x>`
**Sistema esperado:** Android TV o Google TV. Si `tools/tv.py --check` dice INCOMPATIBLE (Fire OS, Tizen, webOS, móvil), **para** y explícamelo. No instales nada.

Hazlo en el dispositivo real, paso a paso. Pregúntame antes de desactivar apps. No hagas root ni exploits.

### Conectar
- `TV_HOST=<IP>` y `tools/venv` con `tools/requirements.txt`. Si no hay `adb` de sistema, usa `tools/tv.py` (Python `adb-shell`).
- Avísame **antes** de conectar para que acepte el aviso en la tele.
- Pasa `--check`. Solo sigue si es Android TV / Google TV (leanback).

### Copia de seguridad
- Guarda lista de paquetes, `getprop` y settings en `backup/` (está en `.gitignore`). No subas seriales a git.

### Perfil e inicio
- Aplica **siempre** `perfiles/_base/debloat.txt` (anuncios, inicio de Google, asistente de voz, precargas). No preguntes para eso: es el modo optimizar. Salvo que yo diga `-SinDebloat`.
- No asumas mis apps. Pregunta el **dock** (máx. 4; luego lo puedo cambiar en la tele) y Live TV/HDMI.
- No desactives: Play Store, Play Services, Play Games, YouTube, Chromecast, teclado, Bluetooth, mando, sintonizador HDMI/Live TV, ni las apps que yo nombre para el dock.
- Si pido **sistema / inicio gaming** o **modo gaming**, usa `instalar.ps1 ... -ModoGaming` (o `gaming_mode_default=1` en el perfil). No inventes otro launcher ni un OS aparte.
- Si el hardware coincide con un perfil en `perfiles/` (chipset), aplica también **ese** `debloat.txt` (solo fabricante). Si no hay perfil, copia `_plantilla` solo para Live TV / extras de marca, no para inventar un dock.
- Instala FastLauncher con `instalar.ps1` (ya incluye el debloat de base).
- Compila e instala FastLauncher (`com.fastlauncher.tv`) como HOME. No instales launchers de pago.
- Desactiva el inicio original con `pm disable-user --user 0`, **nunca** `pm uninstall` de paquetes de sistema.
- Un `pm` por invocación de shell (o `--batch` una línea = un comando).
- Antes de instalar: `settings put global verifier_verify_adb_installs 0`. Sube el APK con `tv.py --push`, no con chunks raros si el push funciona.
- Permisos: localización, `appops GET_USAGE_STATS`, listener de notificaciones.

### Comprobar
- Tras instalar, `input keyevent KEYCODE_HOME`, captura (`screencap`) y **mira** la foto. Si algo se ve mal, arréglalo.
- Al terminar, dime cómo deshacer: `.\deshacer.ps1 -TvHost IP -Perfil ...`

No toques: sintonizador HDMI/Live TV, Play Store, Play Services, Chromecast (`mediashell`), teclado, Bluetooth, firmware si digo que lo quiero.

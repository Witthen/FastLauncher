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
- Si ya hay carpeta en `perfiles/` para este modelo, úsala (`instalar.ps1 -Perfil ...`).
- Si no, copia `perfiles/_plantilla` a `perfiles/<slug-del-modelo>` y pregunta:
  - qué 4 apps van al dock y en qué orden
  - si quiero Live TV / HDMI
  - si quiero desactivar bloat (`-Debloat`) y cuáles no tocar (YouTube, Play Store, Chromecast, sintonizador, teclado, Bluetooth, mando)
- Compila e instala FastLauncher (`com.fastlauncher.tv`) como HOME. No instales launchers de pago.
- Desactiva el inicio original con `pm disable-user --user 0`, **nunca** `pm uninstall` de paquetes de sistema.
- Un `pm` por invocación de shell (o `--batch` una línea = un comando).
- Antes de instalar: `settings put global verifier_verify_adb_installs 0`. Sube el APK con `tv.py --push`, no con chunks raros si el push funciona.
- Permisos: localización, `appops GET_USAGE_STATS`, listener de notificaciones.

### Comprobar
- Tras instalar, `input keyevent KEYCODE_HOME`, captura (`screencap`) y **mira** la foto. Si algo se ve mal, arréglalo.
- Al terminar, dime cómo deshacer: `.\deshacer.ps1 -TvHost IP -Perfil ...`

No toques: sintonizador HDMI/Live TV, Play Store, Play Services, Chromecast (`mediashell`), teclado, Bluetooth, firmware si digo que lo quiero.

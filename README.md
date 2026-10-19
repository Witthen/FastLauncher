# FastLauncher

Pantalla de inicio **ligera** para televisores **Android TV** y **Google TV**.
Sin filas de anuncios, con un dock que eliges tú, atajos (liberar RAM, Bluetooth…) y poco consumo.

No hace falta root. Se instala por Wi‑Fi desde un PC Windows.

---

## Compatibilidad (léelo antes)

| Funciona | No funciona |
|---|---|
| Android TV 8 o superior | Samsung (Tizen) |
| Google TV 8 o superior | LG (webOS) |
| Teles Toshiba, Xiaomi Mi TV, TCL, Sony, etc. **si dicen Android TV o Google TV** en Ajustes → Acerca de | Amazon Fire TV / Fire OS |
| | Roku, webOS, Vidaa, Firefox OS |
| | Móviles, tablets, Windows, macOS |

Si en la caja pone solo “Smart TV” y no “Android TV” ni “Google TV”, **esto no vale**.
El instalador comprueba el sistema y se para si no encaja.

---

## Qué vas a necesitar

1. La **tele** y el **PC en la misma Wi‑Fi**.
2. **Python 3** — [python.org/downloads](https://www.python.org/downloads/). En el instalador marca **Add python.exe to PATH**.
3. **Java 17** — [Adoptium Temurin 17](https://adoptium.net/temurin/releases/?version=17) (el 8 no sirve).
4. **Android Studio** (gratis) — para el SDK. En SDK Manager marca **Android 14 (API 34)** y **Build-Tools 34**.

No tienes que saber programar. El script `instalar.ps1` hace el resto.

---

## Preparar la tele (5 minutos)

Haz esto **con el mando**, en la tele:

1. Abre **Ajustes**.
2. Entra en **Preferencias del dispositivo** (a veces se llama **Sistema**).
3. Entra en **Acerca de**.
4. Baja hasta **Versión**, **Build** o **Android TV OS build** y pulsa **OK siete veces**.
   Aparece: “Ya eres desarrollador”.
5. Vuelve atrás. Entra en **Opciones para desarrolladores**.
6. Activa **Depuración USB**.
7. Si ves **Depuración de red**, **ADB** o **Depuración inalámbrica**, actívala también.
8. Anota la **IP** de la tele:
   - Ajustes → **Red** (o Wi‑Fi) → tu red → **Estado** / **Dirección IP**.
   - Ejemplo: `192.168.1.26`.

Cuando el PC se conecte, la tele preguntará **«¿Permitir depuración USB?»**.
Pulsa **Aceptar** y marca **Siempre permitir desde este equipo**.

---

## Instalar FastLauncher

En el PC, abre **PowerShell** en la carpeta de este proyecto (la que tiene `instalar.ps1`).

**Cualquier Android TV / Google TV** (configuración genérica):

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\instalar.ps1 -TvHost 192.168.1.26
```

Cambia `192.168.1.26` por **tu** IP.

**Toshiba Vestel (shandao)**, con dock YouTube / Play Store / Crunchyroll / Ibérica:

```powershell
.\instalar.ps1 -TvHost 192.168.1.26 -Perfil toshiba-emir-shandao
```

Para **desactivar** además el bloat de ese perfil (no se borra nada, se puede deshacer):

```powershell
.\instalar.ps1 -TvHost 192.168.1.26 -Perfil toshiba-emir-shandao -Debloat
```

Cuando termine, pulsa el botón **Inicio** (casita) del mando.

El script:

1. Instala lo que falte de Python en una carpeta local (`tools\venv`).
2. Comprueba que la tele es Android TV.
3. Compila FastLauncher.
4. Lo instala y lo pone como pantalla de inicio.

---

## Uso del mando

| Qué pulsas | Qué pasa |
|---|---|
| Arriba / Abajo | Dock ↔ lista de apps ↔ botones de arriba |
| Mantener **OK** sobre una app | Ponerla en el dock, ocultarla, ver información |
| **Atajos** | Liberar RAM, Bluetooth, sonido, reposo |
| Mantener **Ajustes** | Mostrar otra vez las apps ocultas |

---

## Otra tele / tu propio perfil

La configuración **no está atada a Toshiba**. Cada modelo es una carpeta en `perfiles/`.

1. Copia `perfiles\_plantilla` a `perfiles\nombre-de-tu-tele`.
2. Edita `res\values\config.xml` (dock y, si lo sabes, el paquete de Live TV).
3. Opcional: llena `debloat.txt` (una app por línea a desactivar).
4. Instala: `.\instalar.ps1 -TvHost IP -Perfil nombre-de-tu-tele`

Cómo listar apps de la tele:

```powershell
$env:TV_HOST = "192.168.1.26"
tools\venv\Scripts\python.exe -I tools\tv.py "pm list packages"
```

Instrucciones largas: [`perfiles/README.md`](perfiles/README.md).

---

## Volver al inicio de fábrica

```powershell
.\deshacer.ps1 -TvHost 192.168.1.26 -Perfil toshiba-emir-shandao
```

Eso **reactiva** las apps del perfil. No formatea la tele.

---

## Si algo falla

| Mensaje / síntoma | Qué hacer |
|---|---|
| No se pudo conectar | Misma Wi‑Fi, IP bien escrita, depuración ON, **Aceptar** el aviso en la tele |
| INCOMPATIBLE | Esa tele no es Android TV. FastLauncher no se puede poner |
| Falta Java 17 | Instala Temurin 17 y cierra/abre PowerShell |
| Falta SDK 34 | Android Studio → SDK Manager → API 34 |
| INSTALL_FAILED_VERIFICATION | En la tele acepta el escaneo, o desactiva Play Protect un momento |
| Sigue el inicio viejo | Pulsa Inicio otra vez. El script ya deja FastLauncher como predeterminado |

---

## Licencia

MIT. Tipografía Outfit: SIL Open Font License 1.1 (`launcher/FONT-LICENSE-OFL.txt`).

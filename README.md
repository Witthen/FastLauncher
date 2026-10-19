# FastLauncher

Inicio ligero para Android TV. Pensado para teles con poca RAM: sin filas de anuncios, sin
recomendaciones y con un dock que controlas tú.

- Reloj, tiempo y memoria/disco en la parte de arriba
- Cuatro apps fijas abajo; el resto en una rejilla
- Entradas HDMI y Live TV en un menú
- Atajos: liberar RAM, Bluetooth, sonido, reposo
- Las apps que dejas en segundo plano se cierran solas al rato

No hace falta root. Se instala por Wi‑Fi con ADB.

## Requisitos

- Televisor Android TV o Google TV (Android 8 o superior)
- El PC y la tele en la misma red
- Depuración USB / ADB por red activada en el televisor
- JDK 17 y Android SDK (plataforma 34)

## Instalación (Windows)

1. En la tele: **Ajustes → Preferencias del dispositivo → Acerca de** y pulsa **Versión** siete
   veces. Activa **Depuración USB** (y ADB por red si aparece).
2. Anota la IP de la tele.
3. En PowerShell, dentro de esta carpeta:

```powershell
python -m venv tools\venv
tools\venv\Scripts\pip install -r tools\requirements.txt
$env:TV_HOST = "192.168.1.26"
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost $env:TV_HOST
```

Pulsa **Inicio** en el mando. FastLauncher queda como pantalla de inicio.

## Uso

| Mando | Acción |
|---|---|
| Arriba / Abajo | Dock ↔ rejilla ↔ barra superior |
| Mantener OK sobre una app | Dock, ocultar, ficha de la app |
| **Atajos** | Liberar RAM y ajustes rápidos |
| Mantener **Ajustes** | Volver a mostrar apps ocultas |

El dock y las apps ocultas se guardan en el televisor.

## Toshiba (Vestel / shandao)

Lista de paquetes que se pueden desactivar en
[`examples/toshiba-emir-shandao/debloat.txt`](examples/toshiba-emir-shandao/debloat.txt).
No desinstala nada del sistema: solo `pm disable-user`. Para deshacerlo, `tools/restore.ps1`.

No toques el sintonizador (`com.mediatek.wwtv.tvcenter`), Play Store ni Play Services.

## Licencia

MIT. Tipografía Outfit: SIL Open Font License 1.1 (`launcher/FONT-LICENSE-OFL.txt`).

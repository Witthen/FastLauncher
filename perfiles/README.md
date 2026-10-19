# Perfiles por televisor

`_base/debloat.txt` se aplica a **todas** las teles (anuncios y extras de Google).
Cada otra carpeta es un modelo: extras de **fabricante**, no una lista de apps de usuario.

El instalador lee:

| Archivo | Para qué sirve |
|---|---|
| `perfil.txt` | Nombre y paquete del inicio original |
| `res/values/config.xml` | Dock, Live TV, apps ocultas (el instalador lo copia al compilar) |
| `debloat.txt` | Paquetes a desactivar (opcional, `-Debloat`) |

## Añadir tu tele

1. Copia `_plantilla` a una carpeta nueva, por ejemplo `perfiles/mi-xiaomi`.
2. En la tele, anota la IP y activa la depuración (ver el README principal).
3. Lista paquetes:

```powershell
$env:TV_HOST = "192.168.1.xx"
tools\venv\Scripts\python.exe -I tools\tv.py "pm list packages"
```

4. En `config.xml` pon **las apps que quiera el usuario** en el dock (pregúntale; no copies las de otro). Live TV solo si hay sintonizador. `debloat.txt` es opcional.
5. Instala:

```powershell
.\instalar.ps1 -TvHost 192.168.1.xx -Perfil mi-xiaomi
```

Solo Android TV / Google TV. No uses esto en Fire TV, Tizen, webOS ni móviles.

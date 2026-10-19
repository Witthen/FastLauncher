# Perfiles por televisor

Cada carpeta es un modelo de **Android TV / Google TV**. El instalador lee:

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

4. Rellena `config.xml` (dock y Live TV) y, si quieres, `debloat.txt`.
5. Instala:

```powershell
.\instalar.ps1 -TvHost 192.168.1.xx -Perfil mi-xiaomi
```

Solo Android TV / Google TV. No uses esto en Fire TV, Tizen, webOS ni móviles.

#!/usr/bin/env python3
"""Cliente ADB por Wi-Fi para un Android TV (no hace falta el programa adb).

Variable de entorno TV_HOST = IP de la tele. Opcional: TV_PORT (por defecto 5555).

    tv.py --probe     modelo y versión
    tv.py --check     comprueba que es Android TV / Google TV (sale 2 si no)
    tv.py comando     un comando de shell
    tv.py --batch     un comando por línea (stdin)
    tv.py --push local remoto
    tv.py --pull remoto local
"""
import os
import sys

from adb_shell.adb_device import AdbDeviceTcp
from adb_shell.auth.keygen import keygen
from adb_shell.auth.sign_pythonrsa import PythonRSASigner

HOST = os.environ.get("TV_HOST")
PORT = int(os.environ.get("TV_PORT", "5555"))
if not HOST:
    sys.exit(
        "Falta la IP de la tele.\n"
        "  PowerShell:  $env:TV_HOST = \"192.168.1.50\"\n"
        "Luego mira Ajustes → Red (o Wi-Fi) → Estado → Dirección IP.\n"
    )
KEYDIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "adbkey")
KEY = os.path.join(KEYDIR, "adbkey")


def get_signer():
    os.makedirs(KEYDIR, exist_ok=True)
    if not os.path.exists(KEY):
        keygen(KEY)
    with open(KEY) as f:
        priv = f.read()
    with open(KEY + ".pub") as f:
        pub = f.read()
    return PythonRSASigner(pub, priv)


def connect(timeout=120):
    dev = AdbDeviceTcp(HOST, PORT, default_transport_timeout_s=float(timeout))
    dev.connect(rsa_keys=[get_signer()], transport_timeout_s=float(timeout), auth_timeout_s=timeout)
    return dev


def prop(dev, name):
    return (dev.shell(f"getprop {name}") or "").strip()


def probe(dev):
    keys = (
        "ro.product.manufacturer",
        "ro.product.brand",
        "ro.product.model",
        "ro.product.device",
        "ro.build.version.release",
        "ro.build.version.sdk",
        "ro.build.display.id",
        "ro.build.fingerprint",
        "ro.build.version.fireos",
    )
    for k in keys:
        v = prop(dev, k)
        if v:
            print(f"{k} = {v}")
    feat = dev.shell("pm list features") or ""
    print("leanback =", "android.software.leanback" in feat)


def check_android_tv(dev):
    """Return 0 if this looks like Android TV / Google TV, 2 otherwise."""
    fire = prop(dev, "ro.build.version.fireos")
    brand = (prop(dev, "ro.product.brand") + " " + prop(dev, "ro.product.manufacturer")).lower()
    feat = dev.shell("pm list features") or ""
    leanback = "android.software.leanback" in feat
    sdk = prop(dev, "ro.build.version.sdk")
    print(f"marca = {prop(dev, 'ro.product.manufacturer')} {prop(dev, 'ro.product.model')}")
    print(f"android = {prop(dev, 'ro.build.version.release')} (sdk {sdk})")
    print(f"leanback = {leanback}")
    if fire:
        print("INCOMPATIBLE: esta tele usa Fire OS, no Android TV. FastLauncher no se puede instalar.")
        return 2
    if "amazon" in brand and not leanback:
        print("INCOMPATIBLE: parece un dispositivo Amazon / Fire TV.")
        return 2
    if not leanback:
        print(
            "INCOMPATIBLE: no es Android TV ni Google TV (falta android.software.leanback).\n"
            "FastLauncher no funciona en móviles, tablets, Tizen, webOS, Roku ni Windows."
        )
        return 2
    try:
        if int(sdk or "0") < 26:
            print("INCOMPATIBLE: hace falta Android 8 (API 26) o superior.")
            return 2
    except ValueError:
        pass
    print("OK: Android TV / Google TV.")
    return 0


def main():
    args = sys.argv[1:]
    try:
        dev = connect()
    except Exception as e:
        sys.exit(
            f"No se pudo conectar a {HOST}:{PORT}.\n"
            "Comprueba: misma Wi-Fi, depuración USB/ADB activada, y en la tele pulsa ACEPTAR\n"
            f"si sale el aviso «¿Permitir depuración USB?».\nDetalle: {e}"
        )
    code = 0
    try:
        if not args or args == ["--probe"]:
            probe(dev)
        elif args == ["--check"]:
            code = check_android_tv(dev)
        elif args[0] == "--push" and len(args) == 3:
            dev.push(args[1], args[2], transport_timeout_s=300.0, read_timeout_s=300.0)
            print(f"enviado {args[1]} -> {args[2]}")
        elif args[0] == "--pull" and len(args) == 3:
            dev.pull(args[1], args[2], transport_timeout_s=300.0, read_timeout_s=300.0)
            print(f"traído {args[1]} -> {args[2]}")
        elif args[0] == "--batch":
            try:
                sys.stdin.reconfigure(encoding="utf-8-sig")
            except Exception:
                pass
            for line in sys.stdin:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                out = dev.shell(line, read_timeout_s=120.0)
                sys.stdout.write(f"$ {line}\n{out}")
                if out and not out.endswith("\n"):
                    sys.stdout.write("\n")
                sys.stdout.flush()
        else:
            out = dev.shell(" ".join(args), read_timeout_s=120.0)
            sys.stdout.write(out)
    finally:
        dev.close()
    sys.exit(code)


if __name__ == "__main__":
    main()

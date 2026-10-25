"""Configure production signing when available, else the dev app signed with its own key, else a debug dev app."""
import base64
import os
from pathlib import Path


def property_value(value):
    return (value.replace("\\", "\\\\").replace("\n", "\\n")
            .replace("\r", "\\r").replace("\t", "\\t").replace(" ", "\\ ").replace("=", "\\=").replace(":", "\\:"))


PROD = ("KEYSTORE_BASE64", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
# A fork's own key for the dev app. The dev app has to be signed by the same key every time, or
# Android refuses each new build as an update; the debug key is new on every runner.
DEV = tuple("DEV_" + name for name in PROD)


def secrets(names):
    values = [os.environ.get(name, "") for name in names]
    if any(values) and not all(values):
        raise SystemExit(f"Android signing requires all of {', '.join(names)}, or none of them.")
    return values if all(values) else None


def write_signing(values):
    store = Path("bitchord-release.jks")
    store.write_bytes(base64.b64decode("".join(values[0].split()), validate=True))
    store.chmod(0o600)
    properties = Path("keystore.properties")
    properties.write_text("storeFile=bitchord-release.jks\n" + "\n".join(
        f"{key}={property_value(value)}" for key, value in zip(
            ("storePassword", "keyAlias", "keyPassword"), values[1:]
        )
    ) + "\n")
    properties.chmod(0o600)


variant = "DevDebug"
if (values := secrets(PROD)) is not None:
    write_signing(values)
    variant = "ProdRelease"
elif (values := secrets(DEV)) is not None:
    write_signing(values)
    variant = "DevRelease"

with open(os.environ["GITHUB_OUTPUT"], "a") as output:
    output.write(f"variant={variant}\n")

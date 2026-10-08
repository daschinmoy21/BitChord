"""Configure production signing when available, otherwise build the separate dev app."""
import base64
import os
from pathlib import Path


def property_value(value):
    return (value.replace("\\", "\\\\").replace("\n", "\\n")
            .replace("\r", "\\r").replace("\t", "\\t").replace(" ", "\\ ").replace("=", "\\=").replace(":", "\\:"))


names = ("KEYSTORE_BASE64", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
values = [os.environ.get(name, "") for name in names]
if any(values) and not all(values):
    raise SystemExit("Android signing requires all four keystore secrets, or none for a dev APK.")

variant = "DevDebug"
if all(values):
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
    variant = "ProdRelease"

with open(os.environ["GITHUB_OUTPUT"], "a") as output:
    output.write(f"variant={variant}\n")

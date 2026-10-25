"""Exercise CI signing selection without using real signing credentials."""
import base64
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("prepare_android_signing.py").resolve()
PROD = ("KEYSTORE_BASE64", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
DEV = tuple("DEV_" + name for name in PROD)


class SigningTest(unittest.TestCase):
    def run_signing(self, secrets):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "output"
            env = {k: v for k, v in os.environ.items() if k not in PROD + DEV}
            env.update(secrets, GITHUB_OUTPUT=str(output))
            result = subprocess.run([sys.executable, str(SCRIPT)], cwd=root, env=env,
                                    capture_output=True, text=True)
            return (result, output.read_text() if output.exists() else "",
                    (root / "bitchord-release.jks").read_bytes()
                    if (root / "bitchord-release.jks").exists() else None,
                    (root / "keystore.properties").read_text()
                    if (root / "keystore.properties").exists() else None)

    def values(self, names, key=b"test keystore"):
        return dict(zip(names, [base64.b64encode(key).decode(), "p a:ss=word", "alias", "key-password"]))

    def test_without_secrets_builds_dev_debug(self):
        result, output, store, properties = self.run_signing({})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(output, "variant=DevDebug\n")
        self.assertIsNone(store)
        self.assertIsNone(properties)

    def test_dev_key_selects_release_and_writes_signing_properties(self):
        result, output, store, properties = self.run_signing(self.values(DEV))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(output, "variant=DevRelease\n")
        self.assertEqual(store, b"test keystore")
        self.assertIn("storePassword=p\\ a\\:ss\\=word", properties)

    def test_production_key_takes_priority(self):
        result, output, store, _ = self.run_signing(
            self.values(PROD, b"production") | self.values(DEV, b"development"))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(output, "variant=ProdRelease\n")
        self.assertEqual(store, b"production")

    def test_partial_credentials_fail_instead_of_changing_identity(self):
        for names in (PROD, DEV):
            for missing in names:
                with self.subTest(missing=missing):
                    secrets = self.values(names)
                    del secrets[missing]
                    result, output, store, properties = self.run_signing(secrets)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("Android signing requires all", result.stderr)
                    self.assertEqual(output, "")
                    self.assertIsNone(store)
                    self.assertIsNone(properties)

    def test_invalid_base64_fails(self):
        secrets = self.values(DEV)
        secrets[DEV[0]] = "invalid!"
        result, output, _, _ = self.run_signing(secrets)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(output, "")


if __name__ == "__main__":
    unittest.main()

import base64
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
ANDROID = "{http://schemas.android.com/apk/res/android}"
SIGNING = ROOT / "scripts/prepare-signing.sh"

class PackagingTest(unittest.TestCase):
    def test_manifest_has_no_ui_components_or_permissions(self):
        manifest = ET.parse(MAIN / "AndroidManifest.xml").getroot()
        self.assertEqual([child.tag for child in manifest], ["application"])
        app = manifest.find("application")
        self.assertEqual(list(app), [])
        self.assertEqual(app.get(ANDROID + "label"), "@string/app_name")
        self.assertEqual(app.get(ANDROID + "description"), "@string/module_description")
        self.assertEqual(app.get(ANDROID + "allowBackup"), "false")

    def test_names_are_localized_and_description_is_concise(self):
        for locale, name in [("values", "Disable Package Verification Agent"),
                             ("values-zh", "禁用安装包验证代理")]:
            strings = {e.get("name"): e.text.strip() for e in ET.parse(MAIN / "res" / locale / "strings.xml").getroot()}
            self.assertEqual(strings["app_name"], name)
            self.assertIn("CorePatch", (ROOT / "README.md").read_text())
            self.assertLessEqual(len(strings["module_description"]), 120)

    def test_modern_entry_and_static_scope_match_api101(self):
        meta = MAIN / "resources/META-INF/xposed"
        props = dict(line.split("=", 1) for line in (meta / "module.prop").read_text().splitlines())
        self.assertEqual(props, {"minApiVersion": "101", "targetApiVersion": "101", "staticScope": "true"})
        self.assertEqual((meta / "scope.list").read_text().splitlines(), ["system"])
        self.assertEqual((meta / "java_init.list").read_text().splitlines(),
                         ["com.example.disableverificationagent.VerificationAgentModule"])

class SigningTest(unittest.TestCase):
    def run_signing(self, values):
        self.assertTrue(SIGNING.is_file(), "Release signing preparation is missing")
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        env = {"PATH": os.environ["PATH"], "RUNNER_TEMP": temp.name, **values}
        result = subprocess.run(["bash", str(SIGNING)], env=env, capture_output=True, text=True)
        return result, Path(temp.name) / "module-signing.jks"

    def values(self):
        return {"SIGNING_KEY": base64.b64encode(b"synthetic-key-bytes\x00").decode(),
                "ORG_GRADLE_PROJECT_releaseStorePassword": "synthetic-password",
                "ORG_GRADLE_PROJECT_releaseKeyAlias": "module",
                "ORG_GRADLE_PROJECT_releaseKeyPassword": "synthetic-key-password"}

    def test_each_missing_secret_fails_before_writing_key(self):
        for name in self.values():
            with self.subTest(secret=name):
                values = self.values()
                del values[name]
                result, path = self.run_signing(values)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(path.exists())
                self.assertNotIn("synthetic-password", result.stderr)

    def test_invalid_base64_cannot_continue_to_build(self):
        values = self.values()
        values["SIGNING_KEY"] = "!not-valid-base64!"
        result, _ = self.run_signing(values)
        self.assertNotEqual(result.returncode, 0)

    def test_key_bytes_are_decoded_with_private_permissions(self):
        result, path = self.run_signing(self.values())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(path.read_bytes(), b"synthetic-key-bytes\x00")
        self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
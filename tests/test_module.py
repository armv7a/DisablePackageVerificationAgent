"""Behavior checks use the real API101 bytecode and test-only system_server fixtures."""
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ENTRY = ROOT / "app/src/main/java/com/example/disableverificationagent/VerificationAgentModule.java"

class ModuleBehaviorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not ENTRY.is_file():
            return
        cls.temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temp.cleanup)
        work = Path(cls.temp.name)
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if not sdk:
            raise RuntimeError("Set ANDROID_HOME to an Android SDK with platforms/android-35")
        android = Path(sdk) / "platforms/android-35/android.jar"
        api = os.environ.get("LIBXPOSED_API_JAR")
        if not api:
            url = "https://repo.maven.apache.org/maven2/io/github/libxposed/api/101.0.0/api-101.0.0.aar"
            with urllib.request.urlopen(url, timeout=60) as response:
                data = response.read()
            api = work / "api.jar"
            api.write_bytes(zipfile.ZipFile(io.BytesIO(data)).read("classes.jar"))
        classes = work / "classes"
        classes.mkdir()
        sources = [str(p) for p in (ROOT / "tests/java").rglob("*.java")]
        cls.classpath = os.pathsep.join(map(str, (classes, api, android)))
        result = subprocess.run(["javac", "--release", "17", "-cp", cls.classpath,
                                 "-d", str(classes), str(ENTRY), *sources], capture_output=True, text=True)
        if result.returncode:
            raise AssertionError(result.stdout + result.stderr)

    def check_case(self, case):
        self.assertTrue(ENTRY.is_file(), "Module entry implementation is missing")
        result = subprocess.run(["java", "-ea", "-cp", self.classpath, "ModuleTest", case],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_android_9_to_12l_only_disables_agent(self):
        self.check_case("legacy")

    def test_android_13_only_disables_agent(self):
        self.check_case("android13")

    def test_android_14_and_later_preserves_flags_and_proceeds_once(self):
        self.check_case("modern")

    def test_missing_framework_class_reaches_framework_caller(self):
        self.check_case("missing")

    def test_original_exception_is_not_swallowed(self):
        self.check_case("exception")

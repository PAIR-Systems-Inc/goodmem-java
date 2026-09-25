"""Exercise archive validation and ensure failed syncs preserve existing sources."""

import hashlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

import sync_maven


class SyncTest(unittest.TestCase):
    def run_sync(self, entries, *, bad_checksum=False, license_name="Apache-2.0"):
        buf = io.BytesIO()
        with ZipFile(buf, "w") as archive:
            for name, content in entries:
                archive.writestr(name, content)
        sources = buf.getvalue()
        pom = f'''<project xmlns="http://maven.apache.org/POM/4.0.0">
          <groupId>ai.pairsys</groupId><artifactId>goodmem-java</artifactId>
          <version>1.2.3</version><licenses><license><name>{license_name}</name>
          </license></licenses></project>'''.encode()

        def fetch(url):
            if url.endswith(".sha1"):
                return ("0" * 40 if bad_checksum else hashlib.sha1(sources).hexdigest()).encode()
            return pom if url.endswith(".pom") else sources

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "src").mkdir()
            original = root / "src" / "old.java"
            original.write_text("previous snapshot")
            with patch.object(sync_maven, "ROOT", root), patch.object(sync_maven, "fetch", fetch):
                try:
                    sync_maven.sync("1.2.3")
                except ValueError:
                    self.assertEqual(original.read_text(), "previous snapshot")
                    raise
            self.assertFalse(original.exists())
            self.assertEqual((root / "src/main/java/ai/pairsys/goodmem/client/Example.java").read_bytes(), b"public class Example {}")
            self.assertEqual((root / "published-pom.xml").read_bytes(), pom)
            self.assertEqual((root / "VERSION").read_text(), "1.2.3\n")

    def test_exact_source_bytes(self):
        self.run_sync([("ai/pairsys/goodmem/client/Example.java", b"public class Example {}")])

    def test_reject_paths_outside_sdk(self):
        for name in ["../escape.java", "/escape.java", "server/Secret.java", "ai/pairsys/goodmem/client/../../escape.java"]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.run_sync([(name, b"source")])

    def test_bad_checksum(self):
        with self.assertRaisesRegex(ValueError, "checksum"):
            self.run_sync([], bad_checksum=True)

    def test_changed_license(self):
        with self.assertRaisesRegex(ValueError, "licensing"):
            self.run_sync([], license_name="different")

    def test_empty_archive(self):
        with self.assertRaisesRegex(ValueError, "no Java"):
            self.run_sync([])


if __name__ == "__main__":
    unittest.main()

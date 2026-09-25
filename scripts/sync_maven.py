#!/usr/bin/env python3
"""Mirror only source bytes already published in the official Maven artifact."""

import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import tempfile
from urllib.request import urlopen
import xml.etree.ElementTree as ET
from zipfile import ZipFile

BASE = "https://repo.maven.apache.org/maven2/ai/pairsys/goodmem-java"
ROOT = Path(__file__).resolve().parents[1]


def fetch(url):
    with urlopen(url, timeout=60) as response:
        return response.read()


def sync(version=None):
    if version is None:
        metadata = ET.fromstring(fetch(f"{BASE}/maven-metadata.xml"))
        version = metadata.findtext("versioning/release")
    if not version or not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Expected a stable numeric Maven release")
    artifact = f"{BASE}/{version}/goodmem-java-{version}"
    sources = fetch(f"{artifact}-sources.jar")
    expected = fetch(f"{artifact}-sources.jar.sha1").decode().strip().split()[0]
    if hashlib.sha1(sources).hexdigest() != expected:
        raise ValueError("Source archive checksum does not match Maven Central")
    pom = fetch(f"{artifact}.pom")
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    model = ET.fromstring(pom)
    for field, value in [("groupId", "ai.pairsys"), ("artifactId", "goodmem-java"), ("version", version)]:
        if model.findtext(f"m:{field}", namespaces=ns) != value:
            raise ValueError(f"Unexpected POM {field}")
    if model.findtext("m:licenses/m:license/m:name", namespaces=ns) != "Apache-2.0":
        raise ValueError("Review changed licensing before mirroring this release")

    count = 0
    with tempfile.TemporaryDirectory(dir=ROOT) as tmp:
        staged = Path(tmp) / "src"
        with ZipFile(io.BytesIO(sources)) as archive:
            for entry in archive.infolist():
                if entry.is_dir() or entry.filename == "META-INF/MANIFEST.MF":
                    continue
                path = PurePosixPath(entry.filename)
                if path.is_absolute() or ".." in path.parts or "\\" in entry.filename:
                    raise ValueError(f"Unsafe archive path: {entry.filename}")
                if not entry.filename.startswith("ai/pairsys/goodmem/client/"):
                    raise ValueError(f"Review unexpected SDK content: {entry.filename}")
                if path.suffix not in (".java", ".json"):
                    raise ValueError(f"Review unexpected SDK file: {entry.filename}")
                source_set = "java" if path.suffix == ".java" else "resources"
                target = staged / "main" / source_set / path
                if target.exists():
                    raise ValueError(f"Duplicate archive path: {entry.filename}")
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(entry))
                count += path.suffix == ".java"
        if not count:
            raise ValueError("Source archive contains no Java files")
        if (ROOT / "src").exists():
            shutil.rmtree(ROOT / "src")
        shutil.move(str(staged), ROOT / "src")

    (ROOT / "published-pom.xml").write_bytes(pom)
    (ROOT / "VERSION").write_text(version + "\n")
    readme = ROOT / "README.md"
    if readme.exists():
        readme.write_text(re.sub(r"<version>\d+\.\d+\.\d+</version>",
                                f"<version>{version}</version>", readme.read_text()))
    provenance = {
        "coordinates": f"ai.pairsys:goodmem-java:{version}",
        "source_url": f"{artifact}-sources.jar",
        "source_sha256": hashlib.sha256(sources).hexdigest(),
        "pom_url": f"{artifact}.pom",
        "pom_sha256": hashlib.sha256(pom).hexdigest(),
        "java_files": count,
    }
    (ROOT / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
    print(f"Mirrored {count} Java files from {provenance['coordinates']}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", help="Published stable version; default: latest Maven release")
    sync(parser.parse_args().version)

#!/usr/bin/env python3
"""
V2-18: a CycloneDX software bill of materials for the release APK.

An SBOM answers a question the build cannot answer later: when an advisory lands
next year against some transitive library, was it in the version we shipped? The
verification metadata cannot answer that — it is the superset of everything the
build ever resolved, including the AGP tooling and the test-only libraries. Only
`releaseRuntimeClasspath` is the shipped set, and this writes it down.

WHAT IS IN IT

The 148 external coordinates packaged into the APK, as CycloneDX 1.5 components
with Package URLs. Project modules (`:core-messaging` and friends) are the
subject of the document, not dependencies in it.

DELIBERATELY DETERMINISTIC

No timestamp and no random serial number unless asked for. The serial is derived
from a SHA-256 over the component list, so the same dependency graph produces a
byte-identical SBOM: two builds can be diffed to prove the graph did not move,
which is impossible when every run stamps a fresh UUID and a clock reading. Pass
`--timestamp` when publishing a release, where provenance matters more than
comparability.

NO PLUGIN, ON PURPOSE

A CycloneDX Gradle plugin would do this too, and would add a plugin — with its
own transitive tree — to the build whose supply chain this is meant to document.
The input is one text file; the standard library is enough.

USAGE

    ./gradlew :app:shippedDependencies
    python3 scripts/generate-sbom.py --out app/build/reports/sbom-release.cdx.json
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import sys
import urllib.parse
from pathlib import Path

SPEC_VERSION = "1.5"


def purl(group: str, name: str, version: str) -> str:
    """Package URL for a Maven artifact (purl spec, `pkg:maven/...`)."""
    quote = lambda s: urllib.parse.quote(s, safe="")
    return f"pkg:maven/{quote(group)}/{quote(name)}@{quote(version)}"


def read_app_version(build_file: Path) -> str:
    if not build_file.is_file():
        return "unknown"
    match = re.search(r'versionName\s*=\s*"([^"]+)"', build_file.read_text())
    return match.group(1) if match else "unknown"


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description="Write a CycloneDX SBOM for the release APK.")
    parser.add_argument("--shipped", type=Path,
                        default=root / "app/build/reports/shipped-dependencies.txt")
    parser.add_argument("--out", type=Path,
                        default=root / "app/build/reports/sbom-release.cdx.json")
    parser.add_argument("--app-version", default=None)
    parser.add_argument("--timestamp", action="store_true",
                        help="stamp the current UTC time; breaks byte-for-byte reproducibility")
    args = parser.parse_args()

    if not args.shipped.is_file():
        print(f"generate-sbom: missing {args.shipped}\n"
              "Run `./gradlew :app:shippedDependencies` first.", file=sys.stderr)
        return 2

    components = []
    for line in sorted(set(args.shipped.read_text().split())):
        if not line.strip():
            continue
        try:
            group, name, version = line.strip().rsplit(":", 2)
        except ValueError:
            print(f"generate-sbom: cannot parse coordinate '{line}'", file=sys.stderr)
            return 2
        components.append({
            "type": "library",
            "group": group,
            "name": name,
            "version": version,
            "purl": purl(group, name, version),
        })

    if not components:
        print("generate-sbom: no components; the release classpath cannot be empty",
              file=sys.stderr)
        return 2

    # A UUID shaped from the content digest, so an unchanged graph yields an
    # unchanged document. Version/variant nibbles are set to keep it a
    # well-formed RFC 4122 v4-shaped UUID.
    digest = hashlib.sha256(
        "\n".join(c["purl"] for c in components).encode()
    ).hexdigest()
    serial = (f"{digest[0:8]}-{digest[8:12]}-4{digest[13:16]}-"
              f"{'89ab'[int(digest[16], 16) % 4]}{digest[17:20]}-{digest[20:32]}")

    bom = {
        "bomFormat": "CycloneDX",
        "specVersion": SPEC_VERSION,
        "serialNumber": f"urn:uuid:{serial}",
        "version": 1,
        "metadata": {
            "component": {
                "type": "application",
                "name": "com.messages.app",
                "version": args.app_version or read_app_version(root / "app/build.gradle.kts"),
                "purl": purl("com.messages", "app",
                             args.app_version or read_app_version(root / "app/build.gradle.kts")),
            },
            "tools": [{"name": "scripts/generate-sbom.py", "vendor": "Messages"}],
        },
        "components": components,
    }
    if args.timestamp:
        bom["metadata"]["timestamp"] = (
            dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()
        )

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(bom, indent=2, sort_keys=False) + "\n")
    print(f"wrote {args.out} — {len(components)} shipped components, serial {serial}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

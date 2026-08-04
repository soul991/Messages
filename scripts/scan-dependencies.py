#!/usr/bin/env python3
"""
V2-18: ask a vulnerability database about every dependency this build resolves.

`gradle/verification-metadata.xml` already pins a SHA-256 for every artifact, so
nobody can swap one out from under us. That is integrity, and integrity is not
safety: a checksum attests that we got the exact bytes upstream published, and
says nothing about whether those bytes have a published CVE. Until this script
existed, nothing in the build ever asked.

WHAT IT SCANS

Two sets, and the distinction between them is the whole point:

  shipped  — the coordinates packaged into the release APK, taken from
             `:app:shippedDependencies` (releaseRuntimeClasspath). Reachable by
             anything that can reach the app, which for a messaging app means a
             stranger who knows the phone number.

  build    — everything else Gradle resolves, by subtraction from the
             verification metadata: the Android Gradle Plugin's own tree, KSP,
             lint, test-only libraries. Exploiting one of these requires
             influence over the build, not over a message.

Both are worth knowing about; only the first is worth waking up for. A scanner
that reports them as one list produces a number nobody acts on.

The build set deliberately over-reports. Verification metadata records every
artifact Gradle resolved, including versions that lost a conflict and were never
put on a classpath, so a superseded coordinate is scanned too. That is the safe
direction to be wrong in for the scope that cannot reach a user.

POLICY

  shipped  — any advisory at any severity fails. There is no acceptable level
             of known-vulnerable code inside the APK.
  build    — HIGH, CRITICAL and unrated fail unless the exact advisory id is
             recorded in the baseline.

Why a baseline rather than a severity gate for build-time findings: on
2026-08-01 the AGP 8.5.2 tree carried 23 blocking advisories, and resolving the
newest Android Gradle Plugin the toolchain can reach was measured to leave 24 —
one more, not fewer. These are structural to what AGP vendors, not a lag anyone
can bump their way out of. A gate that no reachable upgrade can satisfy gets
switched off; a baseline stays on and still fires the day something NEW appears,
which is the only build-time signal that was ever actionable.

Baseline entries live in `gradle/accepted-advisories.json` with a written reason
and a hard expiry, and list exact advisory ids — never a wildcard id, because a
wildcard would swallow the new finding this is built to catch. Accepted findings
are still printed on every run: an acknowledgement records a decision, it does
not make the finding invisible, and an expired one fails again by itself.

FAILING CLOSED

If the OSV API cannot be reached, this exits non-zero and says so. A scanner
that reports "no vulnerabilities found" when it could not ask anything is worse
than no scanner, because it produces a green check that means nothing.

Standard library only, and deliberately: adding a scanning dependency to fix a
supply-chain finding would enlarge the supply chain it is meant to protect.

USAGE

    ./gradlew :app:shippedDependencies
    python3 scripts/scan-dependencies.py

Exit codes: 0 clean, 1 findings over threshold, 2 could not complete the scan.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import datetime as dt
import fnmatch
import json
import math
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

OSV_BATCH = "https://api.osv.dev/v1/querybatch"
OSV_VULN = "https://api.osv.dev/v1/vulns/"
BATCH_SIZE = 500
TIMEOUT = 90
DETAIL_WORKERS = 8

SEVERITIES = ["NONE", "LOW", "MEDIUM", "HIGH", "CRITICAL", "UNKNOWN"]
# Unrated sorts with the worst on purpose: an advisory nobody has scored is not
# evidence of a harmless advisory, it is an absence of evidence either way.
BUILD_FAILS_AT = {"HIGH", "CRITICAL", "UNKNOWN"}


# --------------------------------------------------------------------------
# Inventory
# --------------------------------------------------------------------------

def read_shipped(path: Path) -> set[str]:
    if not path.is_file():
        die(
            f"missing {path}\n"
            "Run `./gradlew :app:shippedDependencies` first — without it this "
            "script cannot tell APK code from build-machine code, and reporting "
            "them as one list is the failure mode it exists to avoid."
        )
    coords = {line.strip() for line in path.read_text().splitlines() if line.strip()}
    if not coords:
        die(f"{path} is empty; the release classpath cannot be")
    return coords


def read_metadata(path: Path) -> set[str]:
    """Every artifact Gradle has resolved, from the verification metadata.

    Read as text rather than parsed as XML because the file is namespaced and
    the only thing wanted here is three attributes off one element.
    """
    if not path.is_file():
        die(f"missing {path}")
    found = re.findall(
        r'<component\s+group="([^"]+)"\s+name="([^"]+)"\s+version="([^"]+)"',
        path.read_text(),
    )
    if not found:
        die(f"no <component> entries in {path}; has the format changed?")
    return {f"{g}:{n}:{v}" for g, n, v in found}


# --------------------------------------------------------------------------
# CVSS
# --------------------------------------------------------------------------

_CVSS_WEIGHTS = {
    "AV": {"N": 0.85, "A": 0.62, "L": 0.55, "P": 0.2},
    "AC": {"L": 0.77, "H": 0.44},
    "UI": {"N": 0.85, "R": 0.62},
    "C": {"H": 0.56, "L": 0.22, "N": 0.0},
    "I": {"H": 0.56, "L": 0.22, "N": 0.0},
    "A": {"H": 0.56, "L": 0.22, "N": 0.0},
}
_PR_UNCHANGED = {"N": 0.85, "L": 0.62, "H": 0.27}
_PR_CHANGED = {"N": 0.85, "L": 0.68, "H": 0.50}


def cvss_base_score(vector: str) -> float | None:
    """CVSS v3.x base score from a vector string, per the FIRST specification.

    Computed here rather than read off `database_specific.severity` so the
    rating does not depend on which database an advisory happened to come
    through. That field is the fallback, not the source.
    """
    try:
        parts = dict(p.split(":", 1) for p in vector.split("/") if ":" in p)
        if not parts.get("CVSS", "").startswith("3"):
            return None
        changed = parts["S"] == "C"
        pr = (_PR_CHANGED if changed else _PR_UNCHANGED)[parts["PR"]]
        w = _CVSS_WEIGHTS
        iss = 1 - (
            (1 - w["C"][parts["C"]]) * (1 - w["I"][parts["I"]]) * (1 - w["A"][parts["A"]])
        )
        impact = (
            7.52 * (iss - 0.029) - 3.25 * (iss - 0.02) ** 15 if changed else 6.42 * iss
        )
        if impact <= 0:
            return 0.0
        exploitability = 8.22 * w["AV"][parts["AV"]] * w["AC"][parts["AC"]] * pr * w["UI"][parts["UI"]]
        raw = min((1.08 if changed else 1.0) * (impact + exploitability), 10.0)
        return math.ceil(raw * 10) / 10  # CVSS "roundup", not round-half-even
    except (KeyError, ValueError):
        return None


def band(score: float) -> str:
    if score == 0:
        return "NONE"
    if score < 4.0:
        return "LOW"
    if score < 7.0:
        return "MEDIUM"
    if score < 9.0:
        return "HIGH"
    return "CRITICAL"


def severity_of(vuln: dict) -> tuple[str, str]:
    """(severity, how it was determined) for one OSV entry."""
    for entry in vuln.get("severity") or []:
        score = cvss_base_score(entry.get("score", ""))
        if score is not None:
            return band(score), f"CVSS {score}"
    stated = (vuln.get("database_specific") or {}).get("severity")
    if isinstance(stated, str):
        normalised = {"MODERATE": "MEDIUM"}.get(stated.upper(), stated.upper())
        if normalised in SEVERITIES:
            return normalised, "database rating"
    return "UNKNOWN", "unrated"


# --------------------------------------------------------------------------
# OSV
# --------------------------------------------------------------------------

def post_json(url: str, payload: dict) -> dict:
    request = urllib.request.Request(
        url, data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return json.load(response)


def query_osv(coordinates: list[str]) -> dict[str, list[str]]:
    """coordinate -> advisory ids, for the coordinates that have any."""
    queries = []
    for coord in coordinates:
        # OSV names a Maven package "group:artifact"; the version is separate.
        group_name, version = coord.rsplit(":", 1)
        queries.append(
            {"package": {"ecosystem": "Maven", "name": group_name}, "version": version}
        )

    hits: dict[str, list[str]] = {}
    for start in range(0, len(queries), BATCH_SIZE):
        chunk = queries[start:start + BATCH_SIZE]
        results = post_json(OSV_BATCH, {"queries": chunk}).get("results", [])
        if len(results) != len(chunk):
            die("OSV returned a different number of results than queries; refusing to guess")
        for coord, result in zip(coordinates[start:start + BATCH_SIZE], results):
            ids = [v["id"] for v in (result.get("vulns") or [])]
            if ids:
                hits[coord] = ids
    return hits


def fetch_details(ids: set[str]) -> dict[str, dict]:
    def one(vuln_id: str) -> tuple[str, dict]:
        with urllib.request.urlopen(OSV_VULN + vuln_id, timeout=TIMEOUT) as response:
            return vuln_id, json.load(response)

    out: dict[str, dict] = {}
    with concurrent.futures.ThreadPoolExecutor(DETAIL_WORKERS) as pool:
        for vuln_id, detail in pool.map(one, sorted(ids)):
            out[vuln_id] = detail
    return out


# --------------------------------------------------------------------------
# Accepted findings
# --------------------------------------------------------------------------

class Acceptance:
    def __init__(self, raw: dict, index: int):
        for field in ("ids", "coordinate", "scope", "reason", "expires"):
            if not raw.get(field):
                die(f"accepted-advisories.json entry {index} is missing '{field}'")
        self.ids = raw["ids"] if isinstance(raw["ids"], list) else [raw["ids"]]
        # The coordinate may be a glob; the ids may not. A wildcard id would
        # accept advisories nobody has read, including ones published after the
        # entry was written — exactly the finding this scan exists to surface.
        for i in self.ids:
            if "*" in i or "?" in i:
                die(f"accepted-advisories.json entry {index}: advisory ids must be "
                    f"exact, not patterns ('{i}'). A wildcard here would suppress "
                    "future advisories in the same artifact, which is the one thing "
                    "this file must never do.")
        self.coordinate = raw["coordinate"]
        self.scope = raw["scope"]
        self.reason = raw["reason"]
        try:
            self.expires = dt.date.fromisoformat(raw["expires"])
        except ValueError:
            die(f"accepted-advisories.json entry {index}: 'expires' must be YYYY-MM-DD")
        if self.scope not in ("shipped", "build"):
            die(f"accepted-advisories.json entry {index}: scope must be shipped or build")

    def covers(self, vuln_id: str, coordinate: str, scope: str, today: dt.date) -> bool:
        # Scope is matched, not ignored: a finding accepted because it only
        # affects the build machine must fail again the day it turns up in the
        # APK instead.
        return (
            scope == self.scope
            and today <= self.expires
            and vuln_id in self.ids
            and fnmatch.fnmatch(coordinate.rsplit(":", 1)[0], self.coordinate)
        )


def read_accepted(path: Path) -> list[Acceptance]:
    if not path.is_file():
        return []
    data = json.loads(path.read_text())
    return [Acceptance(entry, i) for i, entry in enumerate(data.get("accepted", []))]


# --------------------------------------------------------------------------

def die(message: str) -> None:
    print(f"dependency scan: {message}", file=sys.stderr)
    raise SystemExit(2)


def main() -> int:
    parser = argparse.ArgumentParser(description="Scan resolved dependencies against OSV.")
    root = Path(__file__).resolve().parent.parent
    parser.add_argument("--shipped", type=Path,
                        default=root / "app/build/reports/shipped-dependencies.txt")
    parser.add_argument("--metadata", type=Path,
                        default=root / "gradle/verification-metadata.xml")
    parser.add_argument("--accepted", type=Path,
                        default=root / "gradle/accepted-advisories.json")
    parser.add_argument("--json", type=Path, help="also write the findings as JSON")
    parser.add_argument("--today", type=dt.date.fromisoformat, default=dt.date.today(),
                        help="evaluate acceptance expiry against this date (testing)")
    args = parser.parse_args()

    shipped = read_shipped(args.shipped)
    everything = read_metadata(args.metadata) | shipped
    build_only = everything - shipped
    accepted = read_accepted(args.accepted)

    print(f"scanning {len(everything)} resolved components "
          f"({len(shipped)} shipped, {len(build_only)} build-time) against OSV")

    try:
        hits = query_osv(sorted(everything))
        details = fetch_details({i for ids in hits.values() for i in ids})
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        die(f"could not reach the OSV API ({error}).\n"
            "Failing rather than reporting a clean scan that never happened.")

    findings, suppressed = [], []
    for coordinate in sorted(hits):
        scope = "shipped" if coordinate in shipped else "build"
        for vuln_id in sorted(hits[coordinate]):
            detail = details.get(vuln_id, {})
            if detail.get("withdrawn"):
                continue
            severity, basis = severity_of(detail)
            record = {
                "id": vuln_id,
                "coordinate": coordinate,
                "scope": scope,
                "severity": severity,
                "basis": basis,
                "summary": (detail.get("summary") or "").strip(),
            }
            cover = next(
                (a for a in accepted if a.covers(vuln_id, coordinate, scope, args.today)),
                None,
            )
            if cover:
                record["accepted_reason"] = cover.reason
                record["accepted_until"] = cover.expires.isoformat()
                suppressed.append(record)
            else:
                record["fails"] = scope == "shipped" or severity in BUILD_FAILS_AT
                findings.append(record)

    order = {s: i for i, s in enumerate(reversed(SEVERITIES))}
    findings.sort(key=lambda f: (f["scope"] != "shipped", order.get(f["severity"], 0)))

    report(findings, suppressed)

    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(
            {"scanned": len(everything), "shipped": len(shipped),
             "findings": findings, "accepted": suppressed}, indent=2) + "\n")
        print(f"\nwrote {args.json}")

    blocking = [f for f in findings if f["fails"]]
    if blocking:
        print(f"\nFAIL: {len(blocking)} finding(s) over threshold.")
        print("Fix by upgrading, or record a reviewed exception with an expiry "
              "date in gradle/accepted-advisories.json.")
        return 1
    print("\nOK: no findings over threshold.")
    return 0


def report(findings: list[dict], suppressed: list[dict]) -> None:
    shipped = [f for f in findings if f["scope"] == "shipped"]
    print(f"\n--- shipped code (in the APK): {len(shipped)} finding(s) ---")
    if not shipped:
        print("  none")
    for f in shipped:
        print(f"  [{f['severity']}] {f['coordinate']}  {f['id']}  ({f['basis']})")
        if f["summary"]:
            print(f"      {f['summary'][:120]}")

    build = [f for f in findings if f["scope"] == "build"]
    print(f"\n--- build-time only (not in the APK): {len(build)} finding(s) ---")
    if not build:
        print("  none")
    for f in build:
        marker = "FAIL" if f["fails"] else "note"
        print(f"  {marker} [{f['severity']}] {f['coordinate']}  {f['id']}")

    # Printed every run on purpose. An acknowledgement is a decision on the
    # record, not a way to stop seeing the finding.
    print(f"\n--- accepted, with expiry: {len(suppressed)} finding(s) ---")
    if not suppressed:
        print("  none")
    by_reason: dict[tuple[str, str], list[str]] = {}
    for f in suppressed:
        by_reason.setdefault((f["accepted_reason"], f["accepted_until"]), []).append(
            f"{f['coordinate'].rsplit(':', 1)[0]} {f['id']}")
    for (reason, until), items in sorted(by_reason.items(), key=lambda kv: kv[0][1]):
        print(f"  until {until}: {len(items)} finding(s)")
        print(f"      {reason}")


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Tests the release tag pattern. Run by the actionlint job; needs no network and no Gradle.

The same pattern guards three places: the tag check of the `verify` job and the check of the `publish` step in
`.github/workflows/release.yml` (grep -E), and `versionPattern` in `.github/gradle/publish-github-packages.init.gradle.kts`
(Kotlin Regex). This script reads the pattern out of each place, fails when they differ, and runs the fixtures below
against it. Accepted: X.Y.Z, X.Y.Z-M<n> and X.Y.Z-RC<n> (n from 1, no leading zero). Everything else is refused.
"""
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[2]

ACCEPTED = [
    "1.0.0", "2.0.0", "10.20.30",
    "1.0.0-M1", "1.0.0-M5", "1.0.0-M12", "2.0.0-M1",
    "1.0.0-RC1", "1.0.0-RC2", "1.0.0-RC10", "2.0.0-RC1",
]
REFUSED = [
    "", "1", "1.0", "v1.0.0", "1.0.0.0", "1.0", "1.0.0-", "1.0.0-M", "1.0.0-RC",
    "1.0.0-rc1", "1.0.0-Rc1", "1.0.0-m1", "1.0.0-RC0", "1.0.0-RC01", "1.0.0-RC1a", "1.0.0-RC1.1", "1.0.0-RC-1",
    "1.0.0-RC1-RC2", "1.0.0-M1-RC1", "1.0.0-RC1-M1", "1.0.0RC1", "1.0.0_RC1",
    "1.0.0-SNAPSHOT", "1.0.0-M1-SNAPSHOT", "1.0.0-RC1-SNAPSHOT", "1.0.0-beta1", "1.0.0-alpha1", "1.0.0-CR1", "1.0.0-final",
    "1.0.0+build", "1.0.0 ", " 1.0.0", "latest", "master",
]


def workflow_patterns():
    text = (root / ".github/workflows/release.yml").read_text()
    return re.findall(r"grep -Eq '([^']*)'", text)


def init_script_pattern():
    text = (root / ".github/gradle/publish-github-packages.init.gradle.kts").read_text()
    found = re.findall(r'val versionPattern = Regex\("([^"]*)"\)', text)
    return [p.replace("\\\\", "\\") for p in found]


failures = []
workflow = workflow_patterns()
init = init_script_pattern()
if len(workflow) != 2:
    failures.append(f"release.yml must check the tag in exactly two places (verify and publish), found {len(workflow)}")
if len(init) != 1:
    failures.append(f"the init script must define versionPattern exactly once, found {len(init)}")
patterns = set(workflow + init)
if len(patterns) != 1:
    failures.append(f"the tag patterns differ: {sorted(patterns)}")

if not failures:
    pattern = re.compile(next(iter(patterns)))
    for tag in ACCEPTED:
        if not pattern.fullmatch(tag):
            failures.append(f"'{tag}' must be accepted")
    for tag in REFUSED:
        if pattern.fullmatch(tag):
            failures.append(f"{tag!r} must be refused")

for failure in failures:
    print(f"::error title=test-release-tag::{failure}")
if failures:
    sys.exit(1)
print(f"test-release-tag: {len(ACCEPTED)} tags accepted, {len(REFUSED)} refused, one pattern in 3 places")

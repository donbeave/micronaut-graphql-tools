#!/usr/bin/env python3
"""Keeps the lists of modules equal to the modules of settings.gradle.kts (see MAINTAINING.md, "Module lists").

settings.gradle.kts is the one source. A module is published unless it is a test suite (the benchmarks and the TCK are test suites too)
(NOT_PUBLISHED_PREFIXES below); the published modules other than the BOM are the library modules, which compile to release 17.
Fails when one of these lists names a different set of modules:
  1. the publish init script (`publishable`) and the summary of release.yml: every published module and the BOM;
  2. the JDK 17 `check` command in gradle.yml, AGENTS.md and CONTRIBUTING.md, and the module lists of the comments and rules around it;
  3. the BOM README and the module list of the root README (table and artifact coordinates);
  4. the module layout of MAINTAINING.md and the module list of PackageDocumentationSpec.
Adding a module needs one edit in settings.gradle.kts, plus none when it is a `test-suite-*` or `docs-examples-*` module; this script then
names every list that is stale.
"""
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent.parent
NOT_PUBLISHED_PREFIXES = ("test-suite-", "docs-examples-")
BOM = "graphql-tools-bom"
problems = []


def read(path):
    return (root / path).read_text()


def artifact(folder):
    return "micronaut-" + folder


settings = read("settings.gradle.kts")
modules = re.findall(r'^include\("([^"]+)"\)', settings, re.M)
if not modules:
    sys.exit("check-modules: no include(...) in settings.gradle.kts")
for folder in modules:
    if not (root / folder).is_dir():
        problems.append(f"settings.gradle.kts includes `{folder}`, which is not a folder")
published = [m for m in modules if not m.startswith(NOT_PUBLISHED_PREFIXES)]
libraries = [m for m in published if m != BOM]
if BOM not in published:
    problems.append(f"settings.gradle.kts does not include `{BOM}`")
for folder in sorted(root.glob("graphql-tools*")):
    if folder.is_dir() and (folder / "build.gradle.kts").exists() and folder.name not in modules:
        problems.append(f"folder `{folder.name}` has a build file but is not included in settings.gradle.kts")


def compare(where, found, expected, what):
    found, expected = set(found), set(expected)
    if not found:
        problems.append(f"{where}: no {what} found, so the list cannot be checked (did its wording change?)")
        return
    for name in sorted(expected - found):
        problems.append(f"{where}: {what} is missing `{name}`")
    for name in sorted(found - expected):
        problems.append(f"{where}: {what} names `{name}`, which is not in settings.gradle.kts or not of this kind")


def section(text, start, end=r"\n(?=## |\n\n)"):
    m = re.search(start, text)
    if not m:
        return ""
    tail = text[m.start():]
    e = re.search(end, tail[1:])
    return tail[: e.end() + 1] if e else tail


def artifacts_in(text):
    return re.findall(r"micronaut-graphql-tools(?:-[a-z]+)*(?![\w-])", text)


def gradle_checks(text):
    return [a for a in re.findall(r":(micronaut-graphql-tools(?:-[a-z]+)*):check", text)]


library_artifacts = [artifact(m) for m in libraries]
published_artifacts = [artifact(m) for m in published]

init = read(".github/gradle/publish-github-packages.init.gradle.kts")
m = re.search(r"val publishable = setOf\((.*?)\)", init, re.S)
compare("publish-github-packages.init.gradle.kts", re.findall(r'"([^"]+)"', m.group(1)) if m else [], published_artifacts, "`publishable` list")

release = read(".github/workflows/release.yml")
compare("release.yml", re.findall(r"echo \"- io\.micronaut\.graphql\.tools:(micronaut-graphql-tools(?:-[a-z]+)*):\$\{TAG\}", release), published_artifacts, "published-artifacts summary")

gradle = read(".github/workflows/gradle.yml")
compare("gradle.yml", gradle_checks(gradle), library_artifacts, "JDK 17 `check` command")
rule = re.search(r"The library modules \(([^)]*)\)", gradle)
compare("gradle.yml", re.findall(r"graphql-tools(?:-[a-z]+)*", rule.group(1)) if rule else [], libraries, "comment that lists the JDK 17 library modules")

agents = read("AGENTS.md")
compare("AGENTS.md", gradle_checks(agents), library_artifacts, "JDK 17 `check` command")
rule = re.search(r"Library modules \(([^)]*)\) compile with", agents)
compare("AGENTS.md", re.findall(r"graphql-tools(?:-[a-z]+)*", rule.group(1)) if rule else [], libraries, "`Java 17 target` rule")

contributing = read("CONTRIBUTING.md")
commands = [l for l in contributing.splitlines() if ":micronaut-graphql-tools:check" in l]
if not commands:
    problems.append("CONTRIBUTING.md: no JDK 17 `check` command found, so it cannot be checked")
for line in commands:
    compare("CONTRIBUTING.md", gradle_checks(line), library_artifacts, "JDK 17 `check` command")

bom = read("graphql-tools-bom/README.md")
bullet = section(bom, r"- Manage the versions of", r"\n(?=- |\n|## )")
compare("graphql-tools-bom/README.md", artifacts_in(bullet), library_artifacts, "list of managed artifacts")
managed = re.search(r"The modules it manages are ([^\n]*)", bom)
compare("graphql-tools-bom/README.md", re.findall(r"graphql-tools(?:-[a-z]+)*", managed.group(1)) if managed else [], libraries, "`The modules it manages are` list")

readme = read("README.md")
table = re.findall(r"^\| \[`([^`]+)`\]\([^)]*\) \| `:([^`]+)` \|", readme, re.M)
compare("README.md", [f for f, _ in table], modules, "module table")
for f, gradle_path in table:
    expected_path = artifact(f) if f in published or f.startswith("docs-examples-") else f
    if gradle_path != expected_path:
        problems.append(f"README.md: the module table gives `:{gradle_path}` for `{f}`, expected `:{expected_path}`")
coordinates = re.findall(r"^\| .*\| `io\.micronaut\.graphql\.tools:(micronaut-graphql-tools(?:-[a-z]+)*)` \|$", readme, re.M)
compare("README.md", coordinates, published_artifacts, "artifact coordinates table")

maintaining = read("MAINTAINING.md")
layout = section(maintaining, r"## Module layout")
bullets = re.findall(r"^- `(graphql-tools(?:-[a-z]+)*)`", layout, re.M) + (["graphql-tools"] if re.search(r"`graphql-tools` \(the runtime\)", layout) else [])
compare("MAINTAINING.md", bullets, libraries, "module layout")

spec = read("graphql-tools/src/test/groovy/io/micronaut/graphql/tools/docs/PackageDocumentationSpec.groovy")
m = re.search(r"MODULES = \[(.*?)\]", spec)
compare("PackageDocumentationSpec.groovy", re.findall(r"'([^']+)'", m.group(1)) if m else [], libraries + ["test-suite-tck"], "`MODULES` list")

if problems:
    for p in problems:
        print(f"::error title=check-modules::{p}")
    sys.exit(1)
print(f"check-modules: {len(libraries)} library modules and the BOM agree across settings.gradle.kts and the module lists")

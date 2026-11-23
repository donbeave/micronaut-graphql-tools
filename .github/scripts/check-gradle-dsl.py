#!/usr/bin/env python3
"""Keeps the Gradle build logic in the Kotlin DSL (see MAINTAINING.md, "Gradle build logic").

Fails when a file tracked by git has a name that ends in `.gradle` (a Groovy DSL build script, init script or script plugin).
Kotlin DSL files end in `.gradle.kts` and are fine. The rule covers build scripts only: Groovy sources of the tests are not named `*.gradle`.
"""
import subprocess
import sys

tracked = subprocess.run(["git", "ls-files", "-z"], check=True, capture_output=True, text=True).stdout.split("\0")
groovy_dsl = sorted(f for f in tracked if f.endswith(".gradle"))

for f in groovy_dsl:
    print(f"::error file={f},title=check-gradle-dsl::{f} is a Groovy DSL Gradle file; write it in the Kotlin DSL ({f}.kts)")

if groovy_dsl:
    sys.exit(1)
print("check-gradle-dsl: no tracked *.gradle file")

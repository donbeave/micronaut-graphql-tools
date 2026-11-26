#!/usr/bin/env python3
"""Keeps the names of the Spock specs and of the Java test methods consistent.

A Groovy test class is named `*Spec` (a Spock specification, or the abstract base of one), never `*Test`. The one exception is a class that
the guide includes with `snippet::`: the macro looks the class up by the same name in the Java, Kotlin and Groovy suites, so the Groovy
class keeps the `*Test` name of its Java and Kotlin twins.
A Java test method is a short sentence such as `rejectsAnUnknownUser`, never `testX`.

  check-test-names.py            fails and lists every name that breaks the rule
  check-test-names.py --rename   renames them in place (git mv for files) and rewrites the references

The rename is idempotent: a second run changes nothing. Java methods are renamed from METHODS; a `testX` method that is
not in the table fails the check, so the author picks its name.
"""
import re
import subprocess
import sys
from pathlib import Path

METHODS = {
    ("AddBookMutationTest.java", "testNotAuthenticated"): "rejectsAnUnauthenticatedCaller",
    ("AddBookMutationTest.java", "testAddBook"): "addsABook",
    ("AllBooksQueryTest.java", "testAll"): "returnsAllBooks",
    ("CurrentUserQueryTest.java", "testGuest"): "returnsNoUserForAGuest",
    ("CurrentUserQueryTest.java", "testAuthenticated"): "returnsTheAuthenticatedUser",
    ("IntroducedQueryTest.java", "testIntroductionAdviceResolver"): "resolvesAFieldThroughTheIntroductionAdvice",
    ("SearchProfileQueryTest.java", "testEmptyResults"): "returnsNoProfilesWithoutAMatch",
    ("SearchProfileQueryTest.java", "testSingleResult"): "returnsASingleProfile",
    ("SignInMutationTest.java", "testSuccessAuthentication"): "signsInAKnownUser",
    ("SignInMutationTest.java", "testUnknownUser"): "rejectsAnUnknownUser",
    ("SignInMutationTest.java", "testWrongPassword"): "rejectsAWrongPassword",
    ("SignUpMutationTest.java", "testSuccessRegistration"): "registersANewUser",
    ("SignUpMutationTest.java", "testExistedUsername"): "rejectsAnExistingUsername",
}

GROOVY_TEST_CLASS = re.compile(r"^[\w/.-]*src/test/groovy/.*?(\w+)Test\.groovy$")
JAVA_TEST_METHOD = re.compile(r"\bvoid (test[A-Z]\w*)\(")


SNIPPET = re.compile(r"snippet::([\w.]+)\[")


def snippet_classes(files):
    names = set()
    for f in files:
        if f.startswith("src/main/docs/") and f.endswith(".adoc"):
            names.update(SNIPPET.findall(Path(f).read_text()))
    return names


def tracked():
    out = subprocess.run(["git", "ls-files", "-z"], check=True, capture_output=True, text=True).stdout
    return [f for f in out.split("\0") if f]


def main():
    rename = "--rename" in sys.argv[1:]
    files = tracked()
    problems = []

    included = snippet_classes(files)
    groovy = {}
    for f in files:
        m = GROOVY_TEST_CLASS.match(f)
        if m and f.split("src/test/groovy/")[1][: -len(".groovy")].replace("/", ".") not in included:
            groovy[m.group(1) + "Test"] = m.group(1) + "Spec"
            problems.append(f"{f}: a Groovy test class is named *Spec")

    methods = []
    for f in files:
        if "/src/test/" in f and f.endswith(".java"):
            for old in JAVA_TEST_METHOD.findall(Path(f).read_text()):
                methods.append((f, old))
                if (Path(f).name, old) not in METHODS:
                    problems.append(f"{f}: method {old} is a testX name with no entry in METHODS")
                else:
                    problems.append(f"{f}: method {old} is a testX name; use a sentence")

    if not rename:
        for p in problems:
            print(f"::error title=check-test-names::{p}")
        if problems:
            sys.exit(1)
        print("check-test-names: every Groovy test class is a *Spec and no Java test method is named testX")
        return

    for f in files:
        m = GROOVY_TEST_CLASS.match(f)
        if m and (m.group(1) + "Test") in groovy:
            subprocess.run(["git", "mv", f, f[: -len("Test.groovy")] + "Spec.groovy"], check=True)
    if groovy:
        pattern = re.compile(r"\b(" + "|".join(map(re.escape, groovy)) + r")\b")
        for f in tracked():
            if f.endswith(".groovy"):
                p = Path(f)
                text = p.read_text()
                new = pattern.sub(lambda m: groovy[m.group(1)], text)
                if new != text:
                    p.write_text(new)
    for f, old in methods:
        new = METHODS.get((Path(f).name, old))
        if new:
            p = Path(f)
            p.write_text(re.sub(r"\b" + old + r"\b", new, p.read_text()))
    print(f"check-test-names: renamed {len(groovy)} Groovy test classes and {sum(1 for f, o in methods if (Path(f).name, o) in METHODS)} Java methods")


main()

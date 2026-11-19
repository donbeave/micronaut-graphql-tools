#!/usr/bin/env python3
"""Keeps the master check equal to the pull request check (see MAINTAINING.md, "One gate").

Reads .github/workflows/*.yml as text (no YAML library: the files keep the layout this script relies on) and fails when:
  1. a job of gradle.yml other than `required` is missing from the `needs:` of `required`, or `needs:` names a job that does not exist;
  2. a job of gradle.yml has an `if:` (only `required` has one, `always()`): a conditional job can be skipped on a pull request and run on master;
  3. gradle.yml does not run on pull_request, push and merge_group, or the pull_request branches differ from the push branches;
  4. another workflow runs on a push to a branch (tags are fine): a check that only master pushes run is red on master and invisible on the pull request.
"""
import re
import sys
from pathlib import Path

workflows = Path(__file__).resolve().parent.parent / "workflows"
problems = []


def on_block(lines):
    start = next((i for i, l in enumerate(lines) if re.match(r"^on:\s*$", l)), None)
    if start is None:
        return []
    block = []
    for l in lines[start + 1:]:
        if l.strip() and not l.startswith(" "):
            break
        block.append(l)
    return block


def branches(block, event):
    """The branch patterns under `event:` of the `on:` block, or None when the event has no branches key."""
    for i, l in enumerate(block):
        if re.match(rf"^  {event}:\s*$", l):
            found, in_branches = None, False
            for m in block[i + 1:]:
                if re.match(r"^  \S", m):
                    break
                if re.match(r"^    branches:\s*$", m):
                    found, in_branches = [], True
                elif in_branches and (item := re.match(r"^      - (.+?)\s*$", m)):
                    found.append(item.group(1))
                elif re.match(r"^    \S", m):
                    in_branches = False
            return found
    return None


def runs_on_branch_push(lines):
    """True when the workflow has a push trigger that is not limited to tags, in any spelling:
    `on: push`, `on: [push, ...]`, `push:` with no filter, or `push:` with branches, branches-ignore or paths."""
    inline = next((m for l in lines if (m := re.match(r"^on:\s*(\S.*?)\s*(#.*)?$", l))), None)
    if inline:
        return re.search(r"\bpush\b", inline.group(1)) is not None
    block = on_block(lines)
    for i, l in enumerate(block):
        if re.match(r"^  push:", l):
            body = []
            for m in block[i + 1:]:
                if re.match(r"^  \S", m):
                    break
                body.append(m)
            has_tags = any(re.match(r"^    tags:", m) for m in body)
            has_branches = any(re.match(r"^    branches(-ignore)?:", m) for m in body)
            return not has_tags or has_branches
    return False


gradle = (workflows / "gradle.yml").read_text().splitlines()
jobs_at = next(i for i, l in enumerate(gradle) if l == "jobs:")
jobs, current = {}, None
for l in gradle[jobs_at + 1:]:
    if m := re.match(r"^  ([A-Za-z0-9_-]+):\s*$", l):
        current = m.group(1)
        jobs[current] = []
    elif current:
        jobs[current].append(l)

required = jobs.get("required")
if required is None:
    problems.append("gradle.yml has no `required` job")
else:
    needs_line = next((l for l in required if re.match(r"^    needs:\s*\[", l)), None)
    needs = [n.strip() for n in needs_line.split("[", 1)[1].rsplit("]", 1)[0].split(",") if n.strip()] if needs_line else []
    for job in jobs:
        if job != "required" and job not in needs:
            problems.append(f"job `{job}` is not in the `needs:` of `required`: it runs on every push and pull request but blocks no merge")
    for need in needs:
        if need not in jobs:
            problems.append(f"`needs:` of `required` names `{need}`, which is not a job of gradle.yml")
for name, body in jobs.items():
    if name != "required" and any(re.match(r"^    if:", l) for l in body):
        problems.append(f"job `{name}` has an `if:`: a gated job must run on every event, so remove the condition")

block = on_block(gradle)
for event in ("push", "pull_request", "merge_group"):
    if not any(re.match(rf"^  {event}:", l) for l in block):
        problems.append(f"gradle.yml does not run on `{event}`")
push, pr = branches(block, "push"), branches(block, "pull_request")
if push != pr:
    problems.append(f"gradle.yml: `push` branches {push} differ from `pull_request` branches {pr}")

for path in sorted(workflows.glob("*.yml")):
    if path.name == "gradle.yml":
        continue
    if runs_on_branch_push(path.read_text().splitlines()):
        problems.append(f"{path.name} runs on a push to a branch: only gradle.yml may, so that the pull request runs the same checks")

if problems:
    for p in problems:
        print(f"::error title=check-gate::{p}")
    sys.exit(1)
print(f"check-gate: {len(jobs) - 1} gated jobs, push, pull_request and merge_group agree")

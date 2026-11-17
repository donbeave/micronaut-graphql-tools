# Maintaining Micronaut GraphQL Tools

This is not an official Micronaut module yet. This file describes how this repository is run today.

## Repository rules

- `master` is protected by a ruleset. Direct pushes, force pushes and deletion are not allowed, and history is linear.
- Every change is a pull request. Only **squash merge** is enabled (merge commits and rebase merges are off).
- The only required status check is `required checks`, the gate job in `.github/workflows/gradle.yml`. It is green only when every job it lists under `needs:` succeeded. A new gated job goes into that `needs:` list, never into the ruleset.
- There is no admin bypass. A red, cancelled, pending or missing check blocks the merge. Review conversations must be resolved.
- `.github/workflows/native.yml` (Native Image CI) runs on every pull request too, but it is not part of the gate yet.

## Triage incoming issues

Issues and pull requests are tracked on the maintainers' "Roadmap to 1.0" project board. Labels in use:

- Kind: `type: bug`, `type: improvement`, `type: enhancement`, `type: docs`, `type: dependency-upgrade`, `type: breaking`, `type: task`, `type: decision`, `type: epic`.
- Priority: `priority: high`, `priority: medium`, `priority: low`.
- Size: `size: S`, `size: M`, `size: L`.
- Area: `relates-to: <area>` (for example `relates-to: build`, `relates-to: docs`, `relates-to: performance`).
- Workflow: `owner: maintainer`, `status: needs-decision`, `source: review`.
- Community: `help wanted`, `good first issue`, `question`, `duplicate`, `invalid`, `wontfix`.

The `type: *` labels also feed the generated release notes (`.github/release.yml`). New labels are created in the GitHub UI.

## Review pull requests

A pull request is ready to merge when:

- `required checks` is green on the current head commit.
- Every commit is signed off under the [Developer Certificate of Origin](https://developercertificate.org/) (`git commit -s`, a `Signed-off-by` line).
- The title follows the conventional commit style (`feat:`, `fix:`, `docs:`, `build:`, `ci:`, `perf:`, `test:`, `chore:`); it becomes the squash commit message.
- The code is of a minimum quality, uses the Micronaut APIs correctly, contains tests and updates the documentation.
- Linked issues are closed with closing keywords (`Closes #123`).
- No consumer, company or application names appear in code, docs, commit messages or pull request text.

Merge with "Squash and merge" only.

## Dependency upgrades

Renovate is configured in `.github/renovate.json`. Review its pull requests like any other: the same green `required checks` rule applies.

## Releases

Nothing is published: there are no release, snapshot or Maven Central workflows, and no version is on any repository. Releases and tags are cut by the maintainers by hand. To build a version locally, see "Artifacts and versions" in [README.md](README.md).

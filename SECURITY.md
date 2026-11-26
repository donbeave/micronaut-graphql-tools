# Security Policy

This project publishes milestone and release candidate versions to the GitHub Packages registry of this repository only; nothing is published to Maven Central. Security fixes go to `master` until the `1.0.0-RC1` tag. After it, they go to `1.0.x` (Micronaut 4) and are ported to `2.0.x` (Micronaut 5); see the branch model in `MAINTAINING.md`.

## Reporting a Vulnerability

Please do not report a suspected vulnerability in a public issue. Use GitHub private vulnerability reporting instead: open the [Security tab](https://github.com/donbeave/micronaut-graphql-tools/security) of this repository and choose "Report a vulnerability" (direct link: [new advisory](https://github.com/donbeave/micronaut-graphql-tools/security/advisories/new)).

The report stays private between you and the maintainers until a fix is ready.

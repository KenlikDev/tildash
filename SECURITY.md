# Security Policy

## Scope

This policy covers the Tildash repository, its source code, build configuration, GitHub Actions workflows, and published application/backend artifacts.

## Reporting a vulnerability

Do not disclose an unverified security vulnerability in a public issue, pull request, or discussion.

When private vulnerability reporting is enabled for this repository, use the repository Security advisories interface to report the issue.

The report should include:

- affected component or file;
- reproducible steps or proof of concept;
- affected versions or commits;
- security impact;
- any known mitigation.

Please avoid including real credentials, access tokens, personal data, or other secrets in the report.

## Handling

Security findings are treated as actionable engineering work.

A report should be reproduced and its impact assessed before a fix is merged. Security fixes must preserve existing tests and verification rather than suppressing the finding.

Public disclosure timing is decided after a fix or mitigation is available and affected users can be notified where applicable.

## Automated checks

The repository runs:

- GitHub Dependency Review on pull requests;
- CodeQL analysis for Kotlin/JVM code;
- repository dependency-update proposals through Dependabot.

These checks provide detection and visibility. They do not replace secure design, code review, or manual investigation.

## Secret protection

Secret scanning and push protection are repository security controls. Their enabled state is managed in GitHub repository settings and must not be inferred from this document.

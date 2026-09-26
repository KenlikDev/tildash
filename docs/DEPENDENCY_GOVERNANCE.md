# Dependency Governance

## Source of truth

Project dependency and plugin versions are centralized in `gradle/libs.versions.toml` whenever the Gradle build exposes them through the version catalog.

A version must not be copied into multiple build files without an explicit technical reason.

The Gradle Wrapper version remains authoritative in `gradle/wrapper/gradle-wrapper.properties`.

Settings-level plugins that cannot safely consume the version catalog remain pinned in `settings.gradle.kts` until a verified centralization mechanism is available. Such a version must still have a single source of truth.

## Automated updates

Dependabot monitors:

- Gradle dependencies and plugins from the root Gradle configuration;
- GitHub Actions used by repository workflows.

Dependabot targets `ai/integration` so automated update proposals enter the same protected integration boundary as normal task changes. They must pass the repository's required CI checks before they can be integrated.

Dependabot is intentionally scheduled weekly to keep update volume predictable while still detecting routine drift.

Minor and patch Gradle updates are grouped together. GitHub Actions updates are grouped together. Major framework upgrades remain individually reviewable so compatibility impact is visible.

## Framework upgrade policy

A major upgrade of Kotlin, Compose Multiplatform, Android Gradle Plugin, Spring Boot, Gradle, or another foundational tool requires explicit compatibility verification.

The upgrade must consider:

- supported JDK/toolchain versions;
- affected KMP targets;
- generated or deprecated APIs;
- build configuration changes;
- test and lint behavior;
- CI runner requirements.

A framework upgrade is not accepted solely because Dependabot or the build tool reports a newer version.

## Update PR verification

Dependency update pull requests use the same branch and integration rules as normal changes.

Before integration:

- inspect the proposed version changes;
- review release and compatibility notes for major changes;
- run focused checks for affected components;
- run the broader repository CI;
- verify that no dependency was added without a concrete need.

Security updates remain actionable and must not be suppressed merely to keep CI green.

## Deliberate pins

When a dependency is intentionally held below the newest available release, record the reason in the relevant ADR or dependency documentation and include the expected removal condition where practical.

Do not create artificial ignore rules merely to silence a legitimate update.

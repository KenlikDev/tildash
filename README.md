# Tildash

Tildash is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM), and a Spring Boot backend.

## Project structure

- `/app/iosApp` contains the native iOS application entry point.
- `/app/shared` contains shared Compose Multiplatform application code and platform-specific implementations.
- `/core` contains cross-platform domain and shared core code.
- `/server` contains the Spring Boot backend.

## Running the applications

Use IntelliJ IDEA run configurations or the Gradle Wrapper.

- Android: `./gradlew :app:androidApp:assembleDebug`
- Desktop: `./gradlew :app:desktopApp:run`
- Web/Wasm: `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`
- Web/JS compatibility target: `./gradlew :app:webApp:jsBrowserDevelopmentRun`
- Server: `./gradlew :server:bootRun`

The iOS application is opened from `/app/iosApp` in Xcode.

## Running tests

- Android host tests: `./gradlew :app:shared:testAndroidHostTest`
- Desktop tests: `./gradlew :app:shared:jvmTest`
- Server tests: `./gradlew :server:test`
- Web/Wasm tests: `./gradlew :app:shared:wasmJsTest`
- Web/JS tests: `./gradlew :app:shared:jsTest`
- iOS simulator tests: `./gradlew :app:shared:iosSimulatorArm64Test`

## Engineering documentation

- [Architecture](docs/ARCHITECTURE.md) — current system boundaries and dependency direction.
- [Engineering Policies](docs/ENGINEERING_POLICIES.md) — versioning, API compatibility, migrations, deprecation, and documentation policy.
- [Architecture Decision Records](docs/adr/README.md) — durable architectural decisions.

The canonical AI and engineering rules are maintained under `.ai/`. Do not create parallel instruction systems.

Developer-facing documentation and source code are written in professional English.

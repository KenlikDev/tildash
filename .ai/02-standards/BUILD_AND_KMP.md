# Build, Kotlin, KMP, and JVM Standard

Gradle is the canonical build system. Use Kotlin DSL and the repository Gradle Wrapper.

Centralize dependency versions where practical. Do not use dynamic production versions or unverified snapshots.

Kotlin Multiplatform is the client architecture for:
- Android
- iOS
- Desktop/JVM
- Web

Compose Multiplatform is the UI layer. Web is part of the architecture from the beginning.

Keep business rules in shared/common code. Platform-specific implementations are for genuine platform concerns.

Treat the JDK/toolchain version and JVM bytecode target as separate decisions.

The current Gradle daemon configuration uses JDK 21. The generated Android KMP `JVM_11` override is obsolete for this project and is being aligned to JVM 17 after explicit verification. Backend Java uses its own toolchain policy.

Remove wizard-generated sample code once its replacement is understood. Do not keep placeholder greetings, sample buttons, unused platform APIs, or demo tests as product code merely because the IDE generated them.

After build configuration changes, run the applicable compile/test tasks for every affected target.

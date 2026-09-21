# Spring Initializr Handoff

Create the bootstrap project separately from the Tildash repository.

Recommended parameters:
- Project: Gradle - Kotlin
- Language: Kotlin
- Spring Boot: latest stable; currently verified as 4.1.1
- Group: `com.kenlikdev.tildash`
- Artifact: `tildash-server`
- Name: `Tildash Server`
- Package: `com.kenlikdev.tildash.server`
- Packaging: Jar
- Java: 21
- Dependencies: Spring Web, Validation, Actuator

Do not add Spring Security, database, messaging, cloud, or AI starters at this bootstrap stage unless the task explicitly requires them.

After generation:
1. keep the project outside the Tildash repository;
2. run the generated test task without modification;
3. record the generated Kotlin, Gradle, Spring Boot, and JDK versions;
4. provide the complete generated project/ZIP for inspection.

The integration agent adapts the bootstrap into Tildash; it does not replace the repository root.

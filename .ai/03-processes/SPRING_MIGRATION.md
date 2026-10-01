# Spring Boot Migration Process

The Spring Initializr project is created outside the Tildash repository and is treated as reference input.

Do not overwrite the repository with the generated project.

Workflow:
1. inspect the current Tildash server module;
2. inspect every generated Initializr file;
3. verify Spring Boot/Kotlin/Gradle/JDK compatibility;
4. compare build/plugin/dependency configuration;
5. remove Ktor dependencies, imports, tests, and configuration from the backend `server` module only;
6. do not remove legitimate KMP client Ktor usage merely because the backend is migrating to Spring Boot;
7. adapt the Spring application into the existing `server` module;
8. preserve the repository's root Gradle and version-catalog design unless an ADR justifies a change;
9. write test-first verification for the Spring bootstrap;
10. add only intentionally required endpoints and configuration;
11. run server compile/test checks;
12. search the backend source and server dependency graph for leftover Ktor references;
13. inspect the final diff.

The generated Initializr wrapper and root build files are not copied blindly. The existing Tildash build remains the source of truth.

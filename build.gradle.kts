import org.gradle.api.attributes.Bundling
import org.gradle.api.tasks.JavaExec

plugins {
    // Load shared plugins once in the root project classloader.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSpring) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.springBoot) apply false
    alias(libs.plugins.springDependencyManagement) apply false
}

val ktlintCli by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
    }
}

dependencies {
    add(ktlintCli.name, libs.ktlintCli)
}

val ktlintSourceFiles =
    fileTree(rootDir) {
        include("**/*.kt")
        include("**/*.kts")
        exclude("**/build/**")
        exclude(".gradle/**")
        exclude(".git/**")
    }

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Run KtLint checks over Kotlin and Kotlin script sources."
    classpath(ktlintCli)
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir(rootDir)
    inputs.files(ktlintSourceFiles)

    doFirst {
        val sources =
            ktlintSourceFiles.files
                .sortedBy { it.invariantSeparatorsPath }
                .map { it.relativeTo(rootDir).invariantSeparatorsPath }

        args("--relative")
        args(sources)
    }
}

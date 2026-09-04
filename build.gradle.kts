plugins {
    java
}

group = "it.unibo.ise"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

sourceSets {
    main {
        resources {
            srcDir("src/main/asl")
            srcDir("src/main/prolog")
        }
    }
    test { resources { srcDir("src/test/asl") } }
}

dependencies {
    implementation(libs.jason.interpreter) {
        exclude(group = "net.sf.ingenias", module = "jade")
    }
    implementation(libs.tuprolog.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

fun Test.headlessMasConfig() {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    systemProperty(
        "java.util.logging.config.file",
        layout.projectDirectory.file("src/test/resources/logging-test.properties").asFile.absolutePath,
    )
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

tasks.test {
    headlessMasConfig()
    useJUnitPlatform { excludeTags("mas") }
}

val masTest by tasks.registering(Test::class) {
    description = "Runs MAS integration tests (mocked CNP negotiations and end-to-end simulations)."
    group = "verification"
    headlessMasConfig()
    useJUnitPlatform { includeTags("mas") }
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    forkEvery = 1
    maxParallelForks = 1
    shouldRunAfter(tasks.test)
}

tasks.check { dependsOn(masTest) }

tasks.register("printTestClasspath") {
    val classpath = sourceSets.test.get().runtimeClasspath
    doLast { println(classpath.asPath) }
}

file(projectDir).listFiles()
    ?.filter { it.extension == "mas2j" }
    ?.forEach { mas2jFile ->
    val taskName = "run" + mas2jFile.nameWithoutExtension.replaceFirstChar { it.uppercase() } + "Mas"
    tasks.register<JavaExec>(taskName) {
        description = "Runs the MAS defined in ${mas2jFile.name} (single JVM, Local infrastructure)."
        group = "run"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass = "jason.infra.local.RunLocalMAS"
        args(mas2jFile.path)
        standardInput = System.`in`
        javaLauncher = javaToolchains.launcherFor(java.toolchain)
    }
}
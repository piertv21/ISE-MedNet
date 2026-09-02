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
    main { resources { srcDir("src/main/asl") } }
    test { resources { srcDir("src/test/asl") } }
}

dependencies {
    implementation(libs.jason.interpreter)
    implementation(libs.jade)

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
    ?.filter { !it.readText().contains(Regex("""infrastructure\s*:\s*Jade""")) }
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

tasks.register<JavaExec>("runMainContainer") {
    description = "Starts the JADE main container (environment, control center, ambulances, patients)."
    group = "run"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "jason.infra.jade.RunJadeMAS"
    args("mednet_jade.mas2j", "-container-name", "main")
    standardInput = System.`in`
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
}

tasks.register<JavaExec>("runHospitalContainer") {
    description = "Starts one peripheral JADE container with all the agents of a hospital (-Phospital=h1|h2|h3)."
    group = "run"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "jason.infra.jade.RunJadeMAS"
    val h = (project.findProperty("hospital") ?: "h1").toString()
    if (h !in listOf("h1", "h2", "h3")) {
        throw GradleException("Unknown hospital '$h' (expected h1, h2 or h3)")
    }
    args(
        "mednet_jade.mas2j",
        "-container",
        "-host", "localhost",
        "-port", "1099",
        "-container-name", "container_$h",
    )
    standardInput = System.`in`
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
}

tasks.register<JavaExec>("runDistributed") {
    description = "Runs the whole MAS distributed over 4 JVM processes (main container + 3 hospital containers)."
    group = "run"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "mednet.launcher.DistributedLauncher"
    standardInput = System.`in`
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
}
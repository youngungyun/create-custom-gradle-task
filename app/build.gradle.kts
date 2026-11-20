plugins {
    application
    id("dev.lab.toolkit")
}

version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

toolkit {
    greeting = "안녕하세요"
    recipient = "Gradle"
    basePackage = "dev.lab.app"
    checksumAlgorithm = "SHA-256"
}

application {
    mainClass = "dev.lab.app.Main"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named<JavaExec>("run") {
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
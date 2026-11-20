plugins {
    `java-gradle-plugin`
}

group = "dev.lab"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// 기능 테스트(TestKit)용 소스셋
val functionalTest by sourceSets.creating

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(gradleApi())

    "functionalTestImplementation"(platform("org.junit:junit-bom:5.13.4"))
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter")
    "functionalTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("toolkit") {
            id = "dev.lab.toolkit"
            implementationClass = "dev.lab.gradle.ToolkitPlugin"
        }
    }
    testSourceSets(functionalTest)
}

var functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "TestKit 기반 기능 테스트"
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform()
}

tasks.check {
    dependsOn(functionalTestTask)
}
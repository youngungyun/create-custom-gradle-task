# Java 25로 만드는 Gradle 커스텀 Task 커리큘럼

Nov 16, 2026 · @Yun

## 개요

8주 과정으로, Java 25 클래스만으로 실제 동작하는 Gradle 플러그인(`dev.lab.toolkit`)과 커스텀 Task 5종을 만들고 테스트까지 작성합니다. 매주 하나의 Task가 늘어나며, 마지막 주에는 단위·기능 테스트로 전체를 검증합니다.

**대상**: Java는 익숙하지만 Gradle 빌드 로직은 `build.gradle.kts` 몇 줄만 다뤄 본 개발자.

**환경**

| 항목 | 버전 | 비고 |
| --- | --- | --- |
| JDK | 25 (LTS) | Gradle 데몬도 JDK 25로 실행 |
| Gradle | 9.1.0 이상 | Java 25를 공식 지원하는 첫 버전 ([릴리스 노트](https://docs.gradle.org/9.1.0/release-notes.html)) |
| 빌드 스크립트 | Kotlin DSL | Task 구현은 전부 Java |
| 테스트 | JUnit 5.13 + Gradle TestKit |  |

**주차별 산출물**

| 주차 | 주제 | 만드는 것 | 핵심 개념 |
| --- | --- | --- | --- |
| 1 | 프로젝트 골격 | `build-logic` 포함 빌드 | `java-gradle-plugin`, Toolchain, Configuration Cache |
| 2 | 첫 Task | `HelloTask` | `DefaultTask`, `@TaskAction` |
| 3 | 입력·출력 | `GreetingTask` | `Property`, `@Input`/`@OutputFile`, `@Option`, 빌드 캐시 |
| 4 | 플러그인·Extension | `ToolkitPlugin`, `ToolkitExtension` | `register`(Lazy 등록), convention |
| 5 | 소스 생성 | `BuildInfoTask`, `GitCommitValueSource` | 서비스 주입, `ValueSource`, Provider 기반 의존성 |
| 6 | 증분 처리 | `ChecksumTask` | `@Incremental`, `InputChanges` |
| 7 | 병렬 처리 | `LineCountTask`, `LineCountWorkAction` | Worker API |
| 8 | 테스트 | 단위·기능 테스트 | `ProjectBuilder`, `GradleRunner` |

**최종 프로젝트 구조**

```text
gradle-tasks-lab/
├── settings.gradle.kts
├── gradle.properties
├── app/
│   ├── build.gradle.kts
│   └── src/main/java/dev/lab/app/Main.java
└── build-logic/
    ├── settings.gradle.kts
    ├── build.gradle.kts
    └── src/
        ├── main/java/dev/lab/gradle/
        │   ├── ToolkitPlugin.java
        │   ├── ToolkitExtension.java
        │   ├── GitCommitValueSource.java
        │   └── tasks/
        │       ├── HelloTask.java
        │       ├── GreetingTask.java
        │       ├── BuildInfoTask.java
        │       ├── ChecksumTask.java
        │       ├── LineCountTask.java
        │       └── LineCountWorkAction.java
        ├── test/java/dev/lab/gradle/ToolkitPluginTest.java
        └── functionalTest/java/dev/lab/gradle/ToolkitPluginFunctionalTest.java
```

전체 코드는 Gradle 8.14 + JDK 21 환경에서 버전 숫자만 바꿔 빌드·실행을 확인했습니다(Java 25 전용 문법인 `Main.java`의 인스턴스 `main`과 테스트 실행은 제외).

## 1주차 — 프로젝트 골격

목표: 플러그인 코드를 담을 `build-logic`(포함 빌드)과 이를 사용할 `app` 모듈을 나눠 구성합니다. `buildSrc` 대신 포함 빌드를 쓰면 나중에 그대로 Plugin Portal에 배포할 수 있는 독립 프로젝트가 됩니다.

학습 포인트

- `pluginManagement { includeBuild(...) }`로 로컬 플러그인을 `plugins {}` 블록에서 ID로 불러옴
- `java-gradle-plugin`이 Gradle API 의존성, 플러그인 디스크립터, TestKit 클래스패스를 자동 구성
- Toolchain 25: 플러그인 바이트코드가 Java 25(클래스 파일 69)이므로 **Gradle 데몬도 JDK 25로 돌아야** 플러그인을 로드할 수 있음

준비 명령

```bash
# JDK 25가 JAVA_HOME인 상태에서
mkdir gradle-tasks-lab && cd gradle-tasks-lab

# 1) settings 파일부터 만든다 (Gradle 9는 settings 파일이 없는 폴더에서 wrapper 실행을 거부)
#    내용은 아래 settings.gradle.kts 전문을 그대로 붙여 넣는다. build-logic, app 폴더도 함께 생성.

# 2) Wrapper 생성
gradle wrapper --gradle-version 9.1.0   # 또는 최신 9.x

# 3) 데몬 JVM을 25로 고정 (gradle/gradle-daemon-jvm.properties 생성, settings에 foojay 플러그인 필요)
./gradlew updateDaemonJvm --jvm-version=25        # Windows: .\gradlew.bat updateDaemonJvm --jvm-version=25
```

`settings.gradle.kts`

```kotlin
pluginManagement {
    includeBuild("build-logic")   // 우리가 만든 플러그인을 이 빌드에 공급
}

plugins {
    // JDK 자동 다운로드 저장소. updateDaemonJvm, toolchain 자동 설치에 필요 (Gradle 9는 1.0.0 이상)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "gradle-tasks-lab"
include("app")
```

`gradle.properties`

```properties
org.gradle.configuration-cache=true
org.gradle.caching=true
org.gradle.parallel=true
```

`.gitignore`

5주차의 `GitCommitValueSource`가 커밋 해시를 읽으므로, 1주차에 미리 Git 저장소를 만들어 둡니다. 빌드 산출물과 IDE 파일은 제외하고, Wrapper(`gradlew`, `gradlew.bat`, `gradle/wrapper/`)와 `gradle/gradle-daemon-jvm.properties`는 **반드시 커밋**해야 다른 PC에서도 같은 Gradle·JDK로 빌드됩니다.

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# 포함 빌드(build-logic)의 출력도 제외
**/build/

# Kotlin DSL 컴파일 캐시
.kotlin/

# IntelliJ IDEA
.idea/
*.iml
*.ipr
*.iws
out/

# Eclipse / VS Code
.classpath
.project
.settings/
bin/
.vscode/

# OS
.DS_Store
Thumbs.db

# 로그
*.log
```

```bash
git init
git add .
git commit -m "1주차: 프로젝트 골격"
```

`build-logic/settings.gradle.kts`

```kotlin
rootProject.name = "build-logic"
```

`build-logic/build.gradle.kts`

```kotlin
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

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "TestKit 기반 기능 테스트"
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.test { useJUnitPlatform() }
tasks.check { dependsOn(functionalTestTask) }
```

실습 과제: `./gradlew -p build-logic tasks`를 실행해 `pluginDescriptors`, `validatePlugins` Task가 생긴 것을 확인하세요. `validatePlugins`는 이후 주차에서 어노테이션 누락을 잡아 주는 안전망입니다.

**정답**

```text
> .\gradlew.bat -p build-logic tasks

Plugin development tasks
------------------------
pluginDescriptors - Generates plugin descriptors from plugin declarations.
pluginUnderTestMetadata - Generates the metadata for plugin functional tests.
validatePlugins - Validates the plugin by checking parameter annotations on task and artifact transform types etc.
```

| Task | 하는 일 |
| --- | --- |
| `pluginDescriptors` | `gradlePlugin { }` 선언으로 `build/pluginDescriptors/dev.lab.toolkit.properties` 생성. 내용은 `implementation-class=dev.lab.gradle.ToolkitPlugin` 한 줄이고, jar의 `META-INF/gradle-plugins/`에 들어가 플러그인 ID를 클래스와 연결 |
| `pluginUnderTestMetadata` | 8주차 TestKit의 `withPluginClasspath()`가 읽을 클래스패스 목록 생성 |
| `validatePlugins` | Task 클래스의 어노테이션 누락·충돌 검사. `check`에 포함되어 있어 테스트와 함께 실행됨 |

## 2주차 — 첫 Task: HelloTask

목표: `DefaultTask`를 상속한 가장 작은 Task를 만들고, 최소 플러그인으로 `app`에 등록해 실행합니다.

학습 포인트

- Task 클래스는 `abstract`로 선언: Gradle이 런타임에 하위 클래스를 생성(decorate)해 프로퍼티·서비스를 주입
- `@TaskAction` 메서드가 실행 단계에서 호출됨 (설정 단계 ↔ 실행 단계 구분)
- 출력은 `System.out` 대신 `getLogger()` — 로그 레벨(`--quiet`, `--info`)을 따름
- 입력·출력이 없는 Task는 항상 실행됨(UP-TO-DATE 불가)

`build-logic/src/main/java/dev/lab/gradle/tasks/HelloTask.java`

```java
package dev.lab.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

/** 1단계: 가장 단순한 Java Task. 입력도 출력도 없다. */
public abstract class HelloTask extends DefaultTask {

    @TaskAction
    public void sayHello() {
        getLogger().lifecycle("Hello from a Java task! (task path = {})", getPath());
    }
}
```

이 주차의 임시 플러그인 (4주차에서 최종본으로 교체) — `build-logic/src/main/java/dev/lab/gradle/ToolkitPlugin.java`

```java
package dev.lab.gradle;

import dev.lab.gradle.tasks.HelloTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class ToolkitPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().register("hello", HelloTask.class, t -> {
            t.setGroup("toolkit");
            t.setDescription("Java 로 작성한 첫 번째 Task");
        });
    }
}
```

이 주차의 `app/build.gradle.kts`

```kotlin
plugins {
    id("dev.lab.toolkit")
}
```

실행

```bash
./gradlew :app:hello
# > Task :app:hello
# Hello from a Java task! (task path = :app:hello)
```

실습 과제

1. `./gradlew :app:hello`를 두 번 실행해 두 번째에도 실행되는 것을 확인(출력이 없으므로).
2. `register` 대신 `create`를 쓰면 무엇이 달라지는지 `--info` 로그로 비교(즉시 생성 vs 필요할 때 생성).

**정답**

1. 두 번 모두 `> Task :app:hello`가 출력되고 `UP-TO-DATE`가 붙지 않습니다. Gradle은 **선언된 출력이 있는 Task만** 최신 여부를 판단합니다. `HelloTask`는 출력이 없으므로 매번 실행됩니다(로그만 남기는 Task에는 이게 맞는 동작).
2. 설정 람다에 로그를 넣고 **다른 Task**(`help`)를 실행해 비교합니다.

```java
// register: Task가 필요할 때만 생성·설정 (Lazy)
project.getTasks().register("hello", HelloTask.class, t -> t.getLogger().lifecycle(">> hello 설정됨"));

// create: 빌드할 때마다 즉시 생성·설정 (Eager)
project.getTasks().create("hello", HelloTask.class, t -> t.getLogger().lifecycle(">> hello 설정됨"));
```

```text
> .\gradlew.bat :app:help --no-configuration-cache
register → ">> hello 설정됨" 이 출력되지 않음 (hello 는 실행 그래프에 없으므로 만들지 않음)
create   → ">> hello 설정됨" 이 출력됨      (어떤 Task를 실행하든 항상 생성)
```

Task가 수백 개인 빌드에서는 `create`가 설정 시간을 크게 늘립니다. `--no-configuration-cache`를 붙이는 이유는, 설정 캐시가 재사용되면 설정 단계 자체가 건너뛰어져 두 경우 모두 로그가 안 나오기 때문입니다.

## 3주차 — 입력·출력과 Lazy Property: GreetingTask

목표: 입력과 출력을 선언해 Gradle이 "이미 최신(UP-TO-DATE)"을 판단하고 빌드 캐시에서 결과를 복원(FROM-CACHE)하게 만듭니다.

학습 포인트

- 필드 대신 `abstract Property<T> getXxx()` 게터만 선언 → Gradle이 구현체를 주입(Managed Property)
- `@Input`, `@OutputFile` 등 어노테이션이 업투데이트 검사와 캐시 키를 결정
- `@CacheableTask`: 결과를 빌드 캐시에 저장 가능하다고 선언
- `convention()`은 기본값, `set()`은 명시값 — 명시값이 항상 우선
- `@Option`으로 명령줄 인자(`--recipient=...`)를 프로퍼티에 연결

`build-logic/src/main/java/dev/lab/gradle/tasks/GreetingTask.java`

```java
package dev.lab.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.IOException;
import java.nio.file.Files;

/** 2단계: Lazy Property 입력 + 파일 출력 → UP-TO-DATE / 빌드 캐시 지원. */
@CacheableTask
public abstract class GreetingTask extends DefaultTask {

    @Input
    public abstract Property<String> getGreeting();

    @Input
    @Option(option = "recipient", description = "인사 받을 대상")
    public abstract Property<String> getRecipient();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    public GreetingTask() {
        getGreeting().convention("Hello");
    }

    @TaskAction
    public void greet() throws IOException {
        String message = "%s, %s!".formatted(getGreeting().get(), getRecipient().get());
        var out = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(out.getParent());
        Files.writeString(out, message);
        getLogger().lifecycle(message);
    }
}
```

임시 등록 (플러그인의 `apply` 안에 추가)

```java
project.getTasks().register("greet", GreetingTask.class, t -> {
    t.setGroup("toolkit");
    t.getRecipient().convention(project.getName());
    t.getOutputFile().set(project.getLayout().getBuildDirectory().file("toolkit/greeting.txt"));
});
```

위 코드는 2주차에 만든 `build-logic/src/main/java/dev/lab/gradle/ToolkitPlugin.java`의 `apply()` 메서드 안, `hello` 등록 코드 바로 아래에 넣습니다. `GreetingTask` import도 함께 추가해야 합니다. 3주차 시점의 파일 전체는 다음과 같습니다.

```java
package dev.lab.gradle;

import dev.lab.gradle.tasks.GreetingTask;   // ← 추가
import dev.lab.gradle.tasks.HelloTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class ToolkitPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().register("hello", HelloTask.class, t -> {
            t.setGroup("toolkit");
            t.setDescription("Java 로 작성한 첫 번째 Task");
        });

        // ← 3주차 추가
        project.getTasks().register("greet", GreetingTask.class, t -> {
            t.setGroup("toolkit");
            t.getRecipient().convention(project.getName());
            t.getOutputFile().set(project.getLayout().getBuildDirectory().file("toolkit/greeting.txt"));
        });
    }
}
```

`app/build.gradle.kts`는 2주차 그대로(`plugins { id("dev.lab.toolkit") }`) 두면 됩니다. 4주차에서 이 파일을 최종본으로 통째 교체합니다.

실행과 기대 결과

```bash
./gradlew :app:greet                    # > Task :app:greet          Hello, app!
./gradlew :app:greet                    # > Task :app:greet UP-TO-DATE
./gradlew :app:greet --recipient=CLI    # 입력이 바뀌었으므로 재실행 → Hello, CLI!
./gradlew :app:greet                    # 이전 결과가 캐시에 있으므로 FROM-CACHE
```

실습 과제

1. `@Input`을 지우고 `./gradlew -p build-logic validatePlugins`를 실행해 어떤 경고가 나오는지 확인.
2. `build/toolkit/greeting.txt`를 수동으로 수정한 뒤 다시 실행 → 출력 변경도 재실행 조건임을 확인.

**정답**

1. `validatePlugins`가 **실패**합니다(경고가 아니라 오류).

```text
- Error: Type 'dev.lab.gradle.tasks.GreetingTask' property 'greeting' is missing an input or output annotation.
  Reason: A property without annotation isn't considered during up-to-date checking.
    1. Add an input or output annotation.
```

어노테이션이 없으면 `greeting` 값이 바뀌어도 Gradle은 입력이 바뀜 줄 모르고 `UP-TO-DATE`로 건너뛰어 잘못된(오래된) 파일을 남깁니다. 값이 결과에 영향을 주지 않는 프로퍼티라면 `@Internal`을 붙여 명시적으로 제외합니다.

2. Task가 **다시 실행**되고 파일은 원래 내용(`안녕하세요, Gradle!`)으로 복구됩니다. `--info`로 보면 이유가 나옵니다.

```text
> .\gradlew.bat :app:greet --info
Output property 'outputFile' file ...\app\build\toolkit\greeting.txt has changed.
Gradle does not know how file 'build\toolkit\greeting.txt' was created ...
> Task :app:greet
```

Gradle은 입력뿐 아니라 출력 파일의 파일 지문도 기록해 두므로, 누군가 출력을 건드리면 최신이 아니라고 판단합니다. 또 출처를 모르는 파일이 출력 자리에 있으므로 캐시 복원(FROM-CACHE) 대신 실제 실행을 선택합니다.

## 4주차 — 플러그인과 Extension

목표: 사용자가 `toolkit { ... }` 블록으로 설정하고, 플러그인이 그 값을 각 Task에 Lazy하게 연결하도록 만듭니다. 아래 `ToolkitPlugin`은 **최종본**입니다. 5\~7주차 Task를 아직 만들지 않았다면 해당 블록(4\~7번 주석)을 주석 처리한 채 진행하고, 주차마다 풀어 주세요.

학습 포인트

- Extension도 `abstract` + `Property` 게터만 선언하면 Gradle이 인스턴스를 만듦 (`extensions.create`)
- `t.getGreeting().set(ext.getGreeting())`는 값이 아니라 **연결**을 복사 → 사용자가 나중에 값을 바꿔도 반영됨
- `plugins {}`는 `version = ...`보다 먼저 실행됨 → `project.getVersion()`을 즉시 읽으면 `unspecified`. 반드시 `providers.provider(...)`로 감싸기
- `pluginManager.withPlugin("java", ...)`: 다른 플러그인이 있을 때만 반응(적용 순서 무관)

`build-logic/src/main/java/dev/lab/gradle/ToolkitExtension.java`

```java
package dev.lab.gradle;

import org.gradle.api.provider.Property;

/** 4단계: build.gradle.kts 의 `toolkit { ... }` 블록. */
public abstract class ToolkitExtension {
    public abstract Property<String> getGreeting();
    public abstract Property<String> getRecipient();
    public abstract Property<String> getBasePackage();
    public abstract Property<String> getChecksumAlgorithm();
}
```

`build-logic/src/main/java/dev/lab/gradle/ToolkitPlugin.java` (최종본)

```java
package dev.lab.gradle;

import dev.lab.gradle.tasks.BuildInfoTask;
import dev.lab.gradle.tasks.ChecksumTask;
import dev.lab.gradle.tasks.GreetingTask;
import dev.lab.gradle.tasks.HelloTask;
import dev.lab.gradle.tasks.LineCountTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

/** 4단계: 모든 Task 를 등록하고 Extension 과 연결하는 플러그인. */
public class ToolkitPlugin implements Plugin<Project> {

    public static final String GROUP = "toolkit";

    @Override
    public void apply(Project project) {
        var layout = project.getLayout();
        var providers = project.getProviders();
        var tasks = project.getTasks();

        // 1) Extension + 기본값(convention)
        var ext = project.getExtensions().create("toolkit", ToolkitExtension.class);
        ext.getGreeting().convention("Hello");
        ext.getRecipient().convention(project.getName());
        ext.getBasePackage().convention("generated");
        ext.getChecksumAlgorithm().convention("SHA-256");

        // 2) 단순 Task
        tasks.register("hello", HelloTask.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Java 로 작성한 첫 번째 Task");
        });

        // 3) Lazy Property 연결
        tasks.register("greet", GreetingTask.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("인사말을 파일로 출력");
            t.getGreeting().set(ext.getGreeting());
            t.getRecipient().set(ext.getRecipient());
            t.getOutputFile().set(layout.getBuildDirectory().file("toolkit/greeting.txt"));
        });

        // 4) 소스 생성 + ValueSource
        Provider<String> gitCommit = providers.of(GitCommitValueSource.class, spec ->
                spec.getParameters().getRepoDir().set(project.getRootProject().getLayout().getProjectDirectory()));

        // plugins {} 블록은 version = "..." 보다 먼저 실행되므로 반드시 Lazy 하게 읽는다.
        Provider<String> version = providers.provider(() -> project.getVersion().toString());
        TaskProvider<BuildInfoTask> buildInfo = tasks.register("generateBuildInfo", BuildInfoTask.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("BuildInfo.java 생성");
            t.getPackageName().set(ext.getBasePackage());
            t.getAppVersion().convention(version);
            t.getGitCommit().set(gitCommit);
            t.getOutputDir().set(layout.getBuildDirectory().dir("generated/sources/toolkit/java"));
        });

        // 5) 증분 Task
        tasks.register("checksum", ChecksumTask.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("src/ 아래 모든 파일의 체크섬 계산 (증분)");
            t.getSources().from(layout.getProjectDirectory().dir("src"));
            t.getAlgorithm().set(ext.getChecksumAlgorithm());
            t.getOutputDir().set(layout.getBuildDirectory().dir("toolkit/checksums"));
        });

        // 6) Worker API Task
        tasks.register("countLines", LineCountTask.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Java 소스 줄 수 집계 (Worker API 병렬)");
            t.getSources().from(layout.getProjectDirectory().dir("src/main/java"));
            t.getOutputDir().set(layout.getBuildDirectory().dir("toolkit/line-count"));
        });

        // 7) java 플러그인이 있을 때만: 생성 소스를 main 소스셋에 연결
        //    Provider 로 연결하면 compileJava → generateBuildInfo 의존성이 자동 생성된다.
        project.getPluginManager().withPlugin("java", plugin -> {
            var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            sourceSets.named("main", main ->
                    main.getJava().srcDir(buildInfo.flatMap(BuildInfoTask::getOutputDir)));
        });
    }
}
```

실행

```bash
./gradlew :app:tasks --group toolkit
# Toolkit tasks
# checksum - src/ 아래 모든 파일의 체크섬 계산 (증분)
# countLines - Java 소스 줄 수 집계 (Worker API 병렬)
# generateBuildInfo - BuildInfo.java 생성
# greet - 인사말을 파일로 출력
# hello - Java 로 작성한 첫 번째 Task
```

실습 과제: `app/build.gradle.kts`에서 `toolkit { greeting = "안녕하세요" }`를 설정하고 `greet`를 실행. 이어서 `register`의 설정 람다 안에 `getLogger().lifecycle("configured")`를 넣고 `./gradlew :app:hello`를 실행해 `greet`의 람다는 호출되지 않음을 확인(Task Configuration Avoidance).

**정답**

`app/build.gradle.kts`

```kotlin
toolkit {
    greeting = "안녕하세요"
}
```

```text
> .\gradlew.bat :app:greet
> Task :app:greet
안녕하세요, app!          (recipient 는 convention = 프로젝트 이름)
```

`ToolkitPlugin`의 `greet` 등록 람다에 로그 추가:

```java
tasks.register("greet", GreetingTask.class, t -> {
    t.getLogger().lifecycle(">> greet 설정됨");
    t.setGroup(GROUP);
    // ... 기존 코드 그대로
});
```

```text
> .\gradlew.bat :app:hello --no-configuration-cache
> Task :app:hello
Hello from a Java task! (task path = :app:hello)
                       ← ">> greet 설정됨" 은 출력되지 않음

> .\gradlew.bat :app:greet --no-configuration-cache
>> greet 설정됨
> Task :app:greet
```

`register`로 등록한 Task는 실행 대상이거나 다른 Task가 의존할 때만 설정됩니다. 이것이 Task Configuration Avoidance입니다. 플러그인에서 `tasks.getByName(...)`, `tasks.withType(...).forEach(...)`처럼 즉시 조회하는 API를 쓰면 이 효과가 깨집니다. 대신 `named(...)`, `withType(...).configureEach(...)`를 쓰세요.

## 5주차 — 소스 생성과 ValueSource

목표: 빌드 버전과 Git 커밋을 담은 `BuildInfo.java`를 생성하고, `compileJava`가 자동으로 이 Task에 의존하게 만듭니다. 모든 코드는 Configuration Cache와 호환되어야 합니다.

학습 포인트

- Task 안에서 `getProject()` 금지 → 필요한 기능은 `@Inject` 서비스(`FileSystemOperations`, `ExecOperations`)로 주입
- 설정 단계에서 외부 프로세스를 호출할 땐 `ValueSource` — Configuration Cache가 값 변화를 추적해 캐시 무효화 여부를 판단
- `srcDir(taskProvider.flatMap(...))`: Provider가 어느 Task의 출력인지 알기 때문에 `dependsOn` 없이도 의존성이 연결됨
- 생성 코드에 빌드 시각을 넣지 않음 → 입력이 같으면 출력도 같아야 캐시가 동작(재현 가능한 빌드)

`build-logic/src/main/java/dev/lab/gradle/tasks/BuildInfoTask.java`

```javascript
package dev.lab.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;

/** 3단계: 소스 코드 생성 Task. 출력 디렉터리를 main 소스셋에 연결한다. */
@CacheableTask
public abstract class BuildInfoTask extends DefaultTask {

    @Input
    public abstract Property<String> getPackageName();

    @Input
    public abstract Property<String> getAppVersion();

    @Input
    public abstract Property<String> getGitCommit();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    /** Gradle 서비스 주입: Project 없이 파일 작업을 수행 (Configuration Cache 호환). */
    @Inject
    protected abstract FileSystemOperations getFs();

    @TaskAction
    public void generate() throws IOException {
        var root = getOutputDir().get().getAsFile();
        getFs().delete(spec -> spec.delete(root));   // 이전 산출물 정리

        String pkg = getPackageName().get();
        var dir = root.toPath().resolve(pkg.replace('.', '/'));
        Files.createDirectories(dir);

        String source = """
                package %s;

                /** 자동 생성 파일 – 직접 수정하지 마세요. */
                public final class BuildInfo {
                    public static final String VERSION = "%s";
                    public static final String GIT_COMMIT = "%s";

                    private BuildInfo() {}
                }
                """.formatted(pkg, escape(getAppVersion().get()), escape(getGitCommit().get()));

        Files.writeString(dir.resolve("BuildInfo.java"), source);
        getLogger().lifecycle("BuildInfo.java 생성 → {}", dir);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
```

`build-logic/src/main/java/dev/lab/gradle/GitCommitValueSource.java`

```java
package dev.lab.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 7단계: 설정 단계에서 외부 프로세스(git)를 안전하게 호출한다.
 * ValueSource 는 Configuration Cache 가 값의 변화를 추적할 수 있게 해 준다.
 */
public abstract class GitCommitValueSource
        implements ValueSource<String, GitCommitValueSource.Params> {

    public interface Params extends ValueSourceParameters {
        DirectoryProperty getRepoDir();
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    public String obtain() {
        var out = new ByteArrayOutputStream();
        try {
            ExecResult result = getExecOperations().exec(spec -> {
                spec.commandLine("git", "rev-parse", "--short", "HEAD");
                spec.setWorkingDir(getParameters().getRepoDir().get().getAsFile());
                spec.setStandardOutput(out);
                spec.setErrorOutput(OutputStream.nullOutputStream());
                spec.setIgnoreExitValue(true);
            });
            if (result.getExitValue() != 0) {
                return "unknown";
            }
            return out.toString(StandardCharsets.UTF_8).trim();
        } catch (Exception e) {   // git 미설치 등
            return "unknown";
        }
    }
}
```

`app/build.gradle.kts` (최종본)

```kotlin
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
```

`app/src/main/java/dev/lab/app/Main.java` — Java 25의 인스턴스 `main` 메서드와 `java.lang.IO`(JEP 512)를 사용

```java
package dev.lab.app;

// Java 25 (JEP 512): 인스턴스 main 메서드 + java.lang.IO
public class Main {
    void main() {
        IO.println("앱 버전 : " + BuildInfo.VERSION);
        IO.println("커밋    : " + BuildInfo.GIT_COMMIT);
    }
}
```

실행

```bash
git init && git add -A && git commit -m "init"   # 커밋 해시가 있어야 unknown 이 아님
./gradlew :app:run
# > Task :app:generateBuildInfo
# > Task :app:compileJava
# > Task :app:run
# 앱 버전 : 1.0.0
# 커밋    : bafaeb5
```

실습 과제: 새 커밋을 만든 뒤 `./gradlew :app:run`을 다시 실행하면 Configuration Cache는 재사용되는데도 `generateBuildInfo`가 재실행되는지 확인. `ValueSource` 대신 플러그인에서 `ProcessBuilder`를 직접 호출하면 어떤 오류가 나는지도 비교해 보세요.

**정답**

새 커밋 후 실행 결과 (실제 확인한 출력):

```text
> git commit -am "change"
> .\gradlew.bat :app:run
Reusing configuration cache.
> Task :app:generateBuildInfo          ← UP-TO-DATE 가 아니라 재실행
> Task :app:compileJava
> Task :app:run
앱 버전 : 1.0.0
커밋    : 3f9c2ab                     ← 새 커밋 해시
Configuration cache entry reused.
```

Task 입력으로 Lazy하게 연결된 `ValueSource`는 설정 캐시에 값이 저장되지 않고, 캐시를 불러올 때마다 다시 계산됩니다. 그래서 설정 단계를 건너뛰면서도(빠름) 최신 커밋을 반영합니다(정확함). 만약 플러그인이 설정 단계에서 `gitCommit.get()`을 호출해 값을 즉시 사용했다면, 그때는 값이 바뀔 때 설정 캐시 자체가 무효화됩니다.

`ProcessBuilder`를 직접 호출하는 잘못된 버전 (`ToolkitPlugin.apply` 안):

```java
// ❌ 설정 단계에서 외부 프로세스를 직접 실행
String commit;
try {
    Process p = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(project.getRootDir())
            .start();
    commit = new String(p.getInputStream().readAllBytes()).trim();
} catch (IOException e) {
    commit = "unknown";
}
String finalCommit = commit;
// ... t.getGitCommit().set(finalCommit);
```

```text
Configuration cache problems found in this build.
1 problem was found storing the configuration cache.
- Plugin 'dev.lab.toolkit': external process started 'git rev-parse --short HEAD'
```

설정 캐시가 켜져 있으면 빌드가 실패합니다. Gradle은 설정 단계의 임의 프로세스 실행 결과를 추적할 수 없어 캐시가 오래된 값을 재사용할 위험이 있기 때문입니다. `ValueSource`나 `providers.exec { }`로 바꾸면 해결됩니다.

## 6주차 — 증분 Task: ChecksumTask

목표: `src/` 아래 파일마다 체크섬 파일을 만들되, 두 번째 실행부터는 **추가·수정·삭제된 파일만** 처리합니다.

학습 포인트

- `@TaskAction` 메서드가 `InputChanges`를 받으면 증분 Task가 됨
- `@Incremental`을 붙인 파일 입력만 `getFileChanges()`로 변경 내역 조회 가능
- `isIncremental()`이 `false`인 경우(첫 실행, 비파일 입력 변경, 출력 손상)에는 모든 파일이 ADDED로 전달되고 Gradle이 출력 디렉터리를 비움
- `@PathSensitive(RELATIVE)` → `getNormalizedPath()`가 입력 루트 기준 상대 경로가 되고, 프로젝트를 다른 경로에 체크아웃해도 캐시 적중
- 주의: `@Incremental`과 `@SkipWhenEmpty`는 같은 프로퍼티에 함께 쓸 수 없음(검증 단계에서 "conflicting incremental annotations" 오류). 실제로 확인한 함정입니다.

`build-logic/src/main/java/dev/lab/gradle/tasks/ChecksumTask.java`

```java
package dev.lab.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileType;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 5단계: 증분(Incremental) Task. 바뀜 파일만 다시 처리한다. */
@CacheableTask
public abstract class ChecksumTask extends DefaultTask {

    @Incremental
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    public abstract ConfigurableFileCollection getSources();

    @Input
    public abstract Property<String> getAlgorithm();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    public ChecksumTask() {
        getAlgorithm().convention("SHA-256");
    }

    @TaskAction
    public void execute(InputChanges changes) {
        getLogger().lifecycle(changes.isIncremental()
                ? "증분 실행: 변경된 파일만 처리합니다."
                : "전체 실행: 모든 파일을 처리합니다.");

        String ext = getAlgorithm().get().toLowerCase().replace("-", "");
        int processed = 0;

        for (FileChange change : changes.getFileChanges(getSources())) {
            if (change.getFileType() == FileType.DIRECTORY) {
                continue;
            }
            File target = getOutputDir()
                    .file(change.getNormalizedPath() + "." + ext)
                    .get().getAsFile();

            switch (change.getChangeType()) {
                case REMOVED -> {
                    target.delete();
                    getLogger().info("삭제: {}", target);
                }
                case ADDED, MODIFIED -> writeChecksum(change.getFile(), target);
            }
            processed++;
        }
        getLogger().lifecycle("처리한 파일 수: {}", processed);
    }

    private void writeChecksum(File source, File target) {
        try {
            MessageDigest md = MessageDigest.getInstance(getAlgorithm().get());
            String hex = HexFormat.of().formatHex(md.digest(Files.readAllBytes(source.toPath())));
            Files.createDirectories(target.toPath().getParent());
            Files.writeString(target.toPath(), hex + "  " + source.getName() + System.lineSeparator());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("지원하지 않는 알고리즘: " + getAlgorithm().get(), e);
        }
    }
}
```

실행과 기대 결과 (실제 확인한 출력)

```bash
mkdir -p app/src/main/resources
echo a > app/src/main/resources/a.txt
echo b > app/src/main/resources/b.txt
./gradlew :app:checksum
# 전체 실행: 모든 파일을 처리합니다.
# 처리한 파일 수: 3          (Main.java, a.txt, b.txt)

echo c >> app/src/main/resources/a.txt
rm app/src/main/resources/b.txt
./gradlew :app:checksum
# 증분 실행: 변경된 파일만 처리합니다.
# 처리한 파일 수: 2          (a.txt 수정, b.txt.sha256 삭제)

./gradlew :app:checksum
# > Task :app:checksum UP-TO-DATE
```

실습 과제: `toolkit { checksumAlgorithm = "SHA-512" }`로 바꾼 뒤 실행해 보세요. 비파일 입력(`@Input`)이 바뀌었으므로 "전체 실행"이 나오고 이전 `.sha256` 파일은 사라져야 합니다.

**정답**

`app/build.gradle.kts`

```kotlin
toolkit {
    checksumAlgorithm = "SHA-512"
}
```

```text
> .\gradlew.bat :app:checksum
전체 실행: 모든 파일을 처리합니다.
처리한 파일 수: 2

> dir app\build\toolkit\checksums -Recurse -File | % Name
Main.java.sha512
a.txt.sha512                  ← .sha256 파일은 하나도 남아 있지 않음
```

증분 실행은 **파일 입력만** 바뀌었을 때 가능합니다. `@Input`(알고리즘)이 바뀌면 기존 결과를 재활용할 근거가 없으므로 Gradle이 `isIncremental() == false`로 호출하고, 그 전에 `@OutputDirectory`의 이전 내용을 지워 둡니다. 그래서 Task 코드가 `.sha256` 파일을 직접 지우지 않아도 출력이 깨끗합니다. 파일 수(2)는 6주차 실습에서 `b.txt`를 지운 상태 기준입니다.

## 7주차 — Worker API 병렬 처리: LineCountTask

목표: 파일마다 줄 수를 세는 작업을 Worker API로 병렬 실행하고, 모두 끝난 뒤 하나의 리포트로 집계합니다.

학습 포인트

- 작업 단위는 `WorkAction<P>`, 파라미터는 `WorkParameters`를 상속한 인터페이스(직렬화 가능한 값만)
- `noIsolation()` < `classLoaderIsolation()` < `processIsolation()` 순으로 격리 수준과 비용이 증가
- `queue.await()`로 워커 완료를 기다린 뒤 집계. 워커끼리 메모리를 공유하지 않고 파일로 결과를 전달
  - 집계 행은 Java `record`로 표현
- `org.gradle.parallel=true`이면 서로 다른 프로젝트의 Task와도 동시에 실행됨

`build-logic/src/main/java/dev/lab/gradle/tasks/LineCountWorkAction.java`

```java
package dev.lab.gradle.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/** 6단계: Worker API 작업 단위. 파일 하나의 줄 수를 센다. */
public abstract class LineCountWorkAction implements WorkAction<LineCountWorkAction.Params> {

    public interface Params extends WorkParameters {
        RegularFileProperty getSourceFile();
        RegularFileProperty getResultFile();
    }

    @Override
    public void execute() {
        var source = getParameters().getSourceFile().get().getAsFile().toPath();
        var result = getParameters().getResultFile().get().getAsFile().toPath();
        try (var lines = Files.lines(source)) {
            long count = lines.filter(line -> !line.isBlank()).count();
            Files.createDirectories(result.getParent());
            Files.writeString(result, Long.toString(count));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

`build-logic/src/main/java/dev/lab/gradle/tasks/LineCountTask.java`

```java
package dev.lab.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** 6단계: Worker API로 파일별 작업을 병렬 실행하고 결과를 집계한다. */
@CacheableTask
public abstract class LineCountTask extends DefaultTask {

    /** 집계 결과 한 줄. Java record 사용. */
    record FileStat(String path, long lines) {}

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @Inject
    protected abstract FileSystemOperations getFs();

    @TaskAction
    public void count() throws IOException {
        Path partsDir = getOutputDir().dir("parts").get().getAsFile().toPath();
        getFs().delete(spec -> spec.delete(partsDir.toFile()));

        WorkQueue queue = getWorkerExecutor().noIsolation();
        List<String> relativePaths = new ArrayList<>();

        getSources().getAsFileTree().visit(details -> {
            if (details.isDirectory()) return;
            String rel = details.getRelativePath().getPathString();
            relativePaths.add(rel);
            queue.submit(LineCountWorkAction.class, params -> {
                params.getSourceFile().set(details.getFile());
                params.getResultFile().set(partsDir.resolve(rel + ".count").toFile());
            });
        });

        queue.await();   // 모든 워커가 끝날 때까지 대기

        List<FileStat> stats = new ArrayList<>();
        for (String rel : relativePaths) {
            long n = Long.parseLong(Files.readString(partsDir.resolve(rel + ".count")).trim());
            stats.add(new FileStat(rel, n));
        }
        stats.sort(Comparator.comparingLong(FileStat::lines).reversed());

        long total = stats.stream().mapToLong(FileStat::lines).sum();
        String report = stats.stream()
                .map(s -> "%6d  %s".formatted(s.lines(), s.path()))
                .collect(Collectors.joining(System.lineSeparator()))
                + System.lineSeparator() + "------" + System.lineSeparator()
                + "%6d  합계 (%d개 파일)".formatted(total, stats.size()) + System.lineSeparator();

        Path reportFile = getOutputDir().file("report.txt").get().getAsFile().toPath();
        Files.writeString(reportFile, report);
        getLogger().lifecycle(report);
    }
}
```

실행

```bash
./gradlew :app:countLines
#      8  dev/lab/app/Main.java
# ------
#      8  합계 (1개 파일)

rm -rf app/build/toolkit/line-count
./gradlew :app:countLines
# > Task :app:countLines FROM-CACHE
```

실습 과제

1. `app/src/main/java`에 클래스를 50개 생성한 뒤 `--info`로 워커가 여러 스레드에서 돌아가는지 확인.
2. `noIsolation()`을 `processIsolation(spec -> spec.forkOptions(o -> o.setMaxHeapSize("256m")))`로 바꿔 실행 시간을 비교.

**정답**

1. 클래스 50개를 만들고, 워커가 어느 스레드에서 도는지 로그로 남깁니다. `--info`만으로는 스레드가 잘 보이지 않으므로 `WorkAction`에 로그를 추가하는 방법이 확실합니다.

```powershell
1..50 | ForEach-Object {
  "package dev.lab.app;`n`npublic class Gen$_ {`n    int value = $_;`n}" |
    Set-Content -Encoding utf8 "app\src\main\java\dev\lab\app\Gen$_.java"
}
```

`LineCountWorkAction.execute()` 맨 앞에 추가:

```java
org.gradle.api.logging.Logging.getLogger(LineCountWorkAction.class)
        .lifecycle("worker thread = {}", Thread.currentThread().getName());
```

```text
> .\gradlew.bat :app:countLines --rerun-tasks
worker thread = WorkerExecutor Queue
worker thread = WorkerExecutor Queue Thread 2
worker thread = WorkerExecutor Queue Thread 3
...
```

여러 스레드 이름이 섞여 나오면 병렬 실행된 것입니다. 동시에 도는 워커 수는 `--max-workers`(기본값 = CPU 코어 수)로 제한되므로 `--max-workers=1`로 다시 실행해 보면 스레드가 하나로 줄어드는 것을 비교할 수 있습니다. 실습 후에는 `Gen*.java`와 로그 코드를 지워 두세요.

2. `LineCountTask.count()`의 큐 생성 부분 교체:

```java
WorkQueue queue = getWorkerExecutor().processIsolation(spec ->
        spec.forkOptions(o -> o.setMaxHeapSize("256m")));
```

```text
> .\gradlew.bat :app:countLines --rerun-tasks   # 처음: 워커 JVM 기동 비용 때문에 noIsolation 보다 확연히 느림
> .\gradlew.bat :app:countLines --rerun-tasks   # 두 번째: 워커 데몬을 재사용해 차이가 줄어듦
```

줄 수 세기처럼 가벼운 작업은 `noIsolation()`이 가장 빠릅니다. `processIsolation()`은 다른 JDK나 힙 설정이 필요하거나, 라이브러리가 전역 상태를 더럽히는 무거운 작업(컴파일러, 코드 생성기 등)에 씁니다. 실제 수치는 PC마다 다르므로 두 방식 모두 빌드 출력의 `BUILD SUCCESSFUL in ...` 시간으로 비교하세요.

## 8주차 — 테스트: ProjectBuilder와 TestKit

목표: 빠른 단위 테스트로 플러그인 배선을, 느리지만 실제에 가까운 기능 테스트로 Task 동작(UP-TO-DATE, 증분, 캐시, Configuration Cache)을 검증합니다.

| 구분 | 도구 | 검증 대상 | 속도 |
| --- | --- | --- | --- |
| 단위 | `ProjectBuilder` | Task 등록, Extension → Task 값 연결, 의존성 | 밀리초 단위 |
| 기능 | `GradleRunner`(TestKit) | 실제 빌드 실행 결과와 Task outcome | 초 단위 |

`build-logic/src/test/java/dev/lab/gradle/ToolkitPluginTest.java`

```
package dev.lab.gradle;

import dev.lab.gradle.tasks.GreetingTask;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 8단계-1: ProjectBuilder 단위 테스트 – 빠르지만 Task 를 실제로 실행하진 않는다. */
class ToolkitPluginTest {

    @Test
    void registersAllTasks() {
        Project project = ProjectBuilder.builder().withName("demo").build();
        project.getPluginManager().apply("dev.lab.toolkit");

        for (String name : new String[]{"hello", "greet", "generateBuildInfo", "checksum", "countLines"}) {
            assertNotNull(project.getTasks().findByName(name), name + " 태스크가 없음");
        }
    }

    @Test
    void extensionValuesFlowIntoTask() {
        Project project = ProjectBuilder.builder().withName("demo").build();
        project.getPluginManager().apply("dev.lab.toolkit");

        var ext = project.getExtensions().getByType(ToolkitExtension.class);
        ext.getGreeting().set("Hi");

        var greet = (GreetingTask) project.getTasks().getByName("greet");
        assertEquals("Hi", greet.getGreeting().get());
        assertEquals("demo", greet.getRecipient().get());   // convention = 프로젝트 이름
    }

    @Test
    void generatedSourcesAreAddedWhenJavaPluginApplied() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply("dev.lab.toolkit");

        var compileJava = project.getTasks().getByName("compileJava");
        var deps = compileJava.getTaskDependencies().getDependencies(compileJava);
        assertTrue(deps.stream().anyMatch(t -> t.getName().equals("generateBuildInfo")));
    }
}.c
```

`build-logic/src/functionalTest/java/dev/lab/gradle/ToolkitPluginFunctionalTest.java`

```typescript
package dev.lab.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 8단계-2: TestKit 기능 테스트 – 실제 Gradle 빌드를 띄워 Task 를 실행한다. */
class ToolkitPluginFunctionalTest {

    @TempDir
    Path projectDir;

    private GradleRunner runner(String... args) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(args)
                .forwardOutput();
    }

    private void writeBuild(String toolkitBlock) throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"\n");
        Files.writeString(projectDir.resolve("build.gradle.kts"), """
                plugins { id("dev.lab.toolkit") }
                %s
                """.formatted(toolkitBlock));
    }

    @Test
    void greetWritesFileAndIsUpToDateOnSecondRun() throws IOException {
        writeBuild("""
                toolkit {
                    greeting = "Hi"
                    recipient = "TestKit"
                }
                """);

        BuildResult first = runner("greet", "--configuration-cache").build();
        assertEquals(SUCCESS, first.task(":greet").getOutcome());
        assertEquals("Hi, TestKit!",
                Files.readString(projectDir.resolve("build/toolkit/greeting.txt")));

        BuildResult second = runner("greet", "--configuration-cache").build();
        assertEquals(UP_TO_DATE, second.task(":greet").getOutcome());
        assertTrue(second.getOutput().contains("Reusing configuration cache"));
    }

    @Test
    void commandLineOptionOverridesRecipient() throws IOException {
        writeBuild("");
        BuildResult result = runner("greet", "--recipient=CLI").build();
        assertTrue(result.getOutput().contains("Hello, CLI!"));
    }

    @Test
    void checksumIsIncremental() throws IOException {
        writeBuild("");
        Path src = Files.createDirectories(projectDir.resolve("src"));
        Files.writeString(src.resolve("a.txt"), "A");
        Files.writeString(src.resolve("b.txt"), "B");

        BuildResult full = runner("checksum").build();
        assertTrue(full.getOutput().contains("전체 실행"));
        assertTrue(Files.exists(projectDir.resolve("build/toolkit/checksums/a.txt.sha256")));

        Files.writeString(src.resolve("a.txt"), "A2");
        Files.delete(src.resolve("b.txt"));

        BuildResult incremental = runner("checksum").build();
        assertTrue(incremental.getOutput().contains("증분 실행"));
        assertTrue(incremental.getOutput().contains("처리한 파일 수: 2"));
        assertTrue(Files.notExists(projectDir.resolve("build/toolkit/checksums/b.txt.sha256")));
    }

    @Test
    void countLinesIsCacheable() throws IOException {
        writeBuild("");
        Path javaDir = Files.createDirectories(projectDir.resolve("src/main/java/demo"));
        Files.writeString(javaDir.resolve("A.java"), "class A {\n\n  int x;\n}\n");

        runner("countLines", "--build-cache").build();
        deleteRecursively(projectDir.resolve("build"));   // 출력 삭제 → 캐시에서 복원되어야 함
        BuildResult cached = runner("countLines", "--build-cache").build();
        assertEquals(FROM_CACHE, cached.task(":countLines").getOutcome());
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
```

실행

```bash
./gradlew -p build-logic test functionalTest   # 또는 check
```

실습 과제: `countLinesIsCacheable`을 복사해 `generateBuildInfo`용 테스트를 작성하고, `.git`이 없는 임시 디렉터리에서 `GIT_COMMIT = "unknown"`이 생성되는지 검증하세요.

**정답**

`ToolkitPluginFunctionalTest`에 추가:

```java
@Test
void generateBuildInfoFallsBackToUnknownWithoutGit() throws IOException {
    writeBuild("""
            version = "2.0.0"
            toolkit {
                basePackage = "demo.info"
            }
            """);

    BuildResult first = runner("generateBuildInfo", "--build-cache").build();
    assertEquals(SUCCESS, first.task(":generateBuildInfo").getOutcome());

    Path generated = projectDir.resolve(
            "build/generated/sources/toolkit/java/demo/info/BuildInfo.java");
    String source = Files.readString(generated);
    assertTrue(source.contains("package demo.info;"));
    assertTrue(source.contains("VERSION = \"2.0.0\""));
    assertTrue(source.contains("GIT_COMMIT = \"unknown\""));   // @TempDir 에는 .git 이 없음

    deleteRecursively(projectDir.resolve("build"));
    BuildResult cached = runner("generateBuildInfo", "--build-cache").build();
    assertEquals(FROM_CACHE, cached.task(":generateBuildInfo").getOutcome());
}
```

검증 포인트는 세 가지입니다.

- `basePackage`가 디렉터리 경로(`demo/info`)와 `package` 선언에 둘 다 반영되는지
- `version`을 `plugins {}` 뒤에 설정해도 Lazy Provider 덕분에 `unspecified`가 아닌 `2.0.0`이 들어가는지
- git 저장소가 아닌 곳에서 `GitCommitValueSource`가 예외 대신 `unknown`을 돌려주는지

마지막 캐시 검사는 생성 결과에 빌드 시각을 넣지 않았기 때문에 통과합니다. `LocalDateTime.now()`를 넣으면 입력은 같아도 매번 내용이 달라져 재현 가능한 빌드가 깨집니다.

## 마무리 — 수료 체크리스트와 확장 과제

아래 항목이 모두 통과하면 과정을 마친 것입니다.

- [ ] `./gradlew :app:tasks --group toolkit`에 Task 5개가 보인다
- [ ] `./gradlew :app:run`이 버전과 커밋 해시를 출력한다
- [ ] 같은 명령을 두 번 실행하면 "Configuration cache entry reused."가 나온다
- [ ] `greet`, `checksum`이 두 번째 실행에서 UP-TO-DATE가 된다
- [ ] `countLines` 출력을 지우고 다시 실행하면 FROM-CACHE가 된다
- [ ] `./gradlew -p build-logic check`(validatePlugins + 단위 + 기능 테스트)가 통과한다

자주 마주치는 문제

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| `Unsupported class file major version 69` | Gradle 데몬이 JDK 25보다 낮은 버전 | `updateDaemonJvm --jvm-version=25` 또는 `JAVA_HOME`을 JDK 25로 |
| 버전이 `unspecified`로 생성됨 | 플러그인이 `project.getVersion()`을 즉시 읽음 | `providers.provider(...)`로 지연 평가 |
| `conflicting incremental annotations` | `@Incremental`과 `@SkipWhenEmpty` 동시 사용 | 하나만 사용 |
| Configuration Cache 문제 보고 | Task 액션에서 `getProject()` 호출 | `@Inject` 서비스나 Property로 대체 |
| `compileJava`가 BuildInfo를 못 찾음 | `srcDir`에 고정 경로를 넘김 | `taskProvider.flatMap(...)`으로 연결 |

확장 과제

1. `processIsolation` 워커에서 서드파티 라이브러리(예: 마크다운 파서)를 별도 클래스패스로 로드하는 `RenderDocsTask` 만들기.
2. `BuildService`로 빌드 전체에서 공유되는 HTTP 클라이언트를 만들고 `usesService`로 동시성 제한하기.
3. `ExecOperations`를 주입해 외부 CLI를 실행하는 Task를 만들고, 종료 코드에 따라 `GradleException`을 던지기.
4. `com.gradle.plugin-publish` 플러그인으로 `build-logic`을 Gradle Plugin Portal에 배포할 수 있게 구성하기.

## Sources

- [Gradle 9.1.0 Release Notes — Java 25 지원](https://docs.gradle.org/9.1.0/release-notes.html)
- [Gradle Newsletter 2025년 9월 — 데몬·툴체인 모두 Java 25 지원](https://newsletter.gradle.org/html/2025-09-25-September2025/index.html)
- [How Gradle Is Javamaxxing — JDK별 지원 버전](https://blog.gradle.org/gradle-is-javamaxxing)

## 배운 것 정리

Gradle 플러그인의 핵심은 **설정 단계에서는 연결만 하고, 값은 실행 단계에서 읽는 것**입니다. 아래 내용은 전부 이 원칙에서 나옵니다.

### 1. 빌드의 구조

| 개념 | 한 줄 설명 |
| --- | --- |
| `Project` | `build.gradle.kts` 하나에 대응하는 객체. 이름·버전·폴더·Task·Extension을 담음. 스크립트의 코드는 전부 이 객체의 메서드 호출 |
| 포함 빌드 (`build-logic`) | 플러그인 코드를 담는 독립 빌드. `pluginManagement { includeBuild(...) }`로 연결하면 `plugins { id(...) }`로 쓸 수 있음 |
| Plugin | `apply(Project)`에서 Task·Extension을 등록하고 서로 연결하는 클래스. 사용자는 플러그인 ID 한 줄만 씁 |
| Extension | 플러그인 설정값을 받는 창구. `toolkit { }`, `java { }`, `application { }`이 모두 Extension |
| Task | 실제 작업 단위. 입력·출력을 선언하고 `@TaskAction`에서 일함 |
| 소스셋 | `java` 플러그인이 자동으로 만드는 "컴파일할 폴더 묶음" (`main`, `test`). `.\gradlew.bat :app:sourceSets`로 확인 |

### 2. 세 단계와 설정 캐시

| 단계 | 하는 일 | `Project` 사용 |
| --- | --- | --- |
| 초기화 | `settings.gradle.kts`를 읽고 Project 객체 생성 | — |
| 설정 | 빌드 스크립트와 플러그인 `apply()` 실행, Task 등록·연결 | 가능 |
| 실행 | 필요한 Task의 `@TaskAction` 실행 | **금지** |

- 설정 캐시는 설정 단계의 결과(Task들)를 저장했다가 다음 빌드에서 설정 단계를 건너뛴. 실행 단계에는 `Project`가 없다고 생각해야 함
- 플러그인의 `apply()` 본문, `register`의 설정 람다, `withPlugin` 람다는 설정 단계 → `Project` 사용 가능
- `@TaskAction`, `doLast`, `ValueSource.obtain()`, `WorkAction.execute()`는 실행 단계 → `Project` 사용 금지
- 기본 패턴: **플러그인이 `Project`에서 값을 꺼내 Task의 `Property`에 넣고, Task는 자기 `Property`만 읽는다**

### 3. Lazy 설정: Provider와 Property

| 타입 | 읽기 | 쓰기 | 용도 |
| --- | --- | --- | --- |
| `Provider<T>` | `.get()` | 불가 | "나중에 값을 구하는 방법"을 담은 상자 |
| `Property<T>` | `.get()` | `.set()`, `.convention()` | Task·Extension의 설정값 |
| `RegularFileProperty` / `DirectoryProperty` | — | — | 파일 하나 / 폴더 하나 |
| `ConfigurableFileCollection` | — | `.from()`(추가), `.setFrom()`(교체) | 파일·폴더 여러 개 |

- `set(다른 Provider)`는 값 복사가 아니라 **연결**. 사용자가 나중에 값을 바꿔도 반영됨
- `convention()`은 기본값, `set()`은 명시값. 명시값이 우선
- 고정 상수 기본값(`"Hello"`, `"SHA-256"`)은 Task가, 환경에서 오는 기본값(프로젝트 이름)은 플러그인이 정함
- `plugins { }`는 `version = ...`보다 먼저 실행되므로 `project.getVersion()`을 바로 읽으면 `unspecified`. `providers.provider(...)`로 감싸 나중에 읽음
- Task 출력에서 만든 Provider(`buildInfo.flatMap(BuildInfoTask::getOutputDir)`)는 "누가 만드는지"까지 담아서 `dependsOn` 없이 실행 순서가 자동으로 연결됨
- `abstract` getter만 선언하면 Gradle이 리플렉션으로 분석한 뒤 바이트코드로 하위 클래스(`GreetingTask_Decorated`)를 생성해 구현을 채움

### 4. Task 등록

- `register`는 Task가 필요할 때만 생성·설정(Task Configuration Avoidance), `create`는 매번 즉시 생성
- 등록된 Task는 `register`의 람다에서 입력·출력 경로를 채움. Task 클래스는 경로를 모르는 범용 부품으로 둠
- `withPlugin("java", ...)`: 다른 플러그인이 있을 때만, 적용 순서와 무관하게 반응
- 확인 명령: `:app:tasks --group toolkit`, `:app:tasks --all`, `:app:help --task greet`

### 5. 입력·출력과 재사용

| 어노테이션 | 의미 |
| --- | --- |
| `@Input` | 일반 값 입력 |
| `@InputFiles` / `@InputFile` / `@InputDirectory` | 파일 입력 (내용 해시로 비교) |
| `@OutputFile` / `@OutputDirectory` | 출력 |
| `@PathSensitive(RELATIVE)` | 경로를 상대 경로로 취급 → 다른 위치에 체크아웃해도 캐시 재사용 |
| `@Internal` | 결과에 영향 없는 프로퍼티를 명시적으로 제외 |
| `@CacheableTask` | 결과를 빌드 캐시에 저장 가능 |
| `@Option` | 명령줄 인자(`--recipient=...`)를 프로퍼티에 연결 |

| 결과 표시 | 뜻 |
| --- | --- |
| (표시 없음) | 실제로 실행함 |
| `UP-TO-DATE` | 입력·출력이 지난번과 같아서 건너뜀 |
| `FROM-CACHE` | 출력이 없거나 달라졌지만, 같은 입력의 결과를 빌드 캐시에서 복원 |
| `NO-SOURCE` | 입력 파일이 없어서 건너뜀 |

- 출력이 없는 Task는 항상 실행됨
- 출력 파일을 손으로 고치면 Gradle이 감지해 다시 실행함
- 어노테이션 누락·충돌은 `validatePlugins`가 잡아 줌
- rich 콘솔은 출력이 있는 Task만 남김. 전부 보려면 `--console=plain`

### 6. 서비스 주입과 외부 값

- `@Inject` abstract getter로 Gradle 서비스를 받음: `FileSystemOperations`(파일 복사·삭제), `ExecOperations`(외부 프로세스 실행), `WorkerExecutor`(병렬 작업)
- `ValueSource`: 빌드 바깥의 값(git 해시 등)을 Gradle이 추적할 수 있게 읽는 장치. `obtain()`이 값을 계산하고 `providers.of(...)`가 그 레시피를 `Provider`로 돌려줌
- `Params`: Gradle이 객체를 직접 만들어 생성자로 값을 넘길 수 없으므로, 입력을 담는 전용 객체. `repoDir`은 입력(어느 폴더에서 git을 돌릴지)이고, 결과는 `obtain()`의 반환값. `WorkParameters`도 같은 패턴
- Provider를 Task 입력에 **연결만** 하면 설정 캐시는 재사용되고 값은 실행 때 새로 계산됨(빠름 + 정확함). 설정 단계에서 `.get()`하면 값이 바뀔 때마다 설정 캐시 자체가 버려짐
- 설정 단계에서 `ProcessBuilder`로 직접 프로세스를 실행하면 설정 캐시 오류(`external process started`)

### 7. 증분 처리와 병렬 처리

- 증분 Task = `@TaskAction`이 `InputChanges`를 받고 + 파일 입력에 `@Incremental`
- `changes.getFileChanges(getSources())`: 그 입력에서 바뀜 파일만(`ADDED`/`MODIFIED`/`REMOVED`) 돌려줌. Gradle이 파일별 지문을 기록해 두고 비교
- 전체 실행(첫 실행, `@Input` 변경)에는 모든 파일이 `ADDED`로 오고 출력 폴더가 비워짐 → 반복문 하나로 두 경우 처리
- `REMOVED`를 처리하지 않으면 원본 없는 출력이 남음
- Worker API: `WorkAction` + `WorkParameters`, `queue.submit(...)` 후 `queue.await()`. 워커끼리는 파일로 결과를 전달
- 격리 수준: `noIsolation()` < `classLoaderIsolation()` < `processIsolation()`. 가벼운 작업은 `noIsolation()`이 가장 빠름

### 8. 테스트

| 도구 | 검증 대상 | 특징 |
| --- | --- | --- |
| `ProjectBuilder` | Task 등록, Extension → Task 연결, 의존성 | 빠름, Task를 실행하지는 않음 |
| `GradleRunner` (TestKit) | 실제 빌드 결과, `TaskOutcome`, 설정 캐시 재사용 | 느리지만 실제와 같음 |

### 9. 실습하며 만난 문제

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| `does not contain a Gradle build` | settings 파일 없이 `gradle wrapper` 실행 | `settings.gradle.kts` 먼저 작성 |
| `No toolchain download repositories configured` | JDK 다운로드 저장소 없음 | settings에 `foojay-resolver-convention` 1.0.0 |
| `cannot find symbol BuildInfo` | 생성 폴더가 소스셋에 연결 안 됨 | `srcDir(buildInfo.flatMap(...))` |
| 버전이 `unspecified` | 플러그인이 버전을 즉시 읽음 | `providers.provider(...)` |
| `conflicting incremental annotations` | `@Incremental` + `@SkipWhenEmpty` | 하나만 사용 |
| `:app:run` 한글 깨짐 | 앱 출력(CP949)과 Gradle 수신(UTF-8) 불일치 | `run`에 `-Dstdout.encoding=UTF-8` |
| `compileJava`가 화면에 안 보임 | rich 콘솔은 출력 없는 Task를 숨김 | `--console=plain` |

package dev.lab.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 8단계-2: TestKit 기능 테스트 - 실제 Gradle 빌드를 띄워 Task를 실행한다.
 */
public class ToolkitPluginFunctionalTest {

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
		Files.writeString(projectDir.resolve("settings.gradle.kts"),
			"rootProject.name = \"demo\"\n");
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

		var first = runner("greet", "--configuration-cache").build();
		assertEquals(TaskOutcome.SUCCESS, first.task(":greet").getOutcome());
		assertEquals("Hi, TestKit!",
			Files.readString(projectDir.resolve("build/toolkit/greeting.txt")));

		var second = runner("greet", "--configuration-cache").build();
		assertEquals(TaskOutcome.UP_TO_DATE, second.task(":greet").getOutcome());
		assertTrue(second.getOutput().contains("Reusing configuration cache"));
	}

	@Test
	void commandLineOptionOverridesRecipient() throws IOException {
		writeBuild("");
		var result = runner("greet", "--recipient=CLI").build();
		assertTrue(result.getOutput().contains("Hello, CLI!"));
	}

	@Test
	void checksumIsIncremental() throws IOException {
		writeBuild("");
		var src = Files.createDirectories(projectDir.resolve("src"));
		Files.writeString(src.resolve("a.txt"), "A");
		Files.writeString(src.resolve("b.txt"), "B");

		var full = runner("checksum").build();
		assertTrue(full.getOutput().contains("전체 실행"));

		assertTrue(Files.exists(projectDir.resolve("build/toolkit/checksums/a.txt.sha256")));

		Files.writeString(src.resolve("a.txt"), "A2");
		Files.delete(src.resolve("b.txt"));

		var incremental = runner("checksum").build();
		assertTrue(incremental.getOutput().contains("증분 실행"));
		assertTrue(incremental.getOutput().contains("처리한 파일 수: 2"));

		assertTrue(Files.notExists(projectDir.resolve("build/toolkit/checksums/b.txt.sha256")));
	}

	@Test
	void countLinesIsCacheable() throws IOException {
		writeBuild("");
		var javaDir = Files.createDirectories(projectDir.resolve("src/main/java/demo"));
		Files.writeString(javaDir.resolve("A.java"), "class A {\n\n int x;\n}\n");

		runner("countLines", "--build-cache").build();
		deleteRecursively(projectDir.resolve("build"));

		var cached = runner("countLines", "--build-cache").build();
		assertEquals(TaskOutcome.FROM_CACHE, cached.task(":countLines").getOutcome());
	}

	private static void deleteRecursively(Path root) throws IOException {
		try (var paths = Files.walk(root)) {
			for (var p : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(p);
			}
		}
	}
}

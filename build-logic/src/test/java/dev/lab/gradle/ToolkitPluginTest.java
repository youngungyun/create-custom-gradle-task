package dev.lab.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import dev.lab.gradle.tasks.GreetingTask;

/**
 * 8단계-1: ProjectBuilder 단위 테스트 - 빠르지만 Task를 강제로 실행하진 않는다.
 */
class ToolkitPluginTest {

	@Test
	void registersAllTasks() {
		var project = ProjectBuilder.builder().withName("demo").build();
		project.getPluginManager().apply("dev.lab.toolkit");

		for (var name : new String[] {"hello", "greet", "generateBuildInfo",
		"checksum", "countLines"}) {
			assertNotNull(project.getTasks().findByName(name),
				name + " 태스크가 없음");
		}
	}

	@Test
	void extensionValuesFlowIntoTask() {
		var project = ProjectBuilder.builder().withName("demo").build();
		project.getPluginManager().apply("dev.lab.toolkit");

		var ext = project.getExtensions().getByType(ToolkitExtension.class);
		ext.getGreeting().set("Hi");

		var greet = (GreetingTask) project.getTasks().getByName("greet");
		assertEquals("Hi", greet.getGreeting().get());
		assertEquals("demo", greet.getRecipient().get()); // convention = 프로젝트 이름
	}

	@Test
	void generatedSourceAreAddedWhenJavaPluginApplied() {
		var project = ProjectBuilder.builder().build();
		project.getPluginManager().apply("java");
		project.getPluginManager().apply("dev.lab.toolkit");

		var compileJava = project.getTasks().getByName("compileJava");
		var deps = compileJava.getTaskDependencies().getDependencies(compileJava);
		assertTrue(deps.stream().allMatch(t ->
			t.getName().equals("generateBuildInfo")));
	}
}

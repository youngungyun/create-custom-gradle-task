package dev.lab.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import dev.lab.gradle.tasks.GreetingTask;
import dev.lab.gradle.tasks.HelloTask;

/**
 * 4단계: 모든 Task를 등록하고 Extension과 연결하는 플러그인.
 */
public class ToolkitPlugin implements Plugin<Project> {

	public static final String GROUP = "toolkit";

	@Override
	public void apply(Project project) {
		var layout = project.getLayout();
		var providers = project.getProviders();
		var tasks = project.getTasks();

		// 1) Extension + 기본값(convention)
		var ext = project.getExtensions().create("toolkit",
			ToolkitExtension.class);
		ext.getGreeting().convention("Hello");
		ext.getRecipient().convention(project.getName());
		ext.getBasePackage().convention("generated");
		ext.getChecksumAlgorithm().convention("SHA-256");

		// 2) 단순 Task
		tasks.register("hello", HelloTask.class,
			t -> {
			t.setGroup(GROUP);
			t.setDescription("Java로 작성한 첫 번째 Task");
			}
		);

		// 3) Lazy Property 연결
		tasks.register("greet", GreetingTask.class,
			t -> {
				t.setGroup(GROUP);
				t.setDescription("인사말을 파일로 출력");
				t.getGreeting().set(ext.getGreeting());
				t.getRecipient().set(ext.getRecipient());

				t.getOutputFile().set(
					layout.getBuildDirectory()
						.file("toolkit/greeting.txt")
				);
			}
		);

	}
}

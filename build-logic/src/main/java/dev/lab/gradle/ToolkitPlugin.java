package dev.lab.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import dev.lab.gradle.tasks.HelloTask;

public class ToolkitPlugin implements Plugin<Project> {

	@Override
	public void apply(Project target) {
		target.getTasks()
			.register("hello",
				HelloTask.class,
				t -> {
				t.setGroup("toolkit");
				t.setDescription("Java로 작성한 첫 번째 Task");
				});
	}
}

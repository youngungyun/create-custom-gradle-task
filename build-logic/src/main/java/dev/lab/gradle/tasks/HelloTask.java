package dev.lab.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

/**
 * 1단계: 가장 단순한 Java Task. 입력도 출력도 없다.
 */
public abstract class HelloTask extends DefaultTask {

	@TaskAction
	public void sayHello() {
		getLogger().lifecycle(
			"Hello from a Java task! (task path={})", getPath()
		);
	}
}

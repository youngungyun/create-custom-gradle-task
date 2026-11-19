package dev.lab.gradle.tasks;

import java.io.IOException;
import java.nio.file.Files;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/**
 * 2단계: Lazy Property 입력 + 파일 출력 -> UP-TO-DATE / 빌드 캐시 지원.
 */
@CacheableTask
public abstract class GreetingTask extends DefaultTask {

	@Input
	public abstract Property<String> getGreeting();

	@Input
	@Option(option = "recipient", description = "인사 받은 대상")
	public abstract Property<String> getRecipient();

	@OutputFile
	public abstract RegularFileProperty getOutputFile();

	public GreetingTask() {
		getGreeting().convention("Hello");
	}

	@TaskAction
	public void greet() throws IOException {
		var message = "%s, %s!"
			.formatted(getGreeting().get(), getRecipient().get());
		var out = getOutputFile().get().getAsFile().toPath();
		Files.createDirectories(out.getParent());
		Files.writeString(out, message);
		getLogger().lifecycle(message);
	}
}

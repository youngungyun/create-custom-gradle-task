package dev.lab.gradle.tasks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

/**
 * 6단계: Worker API 작업 단위. 파일 하나의 줄 수를 센다.
 */
public abstract class LineCountWorkAction implements
	WorkAction<LineCountWorkAction.Params> {

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

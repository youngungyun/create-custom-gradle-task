package dev.lab.gradle;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.jspecify.annotations.Nullable;

/**
 * 7단계: 설정 단계에서 외부 프로세스(git)을 안전하게 호출한다.
 * ValueSource는 Configuration Cache가 값의 변화를 추척할 수 있게 해 준다.
 */
public abstract class GitCommitValueSource
	implements ValueSource<String, GitCommitValueSource.Params> {

	public interface Params extends ValueSourceParameters {
		DirectoryProperty getRepoDir();
	}

	@Inject
	protected abstract ExecOperations getExecOperations();

	@Override
	public @Nullable String obtain() {
		var out = new ByteArrayOutputStream();
		try {
			var result = getExecOperations()
				.exec(spec -> {
					spec.commandLine("git", "rev-parse",
						"--short", "HEAD");
					spec.setWorkingDir(
						getParameters().getRepoDir().get().getAsFile()
					);
					spec.setStandardOutput(out);
					spec.setErrorOutput(OutputStream.nullOutputStream());
					spec.setIgnoreExitValue(true);
				}
			);
			if (result.getExitValue() != 0) {
				return "unknown";
			}
			return out.toString(StandardCharsets.UTF_8).trim();
		} catch (Exception e) {
			return "unknown";
		}
	}
}

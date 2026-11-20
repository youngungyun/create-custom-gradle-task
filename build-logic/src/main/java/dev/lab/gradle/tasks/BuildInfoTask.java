package dev.lab.gradle.tasks;

import java.io.IOException;
import java.nio.file.Files;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * 3단계: 소스 코드 생성 Task. 출력 디렉토리를 main 소스셋에 연결한다.
 */
public abstract class BuildInfoTask extends DefaultTask {

	@Input
	public abstract Property<String> getPackageName();

	@Input
	public abstract Property<String> getAppVersion();

	@Input
	public abstract Property<String> getGitCommit();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDir();

	/**
	 * Gradle 서비스 주입: Project없이 파일 작업을 수행
	 * (Configuration Cache 호환).
	 */
	@Inject
	protected abstract FileSystemOperations getFs();

	@TaskAction
	public void generate() throws IOException {
		var root = getOutputDir().get().getAsFile();
		getFs().delete(spec -> spec.delete(root));

		var pkg = getPackageName().get();
		var dir = root.toPath().resolve(pkg.replace('.', '/'));
		Files.createDirectories(dir);

		var source = """
			package %s;
			
			/** 자동 생성 파일 - 직접 수정하지 마세요. */
			public final class BuildInfo {
				public static final String VERSION = "%s";
				public static final String GIT_COMMIT = "%s";
				
				private BuildInfo() {}
			}
			""".formatted(pkg,
			escape(getAppVersion().get()),
			escape(getGitCommit().get())
		);
		Files.writeString(dir.resolve("BuildInfo.java"), source);
		getLogger().lifecycle("BuildInfo.java 생성 -> {}", dir);
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\")
			.replace("\"", "\\\"");
	}
}

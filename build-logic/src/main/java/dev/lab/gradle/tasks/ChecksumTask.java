package dev.lab.gradle.tasks;

import static org.gradle.work.ChangeType.ADDED;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileType;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;

/**
 * 5단계: 증분(Incremental) Task. 바뀐 파일만 다시 처리한다.
 */
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
		? "증분 실행: 변경만 파일만 처리합니다."
		: "전체 실행: 모든 파일을 처리합니다.");

		var ext = getAlgorithm().get().toLowerCase(Locale.ROOT)
			.replace("-", "");
		int processed = 0;

		for (var change : changes.getFileChanges(getSources())) {
			if (change.getFileType() == FileType.DIRECTORY) {
				continue;
			}
			var target = getOutputDir()
				.file(change.getNormalizedPath() + "." + ext)
				.get().getAsFile();

			switch (change.getChangeType()) {
				case REMOVED -> {
					target.delete();
					getLogger().info("삭제: {}", target);
				} case ADDED, MODIFIED -> writeChecksum(change.getFile(), target);
			}
			++processed;
		}
		getLogger().lifecycle("처리한 파일 수: {}", processed);
	}

	private void writeChecksum(File source, File target) {
		try {
			var md = MessageDigest.getInstance(getAlgorithm().get());
			var hex = HexFormat.of().formatHex(md.digest(Files.readAllBytes(source.toPath())));

			Files.createDirectories(target.toPath().getParent());
			Files.writeString(target.toPath(), hex + " " + source.getName() + System.lineSeparator());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("지원하지 않는 알고리즘: " + getAlgorithm().get(), e);
		}
	}
}

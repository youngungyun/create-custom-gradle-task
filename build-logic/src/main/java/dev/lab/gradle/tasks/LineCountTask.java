package dev.lab.gradle.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

/**
 * 6단계: Worker API로 파일별 작업을 병렬 실행하고 결과를 집계한다.
 */
@CacheableTask
public abstract class LineCountTask extends DefaultTask {

	record FileStat(String path, long lines) {}

	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getSources();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDir();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@Inject
	protected abstract FileSystemOperations getFs();

	@TaskAction
	public void count() throws IOException {
		var partsDir = getOutputDir().dir("parts").get().getAsFile().toPath();
		getFs().delete((DeleteSpec spec) -> spec.delete(partsDir.toFile()));

		var queue = getWorkerExecutor().noIsolation();
		List<String> relativePaths = new ArrayList<>();

		getSources().getAsFileTree().visit(details -> {
			if (details.isDirectory()) return;
			var rel = details.getRelativePath().getPathString();
			relativePaths.add(rel);
			queue.submit(LineCountWorkAction.class, params -> {
				params.getSourceFile().set(details.getFile());
				params.getResultFile().set(partsDir.resolve(rel + ".count").toFile());
			});
		});

		queue.await();

		List<FileStat> stats = new ArrayList<>();
		for (var rel : relativePaths) {
			long n = Long.parseLong(Files.readString(partsDir.resolve(rel + ".count")).trim());
			stats.add(new FileStat(rel, n));
		}
		stats.sort(Comparator.comparingLong(FileStat::lines).reversed());

		long total = stats.stream().mapToLong(FileStat::lines).sum();
		var report = stats.stream()
			.map(s -> "%6d %s".formatted(s.lines(), s.path()))
			.collect(Collectors.joining(System.lineSeparator()))
			+ System.lineSeparator() + "--------" + System.lineSeparator()
			+ "%6d 합계 (%d개 파일)".formatted(total, stats.size()) +
			System.lineSeparator();

		var reportFile = getOutputDir().file("report.txt").get().getAsFile().toPath();
		Files.writeString(reportFile, report);
		getLogger().lifecycle(report);
	}
}

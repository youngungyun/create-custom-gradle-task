package dev.lab.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

import dev.lab.gradle.tasks.BuildInfoTask;
import dev.lab.gradle.tasks.ChecksumTask;
import dev.lab.gradle.tasks.GreetingTask;
import dev.lab.gradle.tasks.HelloTask;
import dev.lab.gradle.tasks.LineCountTask;

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

		// 4) 소스 생성 + ValueSource
		var gitCommit = providers.of(GitCommitValueSource.class, spec ->
			spec.getParameters().getRepoDir().set(project.getRootProject().getLayout().getProjectDirectory()));

		// plugins {} 블록은 version = "..."보다 먼저 실행되므로 반드시 Lazy하게 읽는다.
		var version = providers.provider(() ->
			project.getVersion().toString());
		TaskProvider<BuildInfoTask> buildInfo =
			tasks.register("generateBuildInfo", BuildInfoTask.class, t -> {
				t.setGroup(GROUP);
				t.setDescription("BuildInfo.java 생성");
				t.getPackageName().set(ext.getBasePackage());
				t.getAppVersion().convention(version);
				t.getGitCommit().set(gitCommit);

				t.getOutputDir().set(layout.getBuildDirectory()
					.dir("generated/sources/toolkit/java"));
			});

		// 5) 증분 Task
		tasks.register("checksum", ChecksumTask.class, t -> {
			t.setGroup(GROUP);
			t.setDescription("src/ 아래 모든 파일의 체크섬 계산 (증분)");

			t.getSources().from(layout.getProjectDirectory().dir("src"));
			t.getAlgorithm().set(ext.getChecksumAlgorithm());

			t.getOutputDir().set(layout.getBuildDirectory().dir("toolkit/checksums"));
		});

		// 6) Worker API Task
		tasks.register("countLines", LineCountTask.class, t -> {
			t.setGroup(GROUP);
			t.setDescription("Java 소스 줄 수 집계 (Worker API 병렬)");
			t.getSources().from(layout.getProjectDirectory().dir("src/main/java"));
			t.getOutputDir().set(layout.getBuildDirectory().dir("toolkit/line-count"));
		});

		// 7) java 플러그인이 있을 때만: 생성 소스를 main 소스셋에 연결
		// Provider로 연결하려면 compileJava -> generateBuildInfo 의존성이 자동 생성된다.
		project.getPluginManager().withPlugin("java", plugin -> {
			var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
			sourceSets.named("main", main ->
				main.getJava().srcDir(buildInfo.flatMap(BuildInfoTask::getOutputDir)));
		});
	}
}

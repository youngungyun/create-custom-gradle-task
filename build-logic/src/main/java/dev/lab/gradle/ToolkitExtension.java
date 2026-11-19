package dev.lab.gradle;

import org.gradle.api.provider.Property;

/**
 * 4단계: build.gradle.kts의 'toolkit { ... } 블록.
 */
public abstract class ToolkitExtension {

	public abstract Property<String> getGreeting();
	public abstract Property<String> getRecipient();
	public abstract Property<String> getBasePackage();
	public abstract Property<String> getChecksumAlgorithm();
}

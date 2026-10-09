package me.whereareiam.identica.common.provider.library;

import me.whereareiam.identica.model.provider.dependency.ProviderLibrary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedLibraryConflictTrackerTest {
	@Test
	void acceptsMatchingCoordinates() {
		SharedLibraryConflictTracker registry = new SharedLibraryConflictTracker();

		assertDoesNotThrow(() -> {
			registry.register(library("recognition", "1.0.0"));
			registry.register(library("recognition", "1.0.0"));
		});
	}

	@Test
	void rejectsConflictingVersions() {
		SharedLibraryConflictTracker registry = new SharedLibraryConflictTracker();
		registry.register(library("recognition", "1.0.0"));

		assertThrows(IllegalStateException.class, () ->
				registry.register(library("recognition", "2.0.0")));
	}

	private static ProviderLibrary library(String artifactId, String version) {
		return ProviderLibrary.builder()
				.groupId("example.provider.library")
				.artifactId(artifactId)
				.version(version)
				.resolveTransitiveDependencies(false)
				.build();
	}
}

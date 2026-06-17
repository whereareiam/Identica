package me.whereareiam.identica.common.provider.runtime.library;

import me.whereareiam.identica.model.provider.dependency.ProviderLibrary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedLibraryConflictTrackerTest {
	@Test
	void acceptsMatchingCoordinates() {
		SharedLibraryConflictTracker registry = new SharedLibraryConflictTracker();

		assertDoesNotThrow(() -> {
			registry.register(library("1.0.0"));
			registry.register(library("1.0.0"));
		});
	}

	@Test
	void rejectsConflictingVersions() {
		SharedLibraryConflictTracker registry = new SharedLibraryConflictTracker();
		registry.register(library("1.0.0"));

		assertThrows(IllegalStateException.class, () ->
				registry.register(library("2.0.0")));
	}

	private static ProviderLibrary library(String version) {
		return ProviderLibrary.builder()
				.groupId("me.whereareiam.identica.capability")
				.artifactId("recognition")
				.version(version)
				.resolveTransitiveDependencies(false)
				.build();
	}
}

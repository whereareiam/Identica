package me.whereareiam.identica.common.provider.runtime.library;

import me.whereareiam.identica.model.provider.dependency.ProviderLibraries;
import me.whereareiam.identica.model.provider.dependency.ProviderLibrary;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderLibraryPlannerTest {
	@Test
	void plansSharedAndProviderLibrariesSeparatelyAndPreservesRepositories() {
		ProviderLibraries libraries = new ProviderLibraries();
		libraries.setRepositories(List.of("https://repo.example/releases"));
		libraries.setLibraries(Arrays.asList(
				shared("recognition-api", "1.0.0"),
				local("recognition-runtime", "1.0.0"),
				null,
				shared("external-api", "2.0.0"),
				local("external-runtime", "2.0.0")
		));

		ProviderLibraryPlanner.ProviderLibraryPlan plan = new ProviderLibraryPlanner(new SharedLibraryConflictTracker())
				.plan(libraries);

		assertEquals(
				Set.of("recognition-api", "external-api"),
				artifactIds(plan.sharedCapabilityApis())
		);
		assertEquals(
				Set.of("recognition-runtime", "external-runtime"),
				artifactIds(plan.providerRuntimeLibraries())
		);
		assertEquals(List.of("https://repo.example/releases"), plan.sharedCapabilityApis().getRepositories());
		assertEquals(List.of("https://repo.example/releases"), plan.providerRuntimeLibraries().getRepositories());
	}

	@Test
	void deduplicatesMatchingSharedLibrariesButKeepsProviderRuntimeEntries() {
		ProviderLibraries libraries = new ProviderLibraries();
		libraries.setLibraries(List.of(
				shared("recognition-api", "1.0.0"),
				shared("recognition-api", "1.0.0"),
				local("recognition-runtime", "1.0.0")
		));

		ProviderLibraryPlanner.ProviderLibraryPlan plan = new ProviderLibraryPlanner(new SharedLibraryConflictTracker())
				.plan(libraries);

		assertEquals(List.of("recognition-api"), plan.sharedCapabilityApis().getLibraries().stream()
				.map(ProviderLibrary::getArtifactId)
				.toList());
		assertEquals(List.of("recognition-runtime"), plan.providerRuntimeLibraries().getLibraries().stream()
				.map(ProviderLibrary::getArtifactId)
				.toList());
	}

	private static @NotNull Set<String> artifactIds(@NotNull ProviderLibraries libraries) {
		return libraries.getLibraries().stream()
				.map(ProviderLibrary::getArtifactId)
				.collect(java.util.stream.Collectors.toSet());
	}

	private static @NotNull ProviderLibrary shared(@NotNull String artifactId, @NotNull String version) {
		return ProviderLibrary.builder()
				.groupId("me.whereareiam.identica.capability")
				.artifactId(artifactId)
				.version(version)
				.resolveTransitiveDependencies(false)
				.loader("shared")
				.build();
	}

	private static @NotNull ProviderLibrary local(@NotNull String artifactId, @NotNull String version) {
		return ProviderLibrary.builder()
				.groupId("me.whereareiam.identica.capability")
				.artifactId(artifactId)
				.version(version)
				.resolveTransitiveDependencies(false)
				.build();
	}
}

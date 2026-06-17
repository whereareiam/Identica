package me.whereareiam.identica.common.provider.runtime.library;

import com.google.inject.Singleton;
import me.whereareiam.identica.model.provider.dependency.ProviderLibrary;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Singleton
public class SharedLibraryConflictTracker {
	private final Map<LibraryKey, ProviderLibrary> definitions = new LinkedHashMap<>();

	public synchronized boolean register(@NotNull ProviderLibrary library) {
		LibraryKey key = LibraryKey.from(library);
		ProviderLibrary existing = definitions.putIfAbsent(key, library);
		if (existing == null) return true;
		if (sameCoordinates(existing, library)) return false;

		throw new IllegalStateException("Shared capability API library conflict for "
				+ coordinates(existing)
				+ " != "
				+ coordinates(library));
	}

	private boolean sameCoordinates(@NotNull ProviderLibrary left, @NotNull ProviderLibrary right) {
		return Objects.equals(left.getGroupId(), right.getGroupId())
				&& Objects.equals(left.getArtifactId(), right.getArtifactId())
				&& Objects.equals(left.getVersion(), right.getVersion())
				&& Objects.equals(left.getClassifier(), right.getClassifier());
	}

	private @NotNull String coordinates(@NotNull ProviderLibrary library) {
		String classifier = library.getClassifier();
		return library.getGroupId() + ":" + library.getArtifactId() + ":" + library.getVersion()
				+ (classifier == null || classifier.isBlank() ? "" : ":" + classifier);
	}

	private record LibraryKey(String groupId, String artifactId, String classifier) {
		private static @NotNull LibraryKey from(@NotNull ProviderLibrary library) {
			String classifier = library.getClassifier();
			return new LibraryKey(
					library.getGroupId(),
					library.getArtifactId(),
					classifier == null ? "" : classifier
			);
		}
	}
}

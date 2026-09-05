package me.whereareiam.identica.common.provider.library;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.provider.dependency.ProviderLibraries;
import me.whereareiam.identica.model.provider.dependency.ProviderLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProviderLibraryPlanner {
	private final SharedLibraryConflictTracker sharedConflicts;

	public @NotNull ProviderLibraryPlan plan(@Nullable ProviderLibraries libraries) {
		if (libraries == null) return ProviderLibraryPlan.empty();

		List<ProviderLibrary> sharedLibraries = new ArrayList<>();
		List<ProviderLibrary> providerRuntimeLibraries = new ArrayList<>();
		for (ProviderLibrary library : libraries.getLibraries() != null ? libraries.getLibraries() : List.<ProviderLibrary>of()) {
			if (library == null) continue;

			if (isSharedLibrary(library)) {
				if (sharedConflicts.register(library))
					sharedLibraries.add(library);
				continue;
			}

			providerRuntimeLibraries.add(library);
		}

		return new ProviderLibraryPlan(
				copyOf(libraries, sharedLibraries),
				copyOf(libraries, providerRuntimeLibraries)
		);
	}

	private @NotNull ProviderLibraries copyOf(
			@NotNull ProviderLibraries source,
			@NotNull List<ProviderLibrary> libraries
	) {
		ProviderLibraries copy = new ProviderLibraries();
		copy.setRepositories(source.getRepositories() == null ? null : List.copyOf(source.getRepositories()));
		copy.setLibraries(List.copyOf(libraries));
		return copy;
	}

	private boolean isSharedLibrary(@NotNull ProviderLibrary library) {
		String loader = library.getLoader();
		return loader != null && "shared".equalsIgnoreCase(loader.trim());
	}

	public record ProviderLibraryPlan(
			@NotNull ProviderLibraries sharedLibraries,
			@NotNull ProviderLibraries providerRuntimeLibraries
	) {
		public static @NotNull ProviderLibraryPlan empty() {
			return new ProviderLibraryPlan(ProviderLibraries.empty(), ProviderLibraries.empty());
		}
	}
}

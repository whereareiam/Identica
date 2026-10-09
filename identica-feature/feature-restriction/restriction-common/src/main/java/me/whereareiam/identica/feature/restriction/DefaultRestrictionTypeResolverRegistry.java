package me.whereareiam.identica.feature.restriction;

import com.google.inject.Singleton;
import me.whereareiam.identica.feature.restriction.registry.type.RestrictionTypeResolverRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class DefaultRestrictionTypeResolverRegistry implements RestrictionTypeResolverRegistry {
	private final List<RestrictionTypeResolver> handlers = new CopyOnWriteArrayList<>();

	@Override
	public void register(@NotNull RestrictionTypeResolver handler) {
		handlers.removeIf(existing -> existing.type().equals(handler.type()));
		handlers.add(handler);
	}

	@Override
	public void unregister(@NotNull RestrictionTypeResolver handler) {
		handlers.removeIf(existing -> existing.type().equals(handler.type()));
	}

	@Override
	public @NotNull Optional<RestrictionTypeResolver> find(@NotNull String typeId) {
		return handlers.stream()
				.filter(handler -> handler.type().matches(typeId))
				.findFirst();
	}

	@Override
	public @NotNull List<RestrictionTypeResolver> values() {
		List<RestrictionTypeResolver> resolved = new ArrayList<>(handlers);
		resolved.sort(Comparator.comparing(handler -> handler.type().getId(), String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(resolved);
	}
}

package me.whereareiam.identica.feature.restriction.registry;

import com.google.inject.Singleton;
import me.whereareiam.identica.feature.restriction.model.RestrictionTypeDescriptor;
import me.whereareiam.identica.feature.restriction.registry.type.RestrictionTypeRegistry;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class DefaultRestrictionTypeRegistry implements RestrictionTypeRegistry {
	private final List<RestrictionTypeDescriptor> descriptors = new CopyOnWriteArrayList<>();

	@Override
	public void register(@NotNull RestrictionTypeDescriptor descriptor) {
		descriptors.removeIf(existing -> existing.getType().equals(descriptor.getType()));
		descriptors.add(descriptor);
	}

	@Override
	public void unregister(@NotNull RestrictionTypeDescriptor descriptor) {
		descriptors.removeIf(existing -> existing.getType().equals(descriptor.getType()));
	}

	@Override
	public @NotNull Optional<RestrictionTypeDescriptor> find(@NotNull RestrictionType type) {
		return descriptors.stream()
				.filter(descriptor -> descriptor.getType().equals(type))
				.findFirst();
	}

	@Override
	public @NotNull List<RestrictionTypeDescriptor> values() {
		List<RestrictionTypeDescriptor> resolved = new ArrayList<>(descriptors);
		resolved.sort(Comparator.comparing(descriptor -> descriptor.getType().getId(), String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(resolved);
	}
}

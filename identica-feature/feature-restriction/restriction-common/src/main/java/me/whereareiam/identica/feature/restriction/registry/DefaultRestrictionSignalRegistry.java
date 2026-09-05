package me.whereareiam.identica.feature.restriction.registry;

import com.google.inject.Singleton;
import me.whereareiam.identica.feature.restriction.model.RestrictionSignalDescriptor;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class DefaultRestrictionSignalRegistry implements RestrictionSignalRegistry {
	private final List<RestrictionSignalDescriptor> descriptors = new CopyOnWriteArrayList<>();

	@Override
	public void register(@NotNull RestrictionSignalDescriptor descriptor) {
		descriptors.removeIf(existing -> existing.getRestrictionType().equals(descriptor.getRestrictionType())
				&& existing.getSignal().equals(descriptor.getSignal()));
		descriptors.add(descriptor);
	}

	@Override
	public void unregister(@NotNull RestrictionSignalDescriptor descriptor) {
		descriptors.removeIf(existing -> existing.getRestrictionType().equals(descriptor.getRestrictionType())
				&& existing.getSignal().equals(descriptor.getSignal()));
	}

	@Override
	public @NotNull Optional<RestrictionSignalDescriptor> find(@NotNull RestrictionType type, @NotNull String signalId) {
		return descriptors.stream()
				.filter(descriptor -> descriptor.getRestrictionType().equals(type))
				.filter(descriptor -> descriptor.getSignal().matches(signalId))
				.findFirst();
	}

	@Override
	public @NotNull List<RestrictionSignalDescriptor> signals(@NotNull RestrictionType type) {
		List<RestrictionSignalDescriptor> resolved = new ArrayList<>();
		for (RestrictionSignalDescriptor descriptor : descriptors) {
			if (descriptor.getRestrictionType().equals(type))
				resolved.add(descriptor);
		}

		resolved.sort(Comparator.comparing(descriptor -> descriptor.getSignal().getId(), String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(resolved);
	}
}

package me.whereareiam.identica.feature.restriction.registry;

import me.whereareiam.identica.feature.restriction.model.RestrictionSignalDescriptor;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Registry of globally known restriction signals.
 */
public interface RestrictionSignalRegistry {
	/**
	 * Registers signal metadata.
	 *
	 * @param descriptor signal descriptor
	 */
	void register(@NotNull RestrictionSignalDescriptor descriptor);

	/**
	 * Unregisters signal metadata.
	 *
	 * @param descriptor signal descriptor
	 */
	void unregister(@NotNull RestrictionSignalDescriptor descriptor);

	/**
	 * Resolves a signal descriptor for the supplied type and id.
	 *
	 * @param type restriction type
	 * @param signalId signal id
	 * @return resolved descriptor or empty when missing
	 */
	@NotNull Optional<RestrictionSignalDescriptor> find(@NotNull RestrictionType type, @NotNull String signalId);

	/**
	 * Returns the globally declared signals for the supplied type.
	 *
	 * @param type restriction type
	 * @return signal descriptors
	 */
	@NotNull List<RestrictionSignalDescriptor> signals(@NotNull RestrictionType type);
}

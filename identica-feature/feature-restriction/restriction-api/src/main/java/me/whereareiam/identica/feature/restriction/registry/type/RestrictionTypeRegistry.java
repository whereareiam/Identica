package me.whereareiam.identica.feature.restriction.registry.type;

import me.whereareiam.identica.feature.restriction.model.RestrictionTypeDescriptor;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Registry of globally known restriction types.
 */
public interface RestrictionTypeRegistry {
	/**
	 * Registers a restriction type descriptor.
	 *
	 * @param descriptor type descriptor
	 */
	void register(@NotNull RestrictionTypeDescriptor descriptor);

	/**
	 * Unregisters a restriction type descriptor.
	 *
	 * @param descriptor type descriptor
	 */
	void unregister(@NotNull RestrictionTypeDescriptor descriptor);

	/**
	 * Resolves descriptor metadata for the supplied type.
	 *
	 * @param type restriction type
	 * @return resolved descriptor or empty when missing
	 */
	@NotNull Optional<RestrictionTypeDescriptor> find(@NotNull RestrictionType type);

	/**
	 * Returns all declared type descriptors.
	 *
	 * @return registered descriptors
	 */
	@NotNull List<RestrictionTypeDescriptor> values();
}

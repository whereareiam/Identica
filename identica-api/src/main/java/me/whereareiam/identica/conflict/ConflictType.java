package me.whereareiam.identica.conflict;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.identica.conflict.resolver.ConflictResolver;
import me.whereareiam.identica.model.conflict.ConflictContext;
import me.whereareiam.identica.model.config.provider.Conflicts;
import me.whereareiam.identica.type.ConflictHook;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Describes a conflict that can occur and how it should be handled.
 */
public interface ConflictType<S extends ConflictSubject> {
	/**
	 * Conflict key used in configuration and context.
	 *
	 * @return conflict key
	 */
	@NotNull
	String getKey();

	/**
	 * Default hook location for this conflict type.
	 *
	 * @return default hook
	 */
	@NotNull
	default ConflictHook getDefaultHook() {
		return ConflictHook.NONE;
	}

	/**
	 * Supplies rules owned by this conflict type when no central override exists.
	 * A feature may return its current configuration so reloading takes effect
	 * without adding feature-specific defaults to core configuration.
	 *
	 * @return fallback rules, or null when the type has no rules
	 */
	default @Nullable Conflicts.ConflictRules getDefaultRules() {
		return null;
	}

	/**
	 * Return resolvers owned by this conflict type.
	 *
	 * <pre>{@code
	 * @Override
	 * public List<ConflictResolver> getResolvers() {
	 *     return List.of(formatResolver);
	 * }
	 * }</pre>
	 *
	 * @return resolvers owned by the type
	 */
	default @NotNull List<ConflictResolver> getResolvers() {
		return List.of();
	}

	/**
	 * Return guards owned by this conflict type.
	 *
	 * <pre>{@code
	 * @Override
	 * public List<ConflictGuard> getGuards() {
	 *     return List.of(entrypointGuard);
	 * }
	 * }</pre>
	 *
	 * @return guards owned by the type
	 */
	default @NotNull List<ConflictGuard> getGuards() {
		return List.of();
	}

	/**
	 * Build a conflict context for the provided subject.
	 *
	 * <pre>{@code
	 * ConflictContext context = type.createContext(subject);
	 * if (context != null) {
	 *     ConflictResolution resolution = conflictService.resolve(context);
	 *     if (resolution != null) {
	 *         // interpret the resolution inside the owning conflict type module
	 *     }
	 * }
	 * }</pre>
	 *
	 * @param subject conflict subject
	 * @return conflict context or {@code null} if no conflict applies
	 */
	@Nullable
	ConflictContext createContext(@NotNull S subject);

	/**
	 * Returns {@code true} when the provided rule selector applies to the current context.
	 *
	 * @param context conflict context
	 * @param when opaque rule selector owned by the conflict type
	 * @return whether the rule should be selected
	 */
	default boolean matchesRule(@NotNull ConflictContext context, @NotNull JsonNode when) {
		return false;
	}
}

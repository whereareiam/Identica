package me.whereareiam.identica.database.schema;

import org.jetbrains.annotations.NotNull;

/**
 * Applies provider-owned Dialectica schema contributions against the shared
 * Identica database.
 *
 * <p>Providers use this bootstrap service during their load phase to register
 * additional entity packages and migration scopes without hardcoding those
 * details in core database startup.
 */
public interface SchemaBootstrap {
	/**
	 * Applies a single schema snapshot.
	 *
	 * <p>The snapshot is executed against the already-initialized shared
	 * Jdbi-backed schema manager for the active database.
	 *
	 * @param contributor schema snapshot to apply
	 */
	void apply(@NotNull SchemaContributor contributor);
}

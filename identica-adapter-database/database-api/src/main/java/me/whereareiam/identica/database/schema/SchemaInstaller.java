package me.whereareiam.identica.database.schema;

import me.whereareiam.strata.exception.MigrationException;
import org.jetbrains.annotations.NotNull;

/**
 * Installs the {@link DatabaseSchema} of an owner into Identica's database, or upgrades it.
 *
 * <p>Identica installs the core schema ({@link DatabaseSchema#ACCOUNT_STREAM}) when it opens the
 * database. A compiled feature installs its schema when it is enabled, and a provider when it is
 * loaded, from the {@code DatabaseSchema}s bound in its injector. An add-on installs its own during
 * its load phase, through this service.
 */
public interface SchemaInstaller {
	/**
	 * Brings the schema's tables up to date: creates them on a new installation, or runs the
	 * migrations the database has not seen yet. Installing a schema again changes nothing.
	 *
	 * @param schema the schema to install
	 * @throws IllegalStateException when a stream the schema {@linkplain DatabaseSchema#requires()
	 *                               requires} has not been installed before it, when the database is
	 *                               not open, or when the schema's classes use another copy of
	 *                               Dialectica or Strata than Identica's, such as one another plugin
	 *                               bundles without relocating it
	 * @throws MigrationException    when the schema could not be brought up to date, for example
	 *                               because a migration failed or a newer build wrote the database;
	 *                               Identica must not continue on that schema
	 */
	void install(@NotNull DatabaseSchema schema);
}

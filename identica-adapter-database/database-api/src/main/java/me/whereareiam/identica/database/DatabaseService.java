package me.whereareiam.identica.database;

import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import org.jetbrains.annotations.NotNull;

/**
 * Identica's database: the connection it opens on startup to the database {@code persistence}
 * configures, and closes on shutdown.
 */
public interface DatabaseService {
	/**
	 * Tells whether the database is open and its core schema installed.
	 *
	 * @return {@code true} between a successful startup and shutdown
	 */
	boolean isInitialized();

	/**
	 * Returns Dialectica's access to the rows of an entity's table in Identica's database. It speaks
	 * the database's dialect, so the entity's columns are stored the way its schema declares them:
	 * <pre>{@code
	 * EntityAccess<NoteEntity> notes = database.access(NoteEntity.class);
	 * notes.upsert(new NoteEntity("first", "hello"));
	 * Optional<NoteEntity> note = notes.find("first");
	 * }</pre>
	 *
	 * <p>The entity's table belongs to a {@link me.whereareiam.identica.database.schema.DatabaseSchema}
	 * that has been installed. The access works until Identica closes the database when it stops.
	 *
	 * @param entityType class of the entity, with Dialectica's annotations
	 * @param <T>        entity type
	 * @return access to the entity's rows
	 * @throws IllegalStateException    when the database is not open
	 * @throws IllegalArgumentException when the class is not a valid Dialectica entity
	 */
	<T> @NotNull EntityAccess<T> access(@NotNull Class<T> entityType);
}

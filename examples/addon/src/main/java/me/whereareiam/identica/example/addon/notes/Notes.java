package me.whereareiam.identica.example.addon.notes;

import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.IdenticaAPI;
import me.whereareiam.identica.database.DatabaseService;
import me.whereareiam.identica.database.schema.SchemaInstaller;
import org.slf4j.Logger;

/**
 * Keeps the addon's notes in Identica's database: installs their table and reads and writes them through
 * Dialectica's entity access, without bundling or relocating a library.
 */
public final class Notes {
	private final EntityAccess<NoteEntity> notes;

	private Notes(EntityAccess<NoteEntity> notes) {
		this.notes = notes;
	}

	/**
	 * Installs the addon's schema, or upgrades it, and opens its notes.
	 *
	 * @param identica Identica, once it is ready
	 * @return the notes
	 */
	public static Notes open(IdenticaAPI identica) {
		identica.findService(SchemaInstaller.class).orElseThrow().install(new NotesSchema());
		DatabaseService database = identica.findService(DatabaseService.class).orElseThrow();

		return new Notes(database.access(NoteEntity.class));
	}

	/**
	 * Writes a note and reads it back, to show that the addon reaches its table.
	 *
	 * @param logger the addon's logger
	 */
	public void greet(Logger logger) {
		notes.upsert(new NoteEntity("first", "hello from the addon"));

		String text = notes.find("first").map(NoteEntity::text).orElse("<none>");
		logger.info("Example addon read note: {}", text);
	}
}

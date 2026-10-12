package me.whereareiam.identica.example.addon.notes;

import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.schema.Schema;
import me.whereareiam.identica.database.schema.DatabaseSchema;
import org.jetbrains.annotations.NotNull;

/**
 * The addon's tables, which Identica installs as the stream {@code example/notes}.
 */
public final class NotesSchema implements DatabaseSchema {
	@Override
	public @NotNull String stream() {
		return "example/notes";
	}

	@Override
	public @NotNull Schema schema() {
		return Schema.of(Dialectica.entity(NoteEntity.class).table());
	}
}

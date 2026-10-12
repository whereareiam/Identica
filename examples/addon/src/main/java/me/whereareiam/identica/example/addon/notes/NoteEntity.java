package me.whereareiam.identica.example.addon.notes;

import me.whereareiam.dialectica.entity.annotation.Id;
import me.whereareiam.dialectica.entity.annotation.Length;
import me.whereareiam.dialectica.entity.annotation.Table;

/**
 * A row of the addon's own table in Identica's database.
 */
@Table("example_notes")
public record NoteEntity(@Id @Length(64) String noteId, String text) {}

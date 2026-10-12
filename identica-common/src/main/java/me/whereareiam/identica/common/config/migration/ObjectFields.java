package me.whereareiam.identica.common.config.migration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the field names of a document's section, for the migrations that change the section while they go
 * through it.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ObjectFields {
	/**
	 * Returns the names of an object's fields, as they are before it changes.
	 *
	 * @param object the object
	 * @return the field names in their order
	 */
	static @NotNull List<String> names(@NotNull ObjectNode object) {
		List<String> names = new ArrayList<>();
		object.fieldNames().forEachRemaining(names::add);

		return names;
	}
}

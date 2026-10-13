package me.whereareiam.identica.common.conflict;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.conflict.ConflictOutcome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Makes the settings an outcome settles one conflict with: its defaults, changed by each layer in turn, where a
 * later layer wins and each one states only what it changes.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConflictOutcomeSettings {
	/**
	 * Resolves an outcome's settings.
	 *
	 * @param outcome the outcome
	 * @param mapper  the mapper the documents are read with
	 * @param layers  changes from the least to the most specific place; {@code null} entries are skipped
	 * @param <S>     the outcome's settings
	 * @return the settings
	 */
	public static <S> @NotNull S resolve(
			@NotNull ConflictOutcome<?, ?, S> outcome,
			@NotNull ObjectMapper mapper,
			@NotNull List<@Nullable ObjectNode> layers
	) {
		ObjectNode settings = mapper.valueToTree(outcome.defaults());
		for (ObjectNode layer : layers)
			if (layer != null) merge(settings, layer);

		return mapper.convertValue(settings, outcome.settingsType());
	}

	private static void merge(@NotNull ObjectNode target, @NotNull ObjectNode changes) {
		Iterator<Map.Entry<String, JsonNode>> fields = changes.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> field = fields.next();
			if (field.getValue() instanceof ObjectNode nested && target.get(field.getKey()) instanceof ObjectNode existing) {
				merge(existing, nested);
				continue;
			}

			target.set(field.getKey(), field.getValue().deepCopy());
		}
	}
}

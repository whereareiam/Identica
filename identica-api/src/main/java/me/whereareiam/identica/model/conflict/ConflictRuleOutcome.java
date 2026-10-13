package me.whereareiam.identica.model.conflict;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The outcome a rule names: its id alone, such as {@code outcome: refuse}, or its id with the settings this rule
 * changes, such as {@code outcome: { id: rename-holder, with: { holderOnline: KICK_HOLDER } }}. The settings a
 * rule leaves out come from the outcome's settings for the provider and from its defaults.
 */
@Getter
@ToString
@EqualsAndHashCode
public final class ConflictRuleOutcome {
	private final @NotNull String id;

	/**
	 * The settings this rule changes, or {@code null} when it changes none.
	 */
	private final @Nullable ObjectNode with;

	private ConflictRuleOutcome(@NotNull String id, @Nullable ObjectNode with) {
		this.id = id;
		this.with = with == null || with.isEmpty() ? null : with;
	}

	/**
	 * Names an outcome without changing its settings.
	 *
	 * @param id id of the outcome
	 * @return the reference
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static @NotNull ConflictRuleOutcome of(@NotNull String id) {
		return new ConflictRuleOutcome(id, null);
	}

	/**
	 * Names an outcome with the settings this rule changes.
	 *
	 * @param id   id of the outcome
	 * @param with settings this rule changes, or {@code null}
	 * @return the reference
	 */
	@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
	public static @NotNull ConflictRuleOutcome of(@JsonProperty("id") @NotNull String id, @JsonProperty("with") @Nullable ObjectNode with) {
		return new ConflictRuleOutcome(id, with);
	}

	/**
	 * Returns how the reference is written: the id alone when the rule changes no setting.
	 *
	 * @return the id, or an object with the id and the changed settings
	 */
	@JsonValue
	public @NotNull Object value() {
		if (with == null) return id;

		ObjectNode node = JsonNodeFactory.instance.objectNode();
		node.put("id", id);
		node.set("with", with);
		return node;
	}
}

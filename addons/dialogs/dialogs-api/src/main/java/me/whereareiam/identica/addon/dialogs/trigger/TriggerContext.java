package me.whereareiam.identica.addon.dialogs.trigger;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * One moment a trigger reports for a player: the attributes rules match against and the placeholders the dialog
 * may show.
 */
@Value
@Builder
public class TriggerContext {
	/**
	 * The unique id of the player's connection on the proxy.
	 */
	@NotNull UUID connectionUniqueId;
	/**
	 * ID of the trigger that reports the moment.
	 */
	@NotNull String trigger;
	/**
	 * What rules match against, by name, such as {@code step} or {@code scenario}. A rule's condition matches
	 * when the attribute has the condition's value, ignoring case.
	 */
	@NotNull @Singular Map<String, String> attributes;
	/**
	 * Values for the dialog's {@code {placeholders}}, by name without the braces.
	 */
	@NotNull @Singular Map<String, String> placeholders;
}

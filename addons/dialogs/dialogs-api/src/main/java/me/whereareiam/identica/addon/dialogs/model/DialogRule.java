package me.whereareiam.identica.addon.dialogs.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Which dialog shows an Identica message. Rules are tried in order when Identica tells a player a message; the first
 * whose conditions all match decides. Every rule names the message by {@code prompt}, what it asks for, or by
 * {@code message}, its ID; the addon presents those through Identica's message service.
 */
@Value
@Builder
public class DialogRule {
	/**
	 * Conditions by attribute name; each matches when the attribute has its value, ignoring case: {@code prompt}, what
	 * the message asks for, {@code message}, its ID, {@code scenario}, the player's pending scenario, and
	 * {@code host}, the hostname the player connected through. A condition on another attribute never matches.
	 */
	@NotNull @Singular("when") Map<String, String> when;
	/**
	 * ID of the dialog to show, or {@code null} to show none, which keeps the player in chat for this message.
	 */
	@Nullable String dialog;
}

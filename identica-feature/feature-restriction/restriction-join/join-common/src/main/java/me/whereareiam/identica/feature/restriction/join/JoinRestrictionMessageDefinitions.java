package me.whereareiam.identica.feature.restriction.join;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionMessages;
import me.whereareiam.identica.model.message.MessageDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The join restriction's messages to players. Their texts sit in its {@code messages.yml}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JoinRestrictionMessageDefinitions {
	/**
	 * New players may not join through the provider right now; placeholders {@code providerId}, {@code providerName}
	 * and {@code allow}, who may still join.
	 */
	public static final MessageDefinition<JoinRestrictionMessages> DENIED = MessageDefinition.<JoinRestrictionMessages>builder()
			.id("join-restriction:denied")
			.text(messages -> MessageDefinition.lines(messages.getDenied()))
			.placeholder("providerId")
			.placeholder("providerName")
			.placeholder("allow")
			.build();

	/**
	 * Returns every message of the join restriction to players.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<JoinRestrictionMessages>> all() {
		return List.of(DENIED);
	}
}

package me.whereareiam.identica.feature.sentinel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelMessages;
import me.whereareiam.identica.model.message.MessageDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The sentinel's messages. Their texts sit in the sentinel's {@code messages.yml}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SentinelMessageDefinitions {
	/**
	 * An address resumed logins too often and is locked out; placeholder {@code seconds}, how long it stays locked.
	 */
	public static final MessageDefinition<SentinelMessages> RESUME_SPAM = MessageDefinition.<SentinelMessages>builder()
			.id("sentinel:resume-spam")
			.text(messages -> MessageDefinition.lines(messages.getResumeSpam().getDenied()))
			.placeholder("seconds")
			.build();

	/**
	 * An address kept contesting usernames other connections hold and is locked out; placeholder {@code seconds}.
	 */
	public static final MessageDefinition<SentinelMessages> CONTESTED_CONNECTION = MessageDefinition.<SentinelMessages>builder()
			.id("sentinel:contested-connection")
			.text(messages -> MessageDefinition.lines(messages.getContestedConnection().getDenied()))
			.placeholder("seconds")
			.build();

	/**
	 * An address registered as many accounts as it may; placeholders {@code maxAccounts} and {@code seconds}, until a
	 * slot frees.
	 */
	public static final MessageDefinition<SentinelMessages> ACCOUNTS_PER_ADDRESS = MessageDefinition.<SentinelMessages>builder()
			.id("sentinel:accounts-per-address")
			.text(messages -> MessageDefinition.lines(messages.getAccountsPerAddress().getDenied()))
			.placeholder("maxAccounts")
			.placeholder("seconds")
			.build();

	/**
	 * Returns every message of the sentinel.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<SentinelMessages>> all() {
		return List.of(RESUME_SPAM, CONTESTED_CONNECTION, ACCOUNTS_PER_ADDRESS);
	}
}

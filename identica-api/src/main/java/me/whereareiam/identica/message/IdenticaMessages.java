package me.whereareiam.identica.message;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.model.message.MessageDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Every message of Identica's core, which core registers with {@link MessageService} when it starts. The messages
 * are defined by topic, mirroring core's {@code messages.yml}:
 *
 * <ul>
 *     <li>{@link ProviderChoiceMessages}, the provider choice</li>
 *     <li>{@link JourneyMessages}, a journey that cannot go on</li>
 *     <li>{@link SessionMessages}, sessions and concurrent logins</li>
 *     <li>{@link ConnectionMessages}, a connection Identica cannot take or route</li>
 *     <li>{@link AccountMessages}, accounts</li>
 *     <li>{@link MigrationMessages}, moving an account to another provider</li>
 *     <li>{@link UsernameConflictMessages}, a username two players want</li>
 *     <li>{@link ScenarioMessages}, the messages every scenario has</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IdenticaMessages {
	/**
	 * Returns every message of core.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<?>> all() {
		List<MessageDefinition<?>> all = new ArrayList<>();
		all.addAll(ProviderChoiceMessages.all());
		all.addAll(JourneyMessages.all());
		all.addAll(SessionMessages.all());
		all.addAll(ConnectionMessages.all());
		all.addAll(AccountMessages.all());
		all.addAll(MigrationMessages.all());
		all.addAll(UsernameConflictMessages.all());
		all.addAll(ScenarioMessages.all());

		return List.copyOf(all);
	}
}

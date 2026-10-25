package me.whereareiam.identica.message;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.MessageDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The messages about a player's session. Their texts sit in core's {@code messages.yml}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SessionMessages {
	/**
	 * Another connection finished logging in to the account or under its username.
	 */
	public static final MessageDefinition<Messages> CONCURRENT_LOGIN_KICK =
			CoreDefinitions.lines("identica:concurrent-login-kick", messages -> messages.getEngine().getConcurrentLoginKick());

	/**
	 * The account's session policy refuses a second login.
	 */
	public static final MessageDefinition<Messages> CONCURRENT_LOGIN_REFUSED =
			CoreDefinitions.lines("identica:concurrent-login-refused", messages -> messages.getEngine().getConcurrentLoginRefused());

	/**
	 * Another proxy changed the login while this one was saving it.
	 */
	public static final MessageDefinition<Messages> CONCURRENT_CHANGE_KICK =
			CoreDefinitions.lines("identica:concurrent-change-kick", messages -> messages.getEngine().getConcurrentChangeKick());

	/**
	 * The session of an authentication could not be built.
	 */
	public static final MessageDefinition<Messages> BUILD_FAILED =
			CoreDefinitions.lines("identica:authentication/session-build-failed", messages -> messages.getScenarios().getAuthentication().getSessionBuildFailed());

	/**
	 * A command needs the player's current session, which they do not have.
	 */
	public static final MessageDefinition<Messages> CURRENT_SESSION_REQUIRED =
			CoreDefinitions.text("identica:current-session-required", messages -> messages.getCommands().getCurrentSessionRequired());

	/**
	 * An administrator ended the player's session.
	 */
	public static final MessageDefinition<Messages> ENDED =
			CoreDefinitions.lines("identica:session-ended", messages -> messages.getCommands().getAdmin().getSessions().getEnd().getDisconnect());

	/**
	 * Returns every message of this topic.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<?>> all() {
		return List.of(
				CONCURRENT_LOGIN_KICK,
				CONCURRENT_LOGIN_REFUSED,
				CONCURRENT_CHANGE_KICK,
				BUILD_FAILED,
				CURRENT_SESSION_REQUIRED,
				ENDED
		);
	}
}

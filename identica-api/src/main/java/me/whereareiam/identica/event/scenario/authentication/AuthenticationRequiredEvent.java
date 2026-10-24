package me.whereareiam.identica.event.scenario.authentication;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.event.scenario.ScenarioRequiredEvent;
import me.whereareiam.identica.model.auth.AuthContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when an authentication scenario becomes a held pending
 * requirement for a connection.
 */
@Getter
@ToString
@RequiredArgsConstructor
public class AuthenticationRequiredEvent implements Event, SynchronousEvent, ScenarioRequiredEvent {
	private final @NotNull UUID connectionUniqueId;
	private final @Nullable UUID accountUniqueId;
	private final @NotNull AuthContext context;
	private final boolean resumed;
	private final long expiresAt;

	public AuthenticationRequiredEvent(
			@NotNull AuthContext context,
			boolean resumed,
			long expiresAt
	) {
		this(
				requireConnectionUniqueId(context),
				context.getAccountUniqueId(),
				context,
				resumed,
				expiresAt
		);
	}

	private static @NotNull UUID requireConnectionUniqueId(@NotNull AuthContext context) {
		UUID connectionUniqueId = context.getConnectionUniqueId();
		if (connectionUniqueId == null)
			throw new IllegalArgumentException("Authentication context is missing connectionUniqueId");
		return connectionUniqueId;
	}
}

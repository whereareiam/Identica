package me.whereareiam.identica.model.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.model.message.PlayerMessage;

@Getter
@ToString
@RequiredArgsConstructor
public class ConnectionDecision {
	private final Status status;
	/**
	 * What the player is told, or {@code null} for nothing.
	 */
	private final PlayerMessage message;
	private final String reason;

	public static ConnectionDecision allow() {
		return new ConnectionDecision(Status.ALLOW, null, null);
	}

	public static ConnectionDecision waiting(PlayerMessage message) {
		return new ConnectionDecision(Status.WAIT, message, null);
	}

	public static ConnectionDecision deny(PlayerMessage message) {
		return new ConnectionDecision(Status.DENY, message, null);
	}

	public static ConnectionDecision requireReconnect(PlayerMessage message) {
		return new ConnectionDecision(Status.REQUIRE_RECONNECT, message, null);
	}

	public static ConnectionDecision noPending() {
		return new ConnectionDecision(Status.NO_PENDING, null, null);
	}

	public enum Status {
		ALLOW,
		WAIT,
		DENY,
		REQUIRE_RECONNECT,
		NO_PENDING
	}
}

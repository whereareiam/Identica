package me.whereareiam.identica.model.pipeline.prepare.decision;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Result of a generic connection preparation request.
 *
 * <p>This decision combines early handshake preparation and profile-aware
 * preparation into a single contract that platform adapters can reuse across
 * multiple connection events.</p>
 *
 * <pre>{@code
 * PrepareDecision decision = connectionCoordinator
 *         .prepare(request)
 *         .toCompletableFuture()
 *         .join();
 * if (decision.isDenied()) {
 *     // deny the connection
 * }
 * }</pre>
 */
@Getter
@ToString
@Builder(toBuilder = true)
public class PrepareDecision {
	private final @Nullable UUID accountUniqueId;
	private final @Nullable String effectiveUsername;
	/**
	 * UUID the client claimed when it started logging in, if the platform exposed it.
	 */
	private final @Nullable UUID claimedUniqueId;

	private final @Nullable HandshakeDecision handshake;
	private final @Nullable ProviderContext provider;

	private final @Nullable String denialMessage;
	private final @NotNull Status status;

	/**
	 * Returns whether this preparation result denies the connection.
	 *
	 * @return {@code true} when denied
	 */
	public boolean isDenied() {
		return status == Status.DENY;
	}

	/**
	 * Creates an allow decision.
	 *
	 * @return allow decision
	 */
	public static @NotNull PrepareDecision allow() {
		return PrepareDecision.builder()
				.status(Status.ALLOW)
				.build();
	}

	/**
	 * Creates a deny decision.
	 *
	 * @param message denial message
	 * @return deny decision
	 */
	public static @NotNull PrepareDecision deny(@Nullable String message) {
		return PrepareDecision.builder()
				.status(Status.DENY)
				.denialMessage(message)
				.build();
	}

	/**
	 * Preparation result status.
	 */
	public enum Status {
		ALLOW,
		DENY
	}
}

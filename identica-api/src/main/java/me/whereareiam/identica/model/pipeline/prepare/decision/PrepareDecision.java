package me.whereareiam.identica.model.pipeline.prepare.decision;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.pipeline.state.StateKey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
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
	/**
	 * Key of the decision a prepare run reached, in its pipeline state. Only lives during that run and is never saved.
	 */
	public static final StateKey<PrepareDecision> KEY = StateKey.run("prepare:decision", PrepareDecision.class);

	private final @Nullable UUID accountUniqueId;
	private final @Nullable String effectiveUsername;
	/**
	 * UUID the client claimed when it started logging in, if the platform exposed it.
	 */
	private final @Nullable UUID claimedUniqueId;

	private final @Nullable HandshakeDecision handshake;
	/**
	 * Provider the connection continues with, once its profile is known.
	 */
	private final @Nullable ProviderContext provider;
	/**
	 * Which provider handles the connection, and why. The profile stage restores it from the handshake stage and
	 * the scenario keeps it.
	 */
	private final @Nullable ProviderSelection selection;
	/**
	 * Preferred provider link of the account the connection belongs to, resolved at the handshake.
	 */
	private final @Nullable AccountProviderLink preferredLink;
	/**
	 * Journey mode of the scenario the connection heads to, resolved at the handshake.
	 */
	private final @Nullable JourneyMode journeyMode;

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
	 * Creates an allow decision for a prepared connection.
	 *
	 * @param context prepare context the decision carries the handshake, claimed UUID, provider and selection from
	 * @param accountUniqueId account the connection belongs to, if known
	 * @param effectiveUsername username the player continues with
	 * @return allow decision
	 */
	public static @NotNull PrepareDecision allow(
			@Nullable PrepareContextItem context,
			@Nullable UUID accountUniqueId,
			@Nullable String effectiveUsername
	) {
		return from(context, accountUniqueId, effectiveUsername)
				.status(Status.ALLOW)
				.build();
	}

	/**
	 * Creates a deny decision for a prepared connection.
	 *
	 * @param message denial message
	 * @param context prepare context the decision carries the handshake, claimed UUID, provider and selection from
	 * @param accountUniqueId account the connection belongs to, if known
	 * @param effectiveUsername username the player tried to continue with
	 * @return deny decision
	 */
	public static @NotNull PrepareDecision deny(
			@Nullable String message,
			@Nullable PrepareContextItem context,
			@Nullable UUID accountUniqueId,
			@Nullable String effectiveUsername
	) {
		return from(context, accountUniqueId, effectiveUsername)
				.status(Status.DENY)
				.denialMessage(message)
				.build();
	}

	private static @NotNull PrepareDecisionBuilder from(
			@Nullable PrepareContextItem context,
			@Nullable UUID accountUniqueId,
			@Nullable String effectiveUsername
	) {
		return PrepareDecision.builder()
				.accountUniqueId(accountUniqueId)
				.effectiveUsername(effectiveUsername)
				.claimedUniqueId(context != null ? context.getClaimedUniqueId() : null)
				.handshake(context != null ? context.getHandshake() : null)
				.provider(context != null ? context.getProvider() : null)
				.selection(context != null ? context.getSelection() : null)
				.preferredLink(context != null ? context.getPreferredLink() : null)
				.journeyMode(context != null ? context.getJourneyMode() : null);
	}

	/**
	 * Preparation result status.
	 */
	public enum Status {
		ALLOW,
		DENY
	}
}

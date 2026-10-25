package me.whereareiam.identica.provider.premium;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.feature.verification.VerificationMessageDefinitions;
import me.whereareiam.identica.model.message.MessageDefinition;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Premium's messages to players. Their texts sit in Premium's {@code messages.yml}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PremiumMessageDefinitions {
	/**
	 * Asks a premium player for the code of their account's authenticator app.
	 */
	public static final MessageDefinition<PremiumMessages> VERIFICATION_CODE = MessageDefinition.<PremiumMessages>builder()
			.id("premium:verification-code")
			.text(messages -> MessageDefinition.lines(messages.getVerification().getAuthentication().getPrompt()))
			.prompt(VerificationMessageDefinitions.CODE_PROMPT)
			.build();

	/**
	 * The account needs a verified code before the login completes, but has no method to give one.
	 */
	public static final MessageDefinition<PremiumMessages> VERIFICATION_REQUIRED = MessageDefinition.<PremiumMessages>builder()
			.id("premium:verification-required")
			.text(messages -> messages.getVerification().getAuthentication().getRequired())
			.build();

	/**
	 * The account's verification cannot be used right now.
	 */
	public static final MessageDefinition<PremiumMessages> VERIFICATION_UNAVAILABLE = MessageDefinition.<PremiumMessages>builder()
			.id("premium:verification-unavailable")
			.text(messages -> messages.getVerification().getAuthentication().getUnavailable())
			.build();

	/**
	 * The player has to join again, so their premium login can be checked.
	 */
	public static final MessageDefinition<PremiumMessages> REJOIN = MessageDefinition.<PremiumMessages>builder()
			.id("premium:rejoin")
			.text(messages -> MessageDefinition.lines(messages.getVerification().getRejoin()))
			.build();

	/**
	 * A client joined with the username of a premium account linked here without being logged in to it.
	 */
	public static final MessageDefinition<PremiumMessages> OWNED_USERNAME = MessageDefinition.<PremiumMessages>builder()
			.id("premium:owned-username")
			.text(messages -> MessageDefinition.lines(messages.getVerification().getOwnedUsername()))
			.build();

	/**
	 * Asks the player to confirm moving their account to Premium.
	 */
	public static final MessageDefinition<PremiumMessages> MOVE_CONFIRM = MessageDefinition.<PremiumMessages>builder()
			.id("premium:move-confirm")
			.text(messages -> MessageDefinition.lines(messages.getCommands().getPremium().getConfirm()))
			.build();

	/**
	 * The move to Premium was confirmed; the player rejoins with their premium login.
	 */
	public static final MessageDefinition<PremiumMessages> MOVE_CONFIRMED = MessageDefinition.<PremiumMessages>builder()
			.id("premium:move-confirmed")
			.text(messages -> MessageDefinition.lines(messages.getCommands().getPremium().getConfirmed()))
			.build();

	/**
	 * The move needs a verified code first.
	 */
	public static final MessageDefinition<PremiumMessages> MOVE_VERIFICATION_REQUIRED = MessageDefinition.<PremiumMessages>builder()
			.id("premium:move-verification-required")
			.text(messages -> messages.getCommands().getPremium().getVerificationRequired())
			.build();

	/**
	 * The move was cancelled.
	 */
	public static final MessageDefinition<PremiumMessages> MOVE_CANCELLED = MessageDefinition.<PremiumMessages>builder()
			.id("premium:move-cancelled")
			.text(messages -> messages.getCommands().getPremium().getCancelled())
			.build();

	/**
	 * The move waited too long.
	 */
	public static final MessageDefinition<PremiumMessages> MOVE_EXPIRED = MessageDefinition.<PremiumMessages>builder()
			.id("premium:move-expired")
			.text(messages -> messages.getCommands().getPremium().getExpired())
			.build();

	/**
	 * No move waits to be confirmed.
	 */
	public static final MessageDefinition<PremiumMessages> MOVE_NO_PENDING = MessageDefinition.<PremiumMessages>builder()
			.id("premium:move-no-pending")
			.text(messages -> messages.getCommands().getPremium().getNoPending())
			.build();

	/**
	 * A move already waits.
	 */
	public static final MessageDefinition<PremiumMessages> MOVE_PENDING_EXISTS = MessageDefinition.<PremiumMessages>builder()
			.id("premium:move-pending-exists")
			.text(messages -> messages.getCommands().getPremium().getPendingExists())
			.build();

	/**
	 * The account already uses Premium.
	 */
	public static final MessageDefinition<PremiumMessages> MOVE_ALREADY_PRIMARY = MessageDefinition.<PremiumMessages>builder()
			.id("premium:move-already-primary")
			.text(messages -> messages.getCommands().getPremium().getAlreadyPrimary())
			.build();

	/**
	 * Shown after a returning player was recognised.
	 */
	public static final MessageDefinition<PremiumMessages> COMPLETION_RECOGNITION = MessageDefinition.<PremiumMessages>builder()
			.id("premium:completion-recognition")
			.text(messages -> MessageDefinition.lines(messages.getCompletion().getRecognition().getBody()))
			.placeholder("player")
			.placeholder("provider")
			.build();

	/**
	 * Shown after a premium login.
	 */
	public static final MessageDefinition<PremiumMessages> COMPLETION_AUTHENTICATION = MessageDefinition.<PremiumMessages>builder()
			.id("premium:completion-authentication")
			.text(messages -> MessageDefinition.lines(messages.getCompletion().getAuthentication().getBody()))
			.placeholder("player")
			.placeholder("provider")
			.build();

	/**
	 * Shown after a registration.
	 */
	public static final MessageDefinition<PremiumMessages> COMPLETION_REGISTRATION = MessageDefinition.<PremiumMessages>builder()
			.id("premium:completion-registration")
			.text(messages -> MessageDefinition.lines(messages.getCompletion().getRegistration().getBody()))
			.placeholder("player")
			.placeholder("provider")
			.build();

	/**
	 * Shown after an account moved to Premium.
	 */
	public static final MessageDefinition<PremiumMessages> COMPLETION_MIGRATION = MessageDefinition.<PremiumMessages>builder()
			.id("premium:completion-migration")
			.text(messages -> MessageDefinition.lines(messages.getCompletion().getMigration().getBody()))
			.placeholder("player")
			.placeholder("provider")
			.build();

	/**
	 * Returns every message of Premium to players.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<PremiumMessages>> all() {
		return List.of(
				VERIFICATION_CODE,
				VERIFICATION_REQUIRED,
				VERIFICATION_UNAVAILABLE,
				REJOIN,
				OWNED_USERNAME,
				MOVE_CONFIRM,
				MOVE_CONFIRMED,
				MOVE_VERIFICATION_REQUIRED,
				MOVE_CANCELLED,
				MOVE_EXPIRED,
				MOVE_NO_PENDING,
				MOVE_PENDING_EXISTS,
				MOVE_ALREADY_PRIMARY,
				COMPLETION_RECOGNITION,
				COMPLETION_AUTHENTICATION,
				COMPLETION_REGISTRATION,
				COMPLETION_MIGRATION
		);
	}
}

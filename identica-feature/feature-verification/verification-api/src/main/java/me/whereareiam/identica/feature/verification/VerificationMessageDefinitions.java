package me.whereareiam.identica.feature.verification;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.model.message.MessageDefinition;
import me.whereareiam.identica.model.message.Prompt;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

/**
 * Verification's messages to players and the prompt every step that asks for a code shares. Their texts sit in
 * verification's {@code messages.yml}; a provider that asks for a code words the question in its own messages and
 * gives its message {@link #CODE_PROMPT}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VerificationMessageDefinitions {
	/**
	 * What a step that verifies a player asks for: a code from their authenticator app, or a recovery code.
	 */
	public static final Prompt CODE_PROMPT = Prompt.builder()
			.id("verification:code")
			.field(Prompt.Field.of("code", Prompt.FieldType.CODE))
			.command("verification-confirm")
			.build();

	/**
	 * What an enrollment that waits for its first code asks for: a code from the authenticator app the player is
	 * setting up, answered with the command that confirms enrollments.
	 */
	public static final Prompt ENROLLMENT_CODE_PROMPT = Prompt.builder()
			.id("verification:enrollment-code")
			.field(Prompt.Field.of("code", Prompt.FieldType.CODE))
			.command("verification-enroll-confirm")
			.build();

	/**
	 * A step asked for a code and the player gave a wrong one; asks for it again.
	 */
	public static final MessageDefinition<VerificationMessages> CODE_INVALID = MessageDefinition.<VerificationMessages>builder()
			.id("verification:code-invalid")
			.text(messages -> messages.getCommands().getConfirm().getInvalidCode())
			.prompt(CODE_PROMPT)
			.build();

	/**
	 * A step asked for a code, but no challenge was left to take the one the player gave; asks for it again.
	 */
	public static final MessageDefinition<VerificationMessages> CODE_NO_CHALLENGE = MessageDefinition.<VerificationMessages>builder()
			.id("verification:code-no-challenge")
			.text(messages -> messages.getCommands().getConfirm().getNoPending())
			.prompt(CODE_PROMPT)
			.build();

	/**
	 * The status of the player's methods, rendered from {@code commands.status}.
	 */
	public static final MessageDefinition<VerificationMessages> STATUS = MessageDefinition.<VerificationMessages>builder()
			.id("verification:status")
			.text(messages -> MessageDefinition.lines(messages.getCommands().getStatus().getBody()))
			.build();

	/**
	 * The recovery codes of a newly enrolled method, rendered from {@code commands.confirm.recoveryCodes}.
	 */
	public static final MessageDefinition<VerificationMessages> RECOVERY_CODES = MessageDefinition.<VerificationMessages>builder()
			.id("verification:recovery-codes")
			.text(messages -> MessageDefinition.lines(messages.getCommands().getConfirm().getRecoveryCodes().getBody()))
			.build();

	/**
	 * The secret and instructions of an authenticator app enrollment, waiting for its first code.
	 */
	public static final MessageDefinition<VerificationMessages> TOTP_PENDING = MessageDefinition.<VerificationMessages>builder()
			.id("verification:totp-pending")
			.text(messages -> MessageDefinition.lines(messages.getMethods().getTotp().getPending()))
			.prompt(ENROLLMENT_CODE_PROMPT)
			.build();

	/**
	 * Only a player can use this command.
	 */
	public static final MessageDefinition<VerificationMessages> PLAYER_ONLY = text("verification:player-only", messages -> messages.getCommands().getPlayerOnly());

	/**
	 * The player may not change verification for this account.
	 */
	public static final MessageDefinition<VerificationMessages> NOT_ALLOWED = text("verification:not-allowed", messages -> messages.getCommands().getNotAllowed());

	/**
	 * No verification method has this name; placeholder {@code method}.
	 */
	public static final MessageDefinition<VerificationMessages> ENROLL_UNKNOWN_METHOD = text("verification:enroll-unknown-method", messages -> messages.getCommands().getEnroll().getUnknownMethod(), "method");

	/**
	 * The method is already enrolled; placeholder {@code method}.
	 */
	public static final MessageDefinition<VerificationMessages> ENROLL_ALREADY_ENROLLED = text("verification:enroll-already-enrolled", messages -> messages.getCommands().getEnroll().getAlreadyEnrolled(), "method");

	/**
	 * Nothing waits for a code.
	 */
	public static final MessageDefinition<VerificationMessages> CONFIRM_NO_PENDING = text("verification:confirm-no-pending", messages -> messages.getCommands().getConfirm().getNoPending());

	/**
	 * The code was wrong.
	 */
	public static final MessageDefinition<VerificationMessages> CONFIRM_INVALID_CODE = text("verification:confirm-invalid-code", messages -> messages.getCommands().getConfirm().getInvalidCode());

	/**
	 * A protected action needs a selected method first.
	 */
	public static final MessageDefinition<VerificationMessages> CONFIRM_SELECTION_REQUIRED = text("verification:confirm-selection-required", messages -> messages.getCommands().getConfirm().getProtectedActionSelectionRequired());

	/**
	 * A protected action needs the player's current session.
	 */
	public static final MessageDefinition<VerificationMessages> CONFIRM_SESSION_REQUIRED = text("verification:confirm-session-required", messages -> messages.getCommands().getConfirm().getProtectedActionSessionRequired());

	/**
	 * The method cannot be used for the provider; placeholders {@code method} and {@code provider}.
	 */
	public static final MessageDefinition<VerificationMessages> CONFIRM_METHOD_UNAVAILABLE = text("verification:confirm-method-unavailable", messages -> messages.getCommands().getConfirm().getMethodUnavailable(), "method", "provider");

	/**
	 * The method is enabled; placeholder {@code method}.
	 */
	public static final MessageDefinition<VerificationMessages> CONFIRM_ENABLED = text("verification:confirm-enabled", messages -> messages.getCommands().getConfirm().getEnabled(), "method");

	/**
	 * The method was selected for the provider; placeholders {@code method} and {@code provider}.
	 */
	public static final MessageDefinition<VerificationMessages> CONFIRM_AUTO_SELECTED = text("verification:confirm-auto-selected", messages -> messages.getCommands().getConfirm().getAutoSelected(), "method", "provider");

	/**
	 * No provider has this ID; placeholder {@code provider}.
	 */
	public static final MessageDefinition<VerificationMessages> USE_PROVIDER_NOT_FOUND = text("verification:use-provider-not-found", messages -> messages.getCommands().getUse().getProviderNotFound(), "provider");

	/**
	 * The provider does not support verification; placeholder {@code provider}.
	 */
	public static final MessageDefinition<VerificationMessages> USE_PROVIDER_UNSUPPORTED = text("verification:use-provider-unsupported", messages -> messages.getCommands().getUse().getProviderUnsupported(), "provider");

	/**
	 * Verification is turned off for the provider; placeholder {@code provider}.
	 */
	public static final MessageDefinition<VerificationMessages> USE_PROVIDER_VERIFICATION_DISABLED = text("verification:use-provider-verification-disabled", messages -> messages.getCommands().getUse().getProviderVerificationDisabled(), "provider");

	/**
	 * The method is not enrolled; placeholder {@code method}.
	 */
	public static final MessageDefinition<VerificationMessages> USE_METHOD_NOT_ENROLLED = text("verification:use-method-not-enrolled", messages -> messages.getCommands().getUse().getMethodNotEnrolled(), "method");

	/**
	 * The method is turned off for the provider; placeholders {@code method} and {@code provider}.
	 */
	public static final MessageDefinition<VerificationMessages> USE_METHOD_DISABLED_FOR_PROVIDER = text("verification:use-method-disabled-for-provider", messages -> messages.getCommands().getUse().getMethodDisabledForProvider(), "method", "provider");

	/**
	 * The method is already selected for the provider; placeholders {@code method} and {@code provider}.
	 */
	public static final MessageDefinition<VerificationMessages> USE_ALREADY_SELECTED = text("verification:use-already-selected", messages -> messages.getCommands().getUse().getAlreadySelected(), "method", "provider");

	/**
	 * The method is now selected for the provider; placeholders {@code method} and {@code provider}.
	 */
	public static final MessageDefinition<VerificationMessages> USE_UPDATED = text("verification:use-updated", messages -> messages.getCommands().getUse().getUpdated(), "method", "provider");

	/**
	 * The method to turn off is not enrolled; placeholder {@code method}.
	 */
	public static final MessageDefinition<VerificationMessages> DISABLE_METHOD_NOT_ENROLLED = text("verification:disable-method-not-enrolled", messages -> messages.getCommands().getDisable().getMethodNotEnrolled(), "method");

	/**
	 * Turning the method off needs a code first; placeholder {@code method}.
	 */
	public static final MessageDefinition<VerificationMessages> DISABLE_PROTECTED_PROMPT = text("verification:disable-protected-prompt", messages -> messages.getCommands().getDisable().getProtectedPrompt(), "method");

	/**
	 * The method is turned off; placeholder {@code method}.
	 */
	public static final MessageDefinition<VerificationMessages> DISABLE_DISABLED = text("verification:disable-disabled", messages -> messages.getCommands().getDisable().getDisabled(), "method");

	/**
	 * Nothing waits to be cancelled.
	 */
	public static final MessageDefinition<VerificationMessages> CANCEL_NO_PENDING = text("verification:cancel-no-pending", messages -> messages.getCommands().getCancel().getNoPending());

	/**
	 * The pending enrollment was cancelled.
	 */
	public static final MessageDefinition<VerificationMessages> CANCEL_CANCELLED = text("verification:cancel-cancelled", messages -> messages.getCommands().getCancel().getCancelled());

	/**
	 * The pending protected action was cancelled.
	 */
	public static final MessageDefinition<VerificationMessages> CANCEL_CANCELLED_PROTECTED_ACTION = text("verification:cancel-cancelled-protected-action", messages -> messages.getCommands().getCancel().getCancelledProtectedAction());

	/**
	 * Returns every message of verification.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<VerificationMessages>> all() {
		return List.of(
				CODE_INVALID,
				CODE_NO_CHALLENGE,
				STATUS,
				RECOVERY_CODES,
				TOTP_PENDING,
				PLAYER_ONLY,
				NOT_ALLOWED,
				ENROLL_UNKNOWN_METHOD,
				ENROLL_ALREADY_ENROLLED,
				CONFIRM_NO_PENDING,
				CONFIRM_INVALID_CODE,
				CONFIRM_SELECTION_REQUIRED,
				CONFIRM_SESSION_REQUIRED,
				CONFIRM_METHOD_UNAVAILABLE,
				CONFIRM_ENABLED,
				CONFIRM_AUTO_SELECTED,
				USE_PROVIDER_NOT_FOUND,
				USE_PROVIDER_UNSUPPORTED,
				USE_PROVIDER_VERIFICATION_DISABLED,
				USE_METHOD_NOT_ENROLLED,
				USE_METHOD_DISABLED_FOR_PROVIDER,
				USE_ALREADY_SELECTED,
				USE_UPDATED,
				DISABLE_METHOD_NOT_ENROLLED,
				DISABLE_PROTECTED_PROMPT,
				DISABLE_DISABLED,
				CANCEL_NO_PENDING,
				CANCEL_CANCELLED,
				CANCEL_CANCELLED_PROTECTED_ACTION
		);
	}

	private static @NotNull MessageDefinition<VerificationMessages> text(
			@NotNull String id,
			@NotNull Function<VerificationMessages, String> text,
			@NotNull String... placeholders
	) {
		return MessageDefinition.<VerificationMessages>builder()
				.id(id)
				.text(text)
				.placeholders(List.of(placeholders))
				.build();
	}
}

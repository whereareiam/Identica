package me.whereareiam.identica.feature.verification.command.executor;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.feature.verification.VerificationMessageDefinitions;
import me.whereareiam.identica.feature.verification.VerificationRegistry;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.VerificationDisableResult;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollmentResult;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessDisplay;
import me.whereareiam.identica.feature.verification.model.selection.VerificationSelectionResult;
import me.whereareiam.identica.feature.verification.type.status.VerificationDisableStatus;
import me.whereareiam.identica.feature.verification.type.status.VerificationEnrollmentStatus;
import me.whereareiam.identica.feature.verification.type.status.VerificationSelectionStatus;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.message.MessageDefinition;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Singleton
public class VerificationResultRenderer {
	private final Provider<VerificationMessages> messagesProvider;
	private final VerificationRegistry verificationRegistry;
	private final MessageService messageService;

	@Inject
	public VerificationResultRenderer(
			Provider<VerificationMessages> messagesProvider,
			VerificationRegistry verificationRegistry,
			MessageService messageService
	) {
		this.messagesProvider = messagesProvider;
		this.verificationRegistry = verificationRegistry;
		this.messageService = messageService;
	}

	public void presentEnrollmentResult(@NotNull Actor sender, @NotNull VerificationEnrollmentResult result) {
		VerificationMessages.Commands messages = verificationMessages();
		VerificationMessages.Commands.Enroll enrollMessages = messages.getEnroll();
		VerificationMessages.Commands.Confirm confirmMessages = messages.getConfirm();
		VerificationEnrollmentStatus status = result.getStatus();
		if (status == null) return;

		switch (status) {
			case STARTED -> sendEnrollmentDisplay(sender, result);
			case ACTIVATED -> {
				send(sender, VerificationMessageDefinitions.CONFIRM_ENABLED, methodPlaceholders(result.getMethodId()));
				if (result.getAutoSelectedProviderId() != null && !result.getAutoSelectedProviderId().isBlank()) {
					Map<String, String> placeholders = new HashMap<>(methodPlaceholders(result.getMethodId()));
					placeholders.put("provider", Objects.toString(result.getAutoSelectedProviderId(), ""));
					send(sender, VerificationMessageDefinitions.CONFIRM_AUTO_SELECTED, placeholders);
				}
			}
			case NO_PENDING -> send(sender, VerificationMessageDefinitions.CONFIRM_NO_PENDING, Map.of());
			case UNKNOWN_METHOD -> send(sender, VerificationMessageDefinitions.ENROLL_UNKNOWN_METHOD, methodPlaceholders(result.getMethodId()));
			case ALREADY_ENROLLED -> send(sender, VerificationMessageDefinitions.ENROLL_ALREADY_ENROLLED, methodPlaceholders(result.getMethodId()));
			case METHOD_UNAVAILABLE -> send(sender, VerificationMessageDefinitions.CONFIRM_METHOD_UNAVAILABLE, methodAndProviderPlaceholders(result.getMethodId(), result.getProviderId()));
			case NOT_ALLOWED -> send(sender, VerificationMessageDefinitions.NOT_ALLOWED, Map.of());
			case WAITING -> {
				if (result.getRecoveryCodes() != null && !result.getRecoveryCodes().isEmpty()) {
					sendRecoveryCodes(sender, result.getRecoveryCodes());
				} else {
					sendEnrollmentDisplay(sender, result);
				}
			}
			case INVALID -> send(sender, VerificationMessageDefinitions.CONFIRM_INVALID_CODE, Map.of());
		}
	}

	public void presentSelectionResult(@NotNull Actor sender, @NotNull VerificationSelectionResult result) {
		VerificationMessages.Commands messages = verificationMessages();
		VerificationMessages.Commands.Use useMessages = messages.getUse();
		VerificationSelectionStatus status = result.getStatus();
		if (status == null) return;

		switch (status) {
			case UPDATED -> send(sender, VerificationMessageDefinitions.USE_UPDATED, methodAndProviderPlaceholders(result.getMethodId(), result.getProviderId()));
			case ALREADY_SELECTED -> send(sender, VerificationMessageDefinitions.USE_ALREADY_SELECTED, methodAndProviderPlaceholders(result.getMethodId(), result.getProviderId()));
			case PROVIDER_NOT_FOUND -> send(sender, VerificationMessageDefinitions.USE_PROVIDER_NOT_FOUND, Map.of("provider", Objects.toString(result.getProviderId(), "")));
			case PROVIDER_UNSUPPORTED -> send(sender, VerificationMessageDefinitions.USE_PROVIDER_UNSUPPORTED, Map.of("provider", Objects.toString(result.getProviderId(), "")));
			case PROVIDER_VERIFICATION_DISABLED -> send(sender, VerificationMessageDefinitions.USE_PROVIDER_VERIFICATION_DISABLED, Map.of("provider", Objects.toString(result.getProviderId(), "")));
			case METHOD_NOT_ENROLLED -> send(sender, VerificationMessageDefinitions.USE_METHOD_NOT_ENROLLED, methodPlaceholders(result.getMethodId()));
			case METHOD_DISABLED_FOR_PROVIDER -> send(sender, VerificationMessageDefinitions.USE_METHOD_DISABLED_FOR_PROVIDER, methodAndProviderPlaceholders(result.getMethodId(), result.getProviderId()));
			case NOT_ALLOWED -> send(sender, VerificationMessageDefinitions.NOT_ALLOWED, Map.of());
		}
	}

	public void presentDisableResult(@NotNull Actor sender, @NotNull VerificationDisableResult result) {
		VerificationMessages.Commands.Disable disableMessages = verificationMessages().getDisable();
		VerificationDisableStatus status = result.getStatus();
		if (status == null) return;

		switch (status) {
			case DISABLED -> send(sender, VerificationMessageDefinitions.DISABLE_DISABLED, methodPlaceholders(result.getMethodId()));
			case METHOD_NOT_ENROLLED -> send(sender, VerificationMessageDefinitions.DISABLE_METHOD_NOT_ENROLLED, methodPlaceholders(result.getMethodId()));
		}
	}

	public void presentDisablePrompt(@NotNull Actor sender, @Nullable String methodId) {
		send(sender, VerificationMessageDefinitions.DISABLE_PROTECTED_PROMPT, methodPlaceholders(methodId));
	}

	private void sendRecoveryCodes(@NotNull Actor sender, @Nullable List<String> recoveryCodes) {
		VerificationMessages.Commands.Confirm.RecoveryCodes messages = verificationMessages().getConfirm().getRecoveryCodes();
		List<String> entries = buildRecoveryCodeLines(messages, recoveryCodes);
		// The codes are shown this once and cannot be read again, so they do not depend on the body
		// having text or naming them: without their placeholder they are sent on their own.
		String codes = String.join("\n", entries);
		String token = placeholderFormat().format("entries");
		if (messages.getBody().stream().anyMatch(line -> line != null && line.contains(token))) {
			send(sender, VerificationMessageDefinitions.RECOVERY_CODES, Map.of("entries", codes));
			return;
		}

		send(sender, VerificationMessageDefinitions.RECOVERY_CODES, Map.of());
		send(sender, VerificationMessageDefinitions.RECOVERY_CODES.create(messagesProvider.get()).toBuilder().text(codes).build());
	}

	private List<String> buildRecoveryCodeLines(
			VerificationMessages.Commands.Confirm.RecoveryCodes messages,
			@Nullable List<String> recoveryCodes
	) {
		if (recoveryCodes == null || recoveryCodes.isEmpty())
			return List.of(messages.getEmpty());

		List<String> entries = new ArrayList<>();
		for (String recoveryCode : recoveryCodes) {
			Map<String, String> placeholders = new HashMap<>();
			placeholders.put("code", Objects.toString(recoveryCode, ""));
			entries.add(formatEntry(messages.getSingleColumnEntry(), placeholders));
		}

		if (messages.getLayout() == VerificationMessages.Commands.Confirm.RecoveryCodes.Layout.SINGLE_COLUMN)
			return entries;

		VerificationMessages.Commands.EntryFormat twoColumnEntry = messages.getTwoColumnEntry();
		if (twoColumnEntry.getFormat().isBlank())
			return entries;

		List<String> rows = new ArrayList<>();
		for (int index = 0; index < entries.size(); index += 2) {
			Map<String, String> placeholders = new HashMap<>();
			placeholders.put("left", recoveryCodes.get(index));
			placeholders.put("right", index + 1 < recoveryCodes.size() ? recoveryCodes.get(index + 1) : "");
			rows.add(formatEntry(twoColumnEntry, placeholders));
		}

		return rows;
	}

	private String formatEntry(VerificationMessages.Commands.EntryFormat format, Map<String, String> placeholders) {
		boolean complete = placeholders.values().stream().allMatch(value -> value != null && !value.isBlank());
		String result = complete ? format.getFormat() : format.getEmptyFormat();
		SerializerOptions.PlaceholderFormat placeholderFormat = placeholderFormat();
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			result = result.replace(placeholderFormat.format(entry.getKey()), Objects.toString(entry.getValue(), ""));
		}

		return result;
	}

	private VerificationMessages.Commands verificationMessages() {
		return messagesProvider.get().getCommands();
	}

	private SerializerOptions.PlaceholderFormat placeholderFormat() {
		return Serializer.getEngine().getPlaceholderFormat();
	}

	private void sendEnrollmentDisplay(@NotNull Actor sender, @NotNull VerificationEnrollmentResult result) {
		Map<String, String> placeholders = enrollmentPlaceholders(result);
		VerificationProcessDisplay display = result.getDisplay();
		if (display != null) {
			// A display whose text was emptied shows nothing; the enrollment itself is unaffected.
			PlayerMessage message = display.getMessage();
			if (message != null)
				send(sender, message.toBuilder().placeholders(merged(message.getPlaceholders(), placeholders)).build());
			return;
		}

		String methodId = Objects.toString(result.getMethodId(), "");
		Logger.severe("Verification enrollment display missing for method=%s status=%s",
				methodId,
				result.getStatus());
		throw new IllegalStateException("Verification method " + methodId + " did not provide enrollment display");
	}

	private void send(
			@NotNull Actor sender,
			@NotNull MessageDefinition<VerificationMessages> definition,
			@NotNull Map<String, String> placeholders
	) {
		send(sender, definition.create(messagesProvider.get(), placeholders));
	}

	private void send(@NotNull Actor sender, @NotNull PlayerMessage message) {
		if (message.isEmpty()) return;
		if (sender instanceof Identity identity) {
			messageService.send(identity, message);
			return;
		}

		sender.sendMessage(Serializer.serialize(sender, message.getText(), message.getPlaceholders()));
	}

	private static @NotNull Map<String, String> merged(@NotNull Map<String, String> first, @NotNull Map<String, String> second) {
		Map<String, String> merged = new LinkedHashMap<>(first);
		merged.putAll(second);

		return merged;
	}

	private Map<String, String> methodPlaceholders(@Nullable String methodId) {
		String safeMethodId = Objects.toString(methodId, "");
		String displayName = displayMethod(methodId);
		Map<String, String> placeholders = new HashMap<>();
		placeholders.put("methodDisplayName", displayName);
		placeholders.put("methodId", safeMethodId);
		return placeholders;
	}

	private Map<String, String> methodAndProviderPlaceholders(@Nullable String methodId, @Nullable String providerId) {
		Map<String, String> placeholders = new HashMap<>(methodPlaceholders(methodId));
		placeholders.put("provider", Objects.toString(providerId, ""));
		return placeholders;
	}

	private String displayMethod(@Nullable String methodId) {
		if (methodId == null || methodId.isBlank()) return "";
		return verificationRegistry.find(methodId)
				.map(method -> method.descriptor().getDisplayName())
				.filter(value -> !value.isBlank())
				.orElse(methodId);
	}

	private Map<String, String> enrollmentPlaceholders(@NotNull VerificationEnrollmentResult result) {
		Map<String, String> placeholders = new LinkedHashMap<>(methodPlaceholders(result.getMethodId()));
		VerificationProcessDisplay display = result.getDisplay();
		if (display != null) placeholders.putAll(display.getPlaceholders());
		if (result.getMethodData() != null)
			placeholders.putAll(result.getMethodData());

		String uri = firstNonBlank(placeholders.get("uri"), placeholders.get("otpauthUri"));
		if (uri == null) {
			placeholders.putIfAbsent("uriEncoded", "");
			return placeholders;
		}

		placeholders.putIfAbsent("uri", uri);
		placeholders.putIfAbsent("otpauthUri", uri);
		placeholders.put("uriEncoded", encodeUrlParameter(uri));
		return placeholders;
	}

	private String firstNonBlank(@Nullable String first, @Nullable String second) {
		if (first != null && !first.isBlank()) return first;
		if (second != null && !second.isBlank()) return second;
		return null;
	}

	private String encodeUrlParameter(@Nullable String value) {
		if (value == null || value.isBlank()) return "";
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}

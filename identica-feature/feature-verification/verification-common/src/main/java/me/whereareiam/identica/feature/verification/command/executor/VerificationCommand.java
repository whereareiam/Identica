package me.whereareiam.identica.feature.verification.command.executor;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.command.SessionBoundCommand;
import me.whereareiam.identica.feature.verification.VerificationRegistry;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.feature.verification.model.selection.VerificationSelection;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.type.DateTimePattern;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Singleton
public class VerificationCommand extends SessionBoundCommand {
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

	private final Provider<Messages> coreMessagesProvider;
	private final Provider<VerificationMessages> messagesProvider;
	private final VerificationService verificationService;
	private final VerificationRegistry verificationRegistry;
	private final SessionService sessionService;

	@Inject
	public VerificationCommand(
			Provider<Messages> coreMessagesProvider,
			Provider<VerificationMessages> messagesProvider,
			VerificationService verificationService,
			VerificationRegistry verificationRegistry,
			SessionService sessionService
	) {
		this.coreMessagesProvider = coreMessagesProvider;
		this.messagesProvider = messagesProvider;
		this.verificationService = verificationService;
		this.verificationRegistry = verificationRegistry;
		this.sessionService = sessionService;
	}

	@Override
	protected @NotNull SessionService sessionService() {
		return sessionService;
	}

	@Override
	protected @Nullable String currentSessionRequiredMessage() {
		return coreMessagesProvider.get().getCommands().getCurrentSessionRequired();
	}

	@Definition("verification")
	@Command("2fa")
	public void root(@NotNull Actor sender) {
		status(sender);
	}

	@Definition("verification-status")
	@Command("2fa status")
	public void status(@NotNull Actor sender) {
		Identity identity = requireIdentity(sender, verificationMessages().getPlayerOnly());
		if (identity == null) return;
		if (requireCurrentSession(identity) == null) return;
		var accountUniqueId = requireAccountUniqueId(identity);
		if (accountUniqueId == null) return;

		VerificationMessages.Commands messages = verificationMessages();
		VerificationMessages.Commands.Status statusMessages = messages.getStatus();
		List<VerificationEnrollment> enrollments = verificationService.findEnrollments(accountUniqueId);
		List<VerificationSelection> selections = verificationService.findSelections(accountUniqueId);

		List<String> enrollmentLines = buildEnrollmentLines(statusMessages, enrollments);
		List<String> selectionLines = buildSelectionLines(statusMessages, selections);

		Map<String, String> placeholders = Map.of(
				"enrollments", String.join("\n", enrollmentLines),
				"selections", String.join("\n", selectionLines)
		);
		sendLines(sender, statusMessages.getBody(), placeholders);
	}

	private List<String> buildEnrollmentLines(
			VerificationMessages.Commands.Status messages,
			List<VerificationEnrollment> enrollments
	) {
		if (enrollments == null || enrollments.isEmpty()) return List.of(messages.getEmptyEnrollments());

		List<String> lines = new ArrayList<>();
		for (VerificationEnrollment enrollment : enrollments) {
			Map<String, String> placeholders = new HashMap<>(methodPlaceholders(enrollment.getMethodId()));
			placeholders.put("enabledAt", formatDate(enrollment.getEnabledAt()));
			lines.add(formatEntry(messages.getEnrollmentEntry(), placeholders));
		}

		return lines;
	}

	private List<String> buildSelectionLines(
			VerificationMessages.Commands.Status messages,
			List<VerificationSelection> selections
	) {
		if (selections == null || selections.isEmpty()) return List.of(messages.getEmptySelections());

		List<String> lines = new ArrayList<>();
		for (VerificationSelection selection : selections) {
			Map<String, String> placeholders = new HashMap<>(methodPlaceholders(selection.getMethodId()));
			placeholders.put("provider", Objects.toString(selection.getProviderId(), ""));
			lines.add(formatEntry(messages.getSelectionEntry(), placeholders));
		}

		return lines;
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

	private void sendLines(@NotNull Actor sender, @Nullable List<String> lines, @NotNull Map<String, String> placeholders) {
		if (lines == null || lines.isEmpty()) return;
		sendMessage(sender, String.join("\n", lines), placeholders);
	}

	private void sendMessage(@NotNull Actor sender, @Nullable String message, @NotNull Map<String, String> placeholders) {
		if (message == null || message.isBlank()) return;
		if (sender instanceof Identity identity) {
			identity.send(message, placeholders);
			return;
		}

		sender.sendMessage(Serializer.serialize(SerializerContent.builder()
				.receiver(sender)
				.message(message)
				.placeholders(placeholders)
				.build()));
	}

	private String formatDate(long millis) {
		if (millis <= 0L) return "";
		Messages.Format.Temporal temporal = coreMessagesProvider.get().getFormat().getTemporal();
		DateTimeFormatter dateTimeFormatter = resolveFormatter(
				temporal.getDateTime()
		);
		return dateTimeFormatter.format(Instant.ofEpochMilli(millis));
	}

	private Map<String, String> methodPlaceholders(@Nullable String methodId) {
		String safeMethodId = Objects.toString(methodId, "");
		String displayName = displayMethod(methodId);

		Map<String, String> placeholders = new HashMap<>();
		placeholders.put("methodDisplayName", displayName);
		placeholders.put("methodId", safeMethodId);

		return placeholders;
	}

	private String displayMethod(@Nullable String methodId) {
		if (methodId == null || methodId.isBlank()) return "";
		return verificationRegistry.find(methodId)
				.map(method -> method.descriptor().getDisplayName())
				.filter(value -> !value.isBlank())
				.orElse(methodId);
	}

	private DateTimeFormatter resolveFormatter(DateTimePattern pattern) {
		if (pattern == null) return VerificationCommand.DATE_TIME_FORMATTER;
		return pattern.formatter(VerificationCommand.DATE_TIME_FORMATTER);
	}
}

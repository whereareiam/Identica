package me.whereareiam.identica.provider.password.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.command.ProtectedActionCommand;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.migration.PendingMigration;
import me.whereareiam.identica.model.migration.operation.MigrationCancel;
import me.whereareiam.identica.model.migration.operation.MigrationConfirm;
import me.whereareiam.identica.model.migration.operation.MigrationRequest;
import me.whereareiam.identica.model.migration.operation.MigrationResult;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.password.PasswordConstants;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.service.MigrationService;
import me.whereareiam.identica.type.migration.MigrationCancelScope;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import me.whereareiam.identica.type.provider.ProviderCapability;
import me.whereareiam.identica.verification.VerificationService;
import me.whereareiam.keystone.Actor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PasswordCommand extends ProtectedActionCommand<MigrationRequest> {
	private final Provider<PasswordMessages> messagesProvider;
	private final Provider<Messages> coreMessagesProvider;
	private final MigrationService migrationService;
	private final ProviderManager providerManager;
	private final SessionService sessionService;

	@Inject
	public PasswordCommand(
			Provider<PasswordMessages> messagesProvider,
			Provider<Messages> coreMessagesProvider,
			MigrationService migrationService,
			ProviderManager providerManager,
			VerificationService verificationService,
			SessionService sessionService
	) {
		super(verificationService);
		this.messagesProvider = messagesProvider;
		this.coreMessagesProvider = coreMessagesProvider;
		this.migrationService = migrationService;
		this.providerManager = providerManager;
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

	@Definition("password")
	@Command("password")
	public void password(@NotNull Actor sender) {
		Identity identity = requireIdentity(sender, null);
		if (identity == null) return;

		Session session = requireCurrentSession(identity);
		if (session == null) return;
		if (!supportsMigration()) return;

		MigrationRequest request = MigrationRequest.builder()
				.connectionUniqueId(identity.getUniqueId())
				.targetProviderId(PasswordConstants.PROVIDER_ID)
				.username(identity.getUsername())
				.ip(identity.getIp())
				.initiator(MigrationInitiator.USER)
				.initiatorUniqueId(identity.getUniqueId())
				.build();

		MigrationResult result = migrationService.request(request);

		PasswordMessages.Commands.Password messages = messagesProvider.get().getCommands().getPassword();
		switch (result.getStatus()) {
			case PENDING_CONFIRMATION -> {
				if (!requiresStepUp(identity.getUniqueId())) {
					sendMessage(identity, joinMessage(messages.getConfirm()));
					return;
				}

				StepUpPreparation preparation = prepareStepUp(identity.getUniqueId(), "migration-confirm");
				if (preparation.getStatus() == StepUpPreparation.Status.SELECTION_REQUIRED) {
					sendMessage(identity, coreMessagesProvider.get().getCommands().getVerification().getConfirm().getProtectedActionSelectionRequired());
					return;
				}
				sendMessage(identity, messages.getVerificationRequired());
			}
			case PENDING_EXISTS -> sendMessage(identity, messages.getPendingExists());
			case ALREADY_PRIMARY -> sendMessage(identity, messages.getAlreadyPrimary());
			case PRECHECK_DENIED -> sendMessage(identity, result.getMessage());
			default -> {
			}
		}
	}

	@Definition("password-confirm")
	@Command("password confirm [input]")
	public void confirm(@NotNull Actor sender, @Argument("input") @Nullable String input) {
		Identity identity = requireIdentity(sender, null);
		if (identity == null) return;

		if (requireCurrentSession(identity) == null) return;

		PendingMigration pendingMigration = migrationService.findPendingMigration(identity.getUniqueId()).orElse(null);
		boolean verificationRequired = pendingMigration != null
				&& pendingMigration.getPhase() == PendingMigration.Phase.CONFIRMATION
				&& requiresStepUp(identity.getUniqueId());
		if (verificationRequired) {
			StepUpPreparation preparation = prepareStepUp(identity.getUniqueId(), "migration-confirm");
			if (preparation.getStatus() != StepUpPreparation.Status.READY) {
				switch (preparation.getStatus()) {
					case CURRENT_SESSION_REQUIRED -> sendMessage(identity, coreMessagesProvider.get().getCommands().getVerification().getConfirm().getProtectedActionSessionRequired());
					case SELECTION_REQUIRED -> sendMessage(identity, coreMessagesProvider.get().getCommands().getVerification().getConfirm().getProtectedActionSelectionRequired());
					default -> sendMessage(identity, messagesProvider.get().getCommands().getPassword().getNoPending());
				}
				return;
			}

			if (isBlank(input)) {
				sendMessage(identity, messagesProvider.get().getCommands().getPassword().getVerificationRequired());
				return;
			}

			StepUpResult result = confirmStepUp(identity.getUniqueId(), input, "migration-confirm");
			if (result.getStatus() != StepUpResult.Status.VERIFIED) {
				switch (result.getStatus()) {
					case INVALID_CODE -> sendMessage(identity, coreMessagesProvider.get().getCommands().getVerification().getConfirm().getInvalidCode());
					case CURRENT_SESSION_REQUIRED -> sendMessage(identity, coreMessagesProvider.get().getCommands().getVerification().getConfirm().getProtectedActionSessionRequired());
					case SELECTION_REQUIRED -> sendMessage(identity, coreMessagesProvider.get().getCommands().getVerification().getConfirm().getProtectedActionSelectionRequired());
					default -> sendMessage(identity, messagesProvider.get().getCommands().getPassword().getNoPending());
				}
				return;
			}
		}

		PasswordMessages.Commands.Password messages = messagesProvider.get().getCommands().getPassword();
		var result = migrationService.confirm(MigrationConfirm.builder()
				.connectionUniqueId(identity.getUniqueId())
				.kickMessage(joinMessage(messages.getConfirmed()))
				.build());

		if (result.getStatus() == MigrationResultStatus.NO_PENDING) {
			sendMessage(identity, messages.getNoPending());
			return;
		}

		if (result.getStatus() == MigrationResultStatus.EXPIRED) {
			sendMessage(identity, messages.getExpired());
			return;
		}

		if (result.getStatus() == MigrationResultStatus.ALREADY_PRIMARY) {
			sendMessage(identity, messages.getAlreadyPrimary());
			return;
		}

		if (result.getStatus() == MigrationResultStatus.PRECHECK_DENIED)
			sendMessage(identity, result.getMessage());
	}

	@Definition("password-cancel")
	@Command("password cancel")
	public void cancel(@NotNull Actor sender) {
		Identity identity = requireIdentity(sender, null);
		if (identity == null) return;
		if (requireCurrentSession(identity) == null) return;

		var result = migrationService.cancel(MigrationCancel.builder()
				.connectionUniqueId(identity.getUniqueId())
				.scope(MigrationCancelScope.CONFIRMATION)
				.build());

		PasswordMessages.Commands.Password messages = messagesProvider.get().getCommands().getPassword();
		if (result.getStatus() == MigrationResultStatus.CANCELLED) {
			sendMessage(identity, messages.getCancelled());
			return;
		}

		sendMessage(identity, messages.getNoPending());
	}

	private boolean supportsMigration() {
		return providerManager.findProviders(ProviderCapability.MIGRATION).stream()
				.anyMatch(provider -> provider != null
						&& provider.getDescriptor() != null
						&& PasswordConstants.PROVIDER_ID.equalsIgnoreCase(provider.getDescriptor().getId()));
	}

	private void sendMessage(@NotNull Identity identity, String message) {
		if (message == null || message.isBlank()) return;
		identity.sendMessage(Serializer.serialize(identity, message));
	}

	private String joinMessage(List<String> lines) {
		return lines == null ? "" : String.join("\n", lines);
	}

	private boolean isBlank(@Nullable String input) {
		return input == null || input.isBlank();
	}
}

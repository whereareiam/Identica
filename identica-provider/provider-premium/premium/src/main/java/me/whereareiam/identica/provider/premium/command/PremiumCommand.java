package me.whereareiam.identica.provider.premium.command;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.command.ProtectedActionCommand;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.migration.PendingMigration;
import me.whereareiam.identica.model.migration.operation.MigrationCancel;
import me.whereareiam.identica.model.migration.operation.MigrationConfirm;
import me.whereareiam.identica.model.migration.operation.MigrationRequest;
import me.whereareiam.identica.model.migration.operation.MigrationResult;
import me.whereareiam.identica.provider.premium.PremiumConstants;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.service.MigrationService;
import me.whereareiam.identica.type.migration.MigrationCancelScope;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import me.whereareiam.keystone.Actor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class PremiumCommand extends ProtectedActionCommand<MigrationRequest> {
	private final MigrationService migrationService;
	private final Provider<PremiumMessages> messagesProvider;
	private final Provider<Messages> coreMessagesProvider;
	private final Provider<VerificationMessages> verificationMessagesProvider;
	private final SessionService sessionService;

	@Inject
	public PremiumCommand(
			MigrationService migrationService,
			Provider<PremiumMessages> messagesProvider,
			Provider<Messages> coreMessagesProvider,
			@NotNull Injector injector,
			SessionService sessionService
	) {
		super(injector.getInstance(VerificationService.class));
		this.migrationService = migrationService;
		this.messagesProvider = messagesProvider;
		this.coreMessagesProvider = coreMessagesProvider;
		this.verificationMessagesProvider = () -> injector.getInstance(VerificationMessages.class);
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

	@Command("premium")
	@Definition("premium")
	public void onCommand(@NotNull Actor sender) {
		Identity identity = requireIdentity(sender, null);
		if (identity == null) return;
		Session session = requireCurrentSession(identity);
		if (session == null) return;
		UUID accountUniqueId = requireAccountUniqueId(identity);
		if (accountUniqueId == null) return;

		MigrationRequest request = MigrationRequest.builder()
				.connectionUniqueId(identity.getConnectionUniqueId())
				.accountUniqueId(accountUniqueId)
				.targetProviderId(PremiumConstants.PROVIDER_ID)
				.username(identity.getUsername())
				.ip(identity.getIp())
				.initiator(MigrationInitiator.USER)
				.initiatorUniqueId(identity.getConnectionUniqueId())
				.build();

		MigrationResult result = migrationService.request(request);
		PremiumMessages.Commands.Premium messages = messagesProvider.get().getCommands().getPremium();
		if (result.getStatus() == MigrationResultStatus.PENDING_CONFIRMATION) {
			if (!requiresStepUp(accountUniqueId)) {
				sendMessage(identity, joinMessage(messages.getConfirm()));
				return;
			}

			StepUpPreparation preparation = prepareStepUp(accountUniqueId, "migration-confirm");
			if (preparation.getStatus() == StepUpPreparation.Status.SELECTION_REQUIRED) {
				sendMessage(identity, verificationMessagesProvider.get().getCommands().getConfirm().getProtectedActionSelectionRequired());
				return;
			}
			sendMessage(identity, messages.getVerificationRequired());
			return;
		}
		if (result.getStatus() == MigrationResultStatus.PENDING_EXISTS) {
			sendMessage(identity, messages.getPendingExists());
			return;
		}
		if (result.getStatus() == MigrationResultStatus.ALREADY_PRIMARY) {
			sendMessage(identity, messages.getAlreadyPrimary());
			return;
		}
		if (result.getStatus() == MigrationResultStatus.PRECHECK_DENIED && result.getMessage() != null) {
			sendMessage(identity, result.getMessage());
		}
	}

	@Definition("premium-confirm")
	@Command("premium confirm [input]")
	public void confirm(@NotNull Actor sender, @Argument("input") @Nullable String input) {
		Identity identity = requireIdentity(sender, null);
		if (identity == null) return;
		if (requireCurrentSession(identity) == null) return;
		UUID accountUniqueId = requireAccountUniqueId(identity);
		if (accountUniqueId == null) return;

		PendingMigration pendingMigration = migrationService.findPendingMigration(identity.getConnectionUniqueId()).orElse(null);
		boolean verificationRequired = pendingMigration != null
				&& pendingMigration.getPhase() == PendingMigration.Phase.CONFIRMATION
				&& requiresStepUp(accountUniqueId);
		if (verificationRequired) {
			StepUpPreparation preparation = prepareStepUp(accountUniqueId, "migration-confirm");
			if (preparation.getStatus() != StepUpPreparation.Status.READY) {
				switch (preparation.getStatus()) {
					case CURRENT_SESSION_REQUIRED -> sendMessage(identity, verificationMessagesProvider.get().getCommands().getConfirm().getProtectedActionSessionRequired());
					case SELECTION_REQUIRED -> sendMessage(identity, verificationMessagesProvider.get().getCommands().getConfirm().getProtectedActionSelectionRequired());
					default -> sendMessage(identity, messagesProvider.get().getCommands().getPremium().getNoPending());
				}
				return;
			}

			if (isBlank(input)) {
				sendMessage(identity, messagesProvider.get().getCommands().getPremium().getVerificationRequired());
				return;
			}

			StepUpResult result = confirmStepUp(accountUniqueId, input, "migration-confirm");
			if (result.getStatus() != StepUpResult.Status.VERIFIED) {
				switch (result.getStatus()) {
					case INVALID_CODE -> sendMessage(identity, verificationMessagesProvider.get().getCommands().getConfirm().getInvalidCode());
					case CURRENT_SESSION_REQUIRED -> sendMessage(identity, verificationMessagesProvider.get().getCommands().getConfirm().getProtectedActionSessionRequired());
					case SELECTION_REQUIRED -> sendMessage(identity, verificationMessagesProvider.get().getCommands().getConfirm().getProtectedActionSelectionRequired());
					default -> sendMessage(identity, messagesProvider.get().getCommands().getPremium().getNoPending());
				}
				return;
			}
		}

		var result = migrationService.confirm(MigrationConfirm.builder()
				.connectionUniqueId(identity.getConnectionUniqueId())
				.kickMessage(joinMessage(resolveKickMessage()))
				.build());

		PremiumMessages.Commands.Premium messages = messagesProvider.get().getCommands().getPremium();
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
		if (result.getStatus() == MigrationResultStatus.PRECHECK_DENIED && result.getMessage() != null)
			sendMessage(identity, result.getMessage());
	}

	@Command("premium cancel")
	@Definition("premium-cancel")
	public void cancel(@NotNull Actor sender) {
		Identity identity = requireIdentity(sender, null);
		if (identity == null) return;
		if (requireCurrentSession(identity) == null) return;

		var result = migrationService.cancel(MigrationCancel.builder()
				.connectionUniqueId(identity.getConnectionUniqueId())
				.scope(MigrationCancelScope.CONFIRMATION)
				.build());

		PremiumMessages.Commands.Premium messages = messagesProvider.get().getCommands().getPremium();
		if (result.getStatus() == MigrationResultStatus.CANCELLED) {
			sendMessage(identity, messages.getCancelled());
			return;
		}

		sendMessage(identity, messages.getNoPending());
	}

	private List<String> resolveKickMessage() {
		PremiumMessages.Commands.Premium messages = messagesProvider.get().getCommands().getPremium();
		return messages.getConfirmed();
	}

	private void sendMessage(@NotNull Identity sender, @NotNull String message) {
		if (message.isBlank()) return;
		sender.sendMessage(Serializer.serialize(sender, message));
	}

	private String joinMessage(List<String> lines) {
		return lines == null ? "" : String.join("\n", lines);
	}

	private boolean isBlank(@Nullable String input) {
		return input == null || input.isBlank();
	}
}

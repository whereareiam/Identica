package me.whereareiam.identica.provider.premium.command;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.feature.verification.VerificationMessageDefinitions;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.command.ProtectedActionCommand;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.message.SessionMessages;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.migration.PendingMigration;
import me.whereareiam.identica.model.migration.operation.MigrationCancel;
import me.whereareiam.identica.model.migration.operation.MigrationConfirm;
import me.whereareiam.identica.model.migration.operation.MigrationRequest;
import me.whereareiam.identica.model.migration.operation.MigrationResult;
import me.whereareiam.identica.provider.premium.PremiumConstants;
import me.whereareiam.identica.provider.premium.PremiumMessageDefinitions;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.service.MigrationService;
import me.whereareiam.identica.type.migration.MigrationCancelScope;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import me.whereareiam.keystone.Actor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PremiumCommand extends ProtectedActionCommand<MigrationRequest> {
	private final MigrationService migrationService;
	private final Provider<PremiumMessages> messagesProvider;
	private final Provider<Messages> coreMessagesProvider;
	private final Provider<VerificationMessages> verificationMessagesProvider;
	private final SessionService sessionService;
	private final MessageService messageService;

	@Inject
	public PremiumCommand(
			MigrationService migrationService,
			Provider<PremiumMessages> messagesProvider,
			Provider<Messages> coreMessagesProvider,
			@NotNull Injector injector,
			SessionService sessionService,
			MessageService messageService
	) {
		super(injector.getInstance(VerificationService.class));
		this.migrationService = migrationService;
		this.messagesProvider = messagesProvider;
		this.coreMessagesProvider = coreMessagesProvider;
		this.verificationMessagesProvider = () -> injector.getInstance(VerificationMessages.class);
		this.sessionService = sessionService;
		this.messageService = messageService;
	}

	@Override
	protected @NotNull SessionService sessionService() {
		return sessionService;
	}

	@Override
	protected @NotNull MessageService messageService() {
		return messageService;
	}

	@Override
	protected @Nullable PlayerMessage currentSessionRequiredMessage() {
		return SessionMessages.CURRENT_SESSION_REQUIRED.create(coreMessagesProvider.get());
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
		PremiumMessages messages = messagesProvider.get();
		if (result.getStatus() == MigrationResultStatus.PENDING_CONFIRMATION) {
			if (!requiresStepUp(accountUniqueId)) {
				sendMessage(identity, PremiumMessageDefinitions.MOVE_CONFIRM.create(messages));
				return;
			}

			StepUpPreparation preparation = prepareStepUp(accountUniqueId, "migration-confirm");
			if (preparation.getStatus() == StepUpPreparation.Status.SELECTION_REQUIRED) {
				sendMessage(identity, VerificationMessageDefinitions.CONFIRM_SELECTION_REQUIRED.create(verificationMessagesProvider.get()));
				return;
			}
			sendMessage(identity, PremiumMessageDefinitions.MOVE_VERIFICATION_REQUIRED.create(messages));
			return;
		}
		if (result.getStatus() == MigrationResultStatus.PENDING_EXISTS) {
			sendMessage(identity, PremiumMessageDefinitions.MOVE_PENDING_EXISTS.create(messages));
			return;
		}
		if (result.getStatus() == MigrationResultStatus.ALREADY_PRIMARY) {
			sendMessage(identity, PremiumMessageDefinitions.MOVE_ALREADY_PRIMARY.create(messages));
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
					case CURRENT_SESSION_REQUIRED -> sendMessage(identity, VerificationMessageDefinitions.CONFIRM_SESSION_REQUIRED.create(verificationMessagesProvider.get()));
					case SELECTION_REQUIRED -> sendMessage(identity, VerificationMessageDefinitions.CONFIRM_SELECTION_REQUIRED.create(verificationMessagesProvider.get()));
					default -> sendMessage(identity, PremiumMessageDefinitions.MOVE_NO_PENDING.create(messagesProvider.get()));
				}
				return;
			}

			if (isBlank(input)) {
				sendMessage(identity, PremiumMessageDefinitions.MOVE_VERIFICATION_REQUIRED.create(messagesProvider.get()));
				return;
			}

			StepUpResult result = confirmStepUp(accountUniqueId, input, "migration-confirm");
			if (result.getStatus() != StepUpResult.Status.VERIFIED) {
				switch (result.getStatus()) {
					case INVALID_CODE -> sendMessage(identity, VerificationMessageDefinitions.CONFIRM_INVALID_CODE.create(verificationMessagesProvider.get()));
					case CURRENT_SESSION_REQUIRED -> sendMessage(identity, VerificationMessageDefinitions.CONFIRM_SESSION_REQUIRED.create(verificationMessagesProvider.get()));
					case SELECTION_REQUIRED -> sendMessage(identity, VerificationMessageDefinitions.CONFIRM_SELECTION_REQUIRED.create(verificationMessagesProvider.get()));
					default -> sendMessage(identity, PremiumMessageDefinitions.MOVE_NO_PENDING.create(messagesProvider.get()));
				}
				return;
			}
		}

		var result = migrationService.confirm(MigrationConfirm.builder()
				.connectionUniqueId(identity.getConnectionUniqueId())
				.kickMessage(PremiumMessageDefinitions.MOVE_CONFIRMED.create(messagesProvider.get()))
				.build());

		PremiumMessages messages = messagesProvider.get();
		if (result.getStatus() == MigrationResultStatus.NO_PENDING) {
			sendMessage(identity, PremiumMessageDefinitions.MOVE_NO_PENDING.create(messages));
			return;
		}
		if (result.getStatus() == MigrationResultStatus.EXPIRED) {
			sendMessage(identity, PremiumMessageDefinitions.MOVE_EXPIRED.create(messages));
			return;
		}
		if (result.getStatus() == MigrationResultStatus.ALREADY_PRIMARY) {
			sendMessage(identity, PremiumMessageDefinitions.MOVE_ALREADY_PRIMARY.create(messages));
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

		PremiumMessages messages = messagesProvider.get();
		if (result.getStatus() == MigrationResultStatus.CANCELLED) {
			sendMessage(identity, PremiumMessageDefinitions.MOVE_CANCELLED.create(messages));
			return;
		}

		sendMessage(identity, PremiumMessageDefinitions.MOVE_NO_PENDING.create(messages));
	}

	private boolean isBlank(@Nullable String input) {
		return input == null || input.isBlank();
	}
}

package me.whereareiam.identica.common.migration;

import me.whereareiam.identica.common.config.defaults.SettingsDefaults;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.migration.PendingMigration;
import me.whereareiam.identica.model.migration.operation.MigrationConfirm;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.migration.operation.MigrationRequest;
import me.whereareiam.identica.model.migration.operation.MigrationResult;
import me.whereareiam.identica.model.migration.operation.MigrationStart;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.state.PipelineState;
import me.whereareiam.identica.model.pipeline.migration.MigrationPendingState;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.model.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.UsernameSource;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Default Migration Service")
class DefaultMigrationServiceTest {
	@Mock
	private ProviderManager providerManager;
	@Mock
	private ProviderLinkPersistenceService providerLinkPersistenceService;
	@Mock
	private AccountPersistenceService accountPersistenceService;
	@Mock
	private PipelineStateStore pipelineStateStore;
	@Mock
	private SessionService sessionService;
	@Mock
	private IdentityService identityService;

	@DisplayName("Denies migration when the requested username is still occupied")
	@Test
	void deniesMigrationWhenUsernameNotFree() {
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());

		Account existing = Account.builder()
				.uniqueId(UUID.randomUUID())
				.username("Player")
				.source(UsernameSource.SYSTEM)
				.build();
		when(accountPersistenceService.findByUsername("Player")).thenReturn(List.of(existing));

		Messages messages = new Messages();
		Messages.Commands commandsMessages = new Messages.Commands();
		Messages.Commands.Migration migrationMessages = new Messages.Commands.Migration();
		migrationMessages.setLocked("locked");
		commandsMessages.setMigration(migrationMessages);
		messages.setCommands(commandsMessages);

		Commands commands = new Commands();
		Commands.Behavior behavior = new Commands.Behavior();
		Commands.Behavior.Migration migrationBehavior = new Commands.Behavior.Migration();
		migrationBehavior.setConfirmTtl(Duration.ofSeconds(60));
		behavior.setMigration(migrationBehavior);
		behavior.setSuggestions(new Commands.Behavior.Suggestions());
		behavior.setClear(new Commands.Behavior.Clear());
		behavior.setSessions(new Commands.Behavior.Sessions());
		commands.setBehavior(behavior);

		DefaultMigrationService service = new DefaultMigrationService(
				providerManager,
				providerLinkPersistenceService,
				accountPersistenceService,
				pipelineStateStore,
				sessionService,
				identityService,
				Settings::new,
				() -> commands,
				() -> messages
		);

		UUID uniqueId = UUID.randomUUID();
		MigrationResult result = service.start(MigrationStart.builder()
				.uniqueId(uniqueId)
				.connectionUniqueId(uniqueId)
				.username("Player")
				.targetProviderId("provider")
				.build());

		assertEquals(MigrationResultStatus.PRECHECK_DENIED, result.getStatus());
		assertEquals("locked", result.getMessage());
	}

	@DisplayName("Stores account and connection identifiers in separate pending-migration fields")
	@Test
	void requestStoresAccountAndConnectionIdsInTheirOwnFields() {
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());
		when(providerLinkPersistenceService.findByUniqueIdAndProviderId(any(UUID.class), any(String.class)))
				.thenReturn(Optional.empty());

		DefaultMigrationService service = new DefaultMigrationService(
				providerManager,
				providerLinkPersistenceService,
				accountPersistenceService,
				pipelineStateStore,
				sessionService,
				identityService,
				Settings::new,
				this::commands,
				Messages::new
		);

		UUID connectionUniqueId = UUID.randomUUID();
		UUID identicaUniqueId = UUID.randomUUID();
		MigrationResult result = service.request(MigrationRequest.builder()
				.connectionUniqueId(connectionUniqueId)
				.identicaUniqueId(identicaUniqueId)
				.targetProviderId("premium")
				.username("PlayerOne")
				.ip("127.0.0.1")
				.build());

		assertEquals(MigrationResultStatus.PENDING_CONFIRMATION, result.getStatus());

		PendingMigration pendingMigration = service.findPendingMigration(connectionUniqueId).orElse(null);

		assertNotNull(pendingMigration);
		assertEquals(identicaUniqueId, pendingMigration.getUniqueId(), "pending migration should store the account UUID separately");
		assertEquals(connectionUniqueId, pendingMigration.getConnectionUniqueId(), "pending migration should store the connection UUID separately");
		assertEquals("premium", pendingMigration.getTargetProviderId());
		assertEquals(PendingMigration.Phase.CONFIRMATION, pendingMigration.getPhase());
	}

	@DisplayName("Rebuilds pending migration details from an active migration pipeline state")
	@Test
	void findPendingMigrationReturnsStartedMigrationFromPipelineState() {
		UUID connectionUniqueId = UUID.randomUUID();
		UUID identicaUniqueId = UUID.randomUUID();
		UUID initiatorUniqueId = UUID.randomUUID();

		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.MIGRATION);
		pipelineState.setScenario(MigrationContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.identity(new ConnectionIdentity(identicaUniqueId, "PlayerOne", "127.0.0.1"))
				.targetProviderId("premium")
				.build());
		pipelineState.putItem(new MigrationPendingState(
				"premium",
				1234L,
				MigrationInitiator.ADMIN,
				initiatorUniqueId
		), Duration.ofMinutes(5).toMillis());

		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.of(pipelineState));

		DefaultMigrationService service = new DefaultMigrationService(
				providerManager,
				providerLinkPersistenceService,
				accountPersistenceService,
				pipelineStateStore,
				sessionService,
				identityService,
				Settings::new,
				this::commands,
				Messages::new
		);

		PendingMigration pendingMigration = service.findPendingMigration(connectionUniqueId).orElse(null);

		assertNotNull(pendingMigration);
		assertEquals(identicaUniqueId, pendingMigration.getUniqueId());
		assertEquals(connectionUniqueId, pendingMigration.getConnectionUniqueId());
		assertEquals("premium", pendingMigration.getTargetProviderId());
		assertEquals(1234L, pendingMigration.getRequestedAt());
		assertEquals(MigrationInitiator.ADMIN, pendingMigration.getInitiator());
		assertEquals(initiatorUniqueId, pendingMigration.getInitiatorUniqueId());
		assertEquals(PendingMigration.Phase.STARTED, pendingMigration.getPhase());
	}

	@DisplayName("Starts migration even when the target provider is already linked")
	@Test
	void confirmStoresPendingMigrationEvenWhenTargetProviderAlreadyLinked() {
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());
		when(accountPersistenceService.findByUsername("PlayerOne")).thenReturn(List.of());
		when(providerLinkPersistenceService.findByUniqueIdAndProviderId(any(UUID.class), any(String.class)))
				.thenReturn(Optional.of(AccountProviderLink.builder()
						.uniqueId(UUID.randomUUID())
						.providerId("password")
						.providerSubject("existing-subject")
						.primaryLink(false)
						.build()));
		when(sessionService.close(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(null));
		when(identityService.find(any(UUID.class))).thenReturn(Optional.empty());
		when(identityService.find(any(String.class))).thenReturn(Optional.empty());

		Settings settings = new SettingsDefaults().supply(new Settings());
		DefaultMigrationService service = new DefaultMigrationService(
				providerManager,
				providerLinkPersistenceService,
				accountPersistenceService,
				pipelineStateStore,
				sessionService,
				identityService,
				() -> settings,
				this::commands,
				Messages::new
		);

		UUID connectionUniqueId = UUID.randomUUID();
		UUID identicaUniqueId = UUID.randomUUID();
		MigrationResult requested = service.request(MigrationRequest.builder()
				.connectionUniqueId(connectionUniqueId)
				.identicaUniqueId(identicaUniqueId)
				.targetProviderId("password")
				.username("PlayerOne")
				.ip("127.0.0.1")
				.build());
		assertEquals(MigrationResultStatus.PENDING_CONFIRMATION, requested.getStatus());

		MigrationResult confirmed = service.confirm(MigrationConfirm.builder()
				.connectionUniqueId(connectionUniqueId)
				.kickMessage("rejoin")
				.build());

		assertEquals(MigrationResultStatus.STARTED, confirmed.getStatus());
		verify(pipelineStateStore).save(any(PipelineStateReference.class), any(PipelineState.class), anyLong());
		verify(providerLinkPersistenceService, never()).setPrimaryExclusive(any(UUID.class), any(String.class));
	}

	private Commands commands() {
		Commands commands = new Commands();
		Commands.Behavior behavior = new Commands.Behavior();
		Commands.Behavior.Migration migration = new Commands.Behavior.Migration();
		migration.setConfirmTtl(Duration.ofSeconds(60));
		behavior.setMigration(migration);
		behavior.setSuggestions(new Commands.Behavior.Suggestions());
		behavior.setClear(new Commands.Behavior.Clear());
		behavior.setSessions(new Commands.Behavior.Sessions());
		commands.setBehavior(behavior);
		return commands;
	}
}

package me.whereareiam.identica.common.migration;

import com.google.inject.Provider;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.common.config.defaults.EngineDefaults;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.migration.MigrationJourney;
import me.whereareiam.identica.model.migration.PendingMigration;
import me.whereareiam.identica.model.migration.operation.MigrationCancel;
import me.whereareiam.identica.model.migration.operation.MigrationConfirm;
import me.whereareiam.identica.model.migration.operation.MigrationRequest;
import me.whereareiam.identica.model.migration.operation.MigrationResult;
import me.whereareiam.identica.model.migration.operation.MigrationStart;
import me.whereareiam.identica.model.pipeline.journey.JourneyPlan;
import me.whereareiam.identica.model.pipeline.journey.stage.JourneyStage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.migration.MigrationCancelScope;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import me.whereareiam.identica.type.pipeline.journey.step.StepContextRequirement;
import me.whereareiam.identica.type.provider.ProviderState;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Default Migration Service")
class DefaultMigrationServiceTest {
	@Mock
	private ProviderManager providerManager;
	@Mock
	private ProviderLinkPersistenceService providerLinkPersistenceService;
	@Mock
	private AccountPersistenceService accountPersistenceService;
	@Mock(strictness = Mock.Strictness.LENIENT)
	private ConnectionCoordinator connectionCoordinator;
	@Mock
	private SessionService sessionService;
	@Mock
	private IdentityService identityService;
	@Mock
	private DeliveryService deliveryService;
	@Mock
	private MigrationJourneyRegistry migrationJourneyRegistry;

	@BeforeEach
	void noJourneyStartedYet() {
		when(connectionCoordinator.findMigrationJourney(any(UUID.class)))
				.thenReturn(CompletableFuture.completedFuture(Optional.empty()));
		when(connectionCoordinator.cancelMigrationJourney(any(UUID.class)))
				.thenReturn(CompletableFuture.completedFuture(Optional.empty()));
		when(connectionCoordinator.startMigrationJourney(any(MigrationJourney.class)))
				.thenReturn(CompletableFuture.completedFuture(MigrationResultStatus.STARTED));
	}

	@DisplayName("Denies migration when the requested username is still occupied")
	@Test
	void deniesMigrationWhenUsernameNotFree() {
		mockAvailableProviders("provider");

		Account existing = Account.builder()
				.uniqueId(UUID.randomUUID())
				.username("Player")
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

		DefaultMigrationService service = service(() -> providers(), () -> commands, () -> messages);

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
		mockAvailableProviders("premium");
		when(providerLinkPersistenceService.findByUniqueIdAndProviderId(any(UUID.class), any(String.class)))
				.thenReturn(Optional.empty());

		DefaultMigrationService service = service();

		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		MigrationResult result = service.request(MigrationRequest.builder()
				.connectionUniqueId(connectionUniqueId)
				.accountUniqueId(accountUniqueId)
				.targetProviderId("premium")
				.username("PlayerOne")
				.ip("127.0.0.1")
				.build());

		assertEquals(MigrationResultStatus.PENDING_CONFIRMATION, result.getStatus());

		PendingMigration pendingMigration = service.findPendingMigration(connectionUniqueId).orElse(null);

		assertNotNull(pendingMigration);
		assertEquals(accountUniqueId, pendingMigration.getUniqueId(), "pending migration should store the account UUID separately");
		assertEquals(connectionUniqueId, pendingMigration.getConnectionUniqueId(), "pending migration should store the connection UUID separately");
		assertEquals("premium", pendingMigration.getTargetProviderId());
		assertEquals(PendingMigration.Phase.CONFIRMATION, pendingMigration.getPhase());
	}

	@DisplayName("Reports the started migration journey the connection's turn finds")
	@Test
	void findPendingMigrationReturnsTheStartedJourney() {
		UUID connectionUniqueId = UUID.randomUUID();
		PendingMigration started = PendingMigration.builder()
				.uniqueId(UUID.randomUUID())
				.connectionUniqueId(connectionUniqueId)
				.targetProviderId("premium")
				.phase(PendingMigration.Phase.STARTED)
				.build();
		when(connectionCoordinator.findMigrationJourney(connectionUniqueId))
				.thenReturn(CompletableFuture.completedFuture(Optional.of(started)));

		assertSame(started, service().findPendingMigration(connectionUniqueId).orElseThrow());
	}

	@DisplayName("Starts migration even when the target provider is already linked")
	@Test
	void confirmStoresPendingMigrationEvenWhenTargetProviderAlreadyLinked() {
		mockAvailableProviders("credential");
		when(accountPersistenceService.findByUsername("PlayerOne")).thenReturn(List.of());
		when(providerLinkPersistenceService.findByUniqueIdAndProviderId(any(UUID.class), any(String.class)))
				.thenReturn(Optional.of(AccountProviderLink.builder()
						.uniqueId(UUID.randomUUID())
						.providerId("credential")
						.providerSubject("existing-subject")
						.primaryLink(false)
						.build()));
		when(sessionService.close(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(null));
		when(identityService.findByConnectionUniqueId(any(UUID.class))).thenReturn(Optional.empty());

		DefaultMigrationService service = service(() -> providers(), this::commands, Messages::new);

		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		MigrationResult requested = service.request(MigrationRequest.builder()
				.connectionUniqueId(connectionUniqueId)
				.accountUniqueId(accountUniqueId)
				.targetProviderId("credential")
				.username("PlayerOne")
				.ip("127.0.0.1")
				.build());
		assertEquals(MigrationResultStatus.PENDING_CONFIRMATION, requested.getStatus());

		MigrationResult confirmed = service.confirm(MigrationConfirm.builder()
				.connectionUniqueId(connectionUniqueId)
				.kickMessage("rejoin")
				.build());

		assertEquals(MigrationResultStatus.STARTED, confirmed.getStatus());
		verify(providerLinkPersistenceService, never()).setPrimaryExclusive(any(UUID.class), any(String.class));
		verify(connectionCoordinator).startMigrationJourney(argThat(journey ->
				connectionUniqueId.equals(journey.getConnectionUniqueId())
						&& accountUniqueId.equals(journey.getAccountUniqueId())
		));
	}

	@DisplayName("Starts the confirmed journey with what the player asked for")
	@Test
	void confirmStartsTheRequestedJourney() {
		mockAvailableProviders("credential");
		when(accountPersistenceService.findByUsername("PlayerOne")).thenReturn(List.of());
		when(providerLinkPersistenceService.findByUniqueIdAndProviderId(any(UUID.class), any(String.class)))
				.thenReturn(Optional.empty());
		when(sessionService.close(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(null));
		when(identityService.findByConnectionUniqueId(any(UUID.class))).thenReturn(Optional.empty());

		DefaultMigrationService service = service(() -> providers(), this::commands, Messages::new);

		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		service.request(MigrationRequest.builder()
				.connectionUniqueId(connectionUniqueId)
				.accountUniqueId(accountUniqueId)
				.targetProviderId("credential")
				.username("PlayerOne")
				.ip("127.0.0.1")
				.build());

		MigrationResult confirmed = service.confirm(MigrationConfirm.builder()
				.connectionUniqueId(connectionUniqueId)
				.kickMessage("rejoin")
				.build());

		assertEquals(MigrationResultStatus.STARTED, confirmed.getStatus());

		verify(connectionCoordinator).startMigrationJourney(argThat(journey ->
				"credential".equals(journey.getTargetProviderId())
						&& "PlayerOne".equals(journey.getUsername())
						&& "127.0.0.1".equals(journey.getIp())
		));
	}

	@DisplayName("Queues a next-join notice when a started migration is cancelled")
	@Test
	void cancelPendingMigrationQueuesNextJoinNotice() {
		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		when(connectionCoordinator.cancelMigrationJourney(connectionUniqueId))
				.thenReturn(CompletableFuture.completedFuture(Optional.of(startedJourney(connectionUniqueId, accountUniqueId))));

		MigrationResult result = service().cancel(MigrationCancel.builder()
				.connectionUniqueId(connectionUniqueId)
				.scope(MigrationCancelScope.PENDING)
				.build());

		assertEquals(MigrationResultStatus.CANCELLED, result.getStatus());
		verify(deliveryService).queue(argThat(request ->
				accountUniqueId.equals(request.getTarget().getAccountUniqueId()) && request.getPayload().getChatMessage() != null
		));
	}

	@DisplayName("Does not start a journey over one the connection's turn finds, though the store looked empty before")
	@Test
	void startDoesNotReplaceAJourneyFoundInTheConnectionsTurn() {
		mockAvailableProviders("premium");
		when(accountPersistenceService.findByUsername("PlayerOne")).thenReturn(List.of());
		when(providerLinkPersistenceService.findByUniqueIdAndProviderId(any(UUID.class), any(String.class)))
				.thenReturn(Optional.empty());
		when(connectionCoordinator.startMigrationJourney(any(MigrationJourney.class)))
				.thenReturn(CompletableFuture.completedFuture(MigrationResultStatus.PENDING_EXISTS));

		UUID uniqueId = UUID.randomUUID();
		MigrationResult result = service().start(MigrationStart.builder()
				.uniqueId(uniqueId)
				.connectionUniqueId(uniqueId)
				.username("PlayerOne")
				.targetProviderId("premium")
				.build());

		assertEquals(MigrationResultStatus.PENDING_EXISTS, result.getStatus());
		verify(sessionService, never()).close(any(UUID.class));
		verify(identityService, never()).findByConnectionUniqueId(any(UUID.class));
	}

	@DisplayName("Rejects request when the target provider is unknown")
	@Test
	void requestRejectsUnknownTargetProvider() {
		when(providerManager.getProviders()).thenReturn(List.of());

		DefaultMigrationService service = service();

		MigrationResult result = service.request(MigrationRequest.builder()
				.connectionUniqueId(UUID.randomUUID())
				.accountUniqueId(UUID.randomUUID())
				.targetProviderId("ghost")
				.username("PlayerOne")
				.ip("127.0.0.1")
				.build());

		assertEquals(MigrationResultStatus.TARGET_UNSUPPORTED, result.getStatus());
	}

	@DisplayName("Rejects request when the target provider is configured but unavailable")
	@Test
	void requestRejectsUnavailableTargetProvider() {
		when(providerManager.getProviders()).thenReturn(List.of());

		DefaultMigrationService service = service(() -> providers(providerEntry("premium", false)), this::commands, this::messages);

		MigrationResult result = service.request(MigrationRequest.builder()
				.connectionUniqueId(UUID.randomUUID())
				.accountUniqueId(UUID.randomUUID())
				.targetProviderId("premium")
				.username("PlayerOne")
				.ip("127.0.0.1")
				.build());

		assertEquals(MigrationResultStatus.PROVIDER_UNAVAILABLE, result.getStatus());
	}

	@DisplayName("Rejects start when the target provider has no executable migration journey")
	@Test
	void startRejectsProviderWithoutMigrationJourney() {
		when(providerManager.getProviders()).thenReturn(List.of(provider("premium")));
		when(migrationJourneyRegistry.resolvePlan(any(MigrationContext.class), eq(PipelineType.MIGRATION), any(JourneyMode.class), eq("premium")))
				.thenReturn(emptyJourneyPlan());

		DefaultMigrationService service = service();

		MigrationResult result = service.start(MigrationStart.builder()
				.uniqueId(UUID.randomUUID())
				.connectionUniqueId(UUID.randomUUID())
				.targetProviderId("premium")
				.username("PlayerOne")
				.ip("127.0.0.1")
				.build());

		assertEquals(MigrationResultStatus.TARGET_UNSUPPORTED, result.getStatus());
	}

	@DisplayName("Confirm rejects and clears pending migration when target provider becomes unavailable")
	@Test
	void confirmRejectsUnavailableTargetProviderBeforeStoringPendingMigration() {
		when(providerManager.getProviders()).thenReturn(List.of(provider("premium")), List.of());
		when(migrationJourneyRegistry.resolvePlan(any(MigrationContext.class), eq(PipelineType.MIGRATION), any(JourneyMode.class), eq("premium")))
				.thenReturn(journeyPlanWithProviderSteps());
		when(providerLinkPersistenceService.findByUniqueIdAndProviderId(any(UUID.class), any(String.class)))
				.thenReturn(Optional.empty());

		DefaultMigrationService service = service(() -> providers(providerEntry("premium", true)), this::commands, this::messages);

		UUID connectionUniqueId = UUID.randomUUID();
		MigrationResult requested = service.request(MigrationRequest.builder()
				.connectionUniqueId(connectionUniqueId)
				.accountUniqueId(UUID.randomUUID())
				.targetProviderId("premium")
				.username("PlayerOne")
				.ip("127.0.0.1")
				.build());
		assertEquals(MigrationResultStatus.PENDING_CONFIRMATION, requested.getStatus());

		MigrationResult confirmed = service.confirm(MigrationConfirm.builder()
				.connectionUniqueId(connectionUniqueId)
				.kickMessage("rejoin")
				.build());

		assertEquals(MigrationResultStatus.PROVIDER_UNAVAILABLE, confirmed.getStatus());
		assertTrue(service.findPendingMigration(connectionUniqueId).isEmpty());
		verify(connectionCoordinator, never()).startMigrationJourney(any(MigrationJourney.class));
	}

	private DefaultMigrationService service() {
		return service(this::providers, this::commands, this::messages);
	}

	private DefaultMigrationService service(
			Provider<Providers> providersProvider,
			Provider<Commands> commandsProvider,
			Provider<Messages> messagesProvider
		) {
			return new DefaultMigrationService(
					providerManager,
					migrationJourneyRegistry,
					providerLinkPersistenceService,
					accountPersistenceService,
					connectionCoordinator,
					sessionService,
					identityService,
					deliveryService,
					this::engine,
					providersProvider,
					commandsProvider,
				messagesProvider
		);
	}

	private static PendingMigration startedJourney(UUID connectionUniqueId, UUID accountUniqueId) {
		return PendingMigration.builder()
				.uniqueId(accountUniqueId)
				.connectionUniqueId(connectionUniqueId)
				.targetProviderId("premium")
				.phase(PendingMigration.Phase.STARTED)
				.build();
	}

	private void mockAvailableProviders(String... providerIds) {
		when(providerManager.getProviders()).thenReturn(Arrays.stream(providerIds).map(this::provider).toList());
		when(migrationJourneyRegistry.resolvePlan(any(MigrationContext.class), eq(PipelineType.MIGRATION), any(JourneyMode.class), anyString()))
				.thenReturn(journeyPlanWithProviderSteps());
	}

	private InternalProvider provider(String id) {
		return InternalProvider.builder()
				.descriptor(descriptor(id))
				.state(ProviderState.ENABLED)
				.build();
	}

	private ProviderDescriptor descriptor(String id) {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId(id);
		descriptor.setName(id);
		descriptor.setVersion("1.0.0");
		descriptor.setMain("ignored.Main");
		descriptor.setSupportedPlatforms(List.of("ANY"));
		return descriptor;
	}

	private Providers providers(Providers.ProviderEntry... entries) {
		Providers providers = new Providers();
		providers.setProviders(List.of(entries));
		return providers;
	}

	private Providers providers() {
		return providers(new Providers.ProviderEntry[0]);
	}

	private Providers.ProviderEntry providerEntry(String id, boolean enabled) {
		Providers.ProviderEntry entry = new Providers.ProviderEntry();
		entry.setId(id);
		entry.setEnabled(enabled);
		return entry;
	}

	private JourneyPlan journeyPlanWithProviderSteps() {
		JourneyStage stage = JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(100)
				.pipelineTypes(EnumSet.of(PipelineType.MIGRATION))
				.journeyModes(EnumSet.allOf(JourneyMode.class))
				.build();

		return new JourneyPlan(List.of(new JourneyPlan.StageEntry(
				stage,
				List.of(JourneyStep.builder()
						.stageId(StageType.PROVIDER.id())
						.step(new TestStep("migration-step"))
						.providerId("premium")
						.scenarios(EnumSet.of(PipelineType.MIGRATION))
						.journeyModes(EnumSet.of(JourneyMode.SEAMLESS))
						.build())
		)));
	}

	private JourneyPlan emptyJourneyPlan() {
		JourneyStage stage = JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(100)
				.pipelineTypes(EnumSet.of(PipelineType.MIGRATION))
				.journeyModes(EnumSet.allOf(JourneyMode.class))
				.build();

		return new JourneyPlan(List.of(new JourneyPlan.StageEntry(stage, List.of())));
	}

	private static final class TestStep implements Step {
		private final String name;

		private TestStep(String name) {
			this.name = name;
		}

		@Override
		public @NotNull String getName() {
			return name;
		}

		@Override
		public @NotNull Set<JourneyMode> journeyModes() {
			return Set.of(JourneyMode.SEAMLESS);
		}

		@Override
		public @NotNull StepContextRequirement contextRequirement() {
			return StepContextRequirement.LOGIN;
		}

		@Override
		public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
			return CompletableFuture.completedFuture(null);
		}
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

	private Engine engine() {
		return new EngineDefaults().supply(new Engine());
	}

	private Messages messages() {
		Messages messages = new Messages();
		Messages.Scenarios scenarios = new Messages.Scenarios();
		Messages.Scenarios.Migration migration = new Messages.Scenarios.Migration();
		migration.setCancelled(List.of("cancelled"));
		scenarios.setMigration(migration);
		messages.setScenarios(scenarios);
		return messages;
	}
}

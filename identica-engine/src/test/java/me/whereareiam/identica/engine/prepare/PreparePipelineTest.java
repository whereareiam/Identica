package me.whereareiam.identica.engine.prepare;

import me.whereareiam.identica.engine.pipeline.prepare.ConnectionProviderContextResolver;
import me.whereareiam.identica.engine.pipeline.prepare.PreparePipeline;
import me.whereareiam.identica.engine.pipeline.prepare.PreparePipelineRegistry;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.database.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountPrepareEvent;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.identity.account.RegistrationAccountService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.engine.pipeline.PipelineExecutor;
import me.whereareiam.identica.engine.pipeline.prepare.group.context.ContextGroup;
import me.whereareiam.identica.engine.pipeline.prepare.group.context.phase.ResolveEntrypointPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.context.phase.ResolvePendingMigrationContextPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.context.phase.RestorePrepareStatePhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.finalize.FinalizeGroup;
import me.whereareiam.identica.engine.pipeline.prepare.group.finalize.phase.StorePrepareDecisionPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.handshake.HandshakeGroup;
import me.whereareiam.identica.engine.pipeline.prepare.group.handshake.phase.EvaluateHandshakePhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.handshake.phase.FinalizeHandshakePhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.policy.PolicyGroup;
import me.whereareiam.identica.engine.pipeline.prepare.group.policy.phase.ApplyPreparePolicyPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.profile.ProfileGroup;
import me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase.LoadPrepareAccountPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase.ResolvePendingMigrationAccountPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase.ResolvePreparedAccountPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase.ResolveProfilePhase;
import me.whereareiam.identica.model.auth.handshake.HandshakeRequest;
import me.whereareiam.identica.handshake.policy.ProviderScopedHandshakePolicy;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.migration.MigrationPendingState;
import me.whereareiam.identica.model.pipeline.state.PipelineState;
import me.whereareiam.identica.model.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.AccountDecision;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.ResolvedEntrypoint;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.profile.ProfileResolution;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.UsernameSource;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Prepare Pipeline")
class PreparePipelineTest {
	@Mock
	private HandshakeStore handshakeStore;
	@Mock
	private RegistrationAccountService registrationAccountService;
	@Mock
	private ProviderOperations providerOperations;
	@Mock
	private AccountPersistenceService accountPersistenceService;
	@Mock
	private ProviderLinkPersistenceService providerLinkPersistenceService;
	@Mock
	private ProviderProfilePersistenceService providerProfilePersistenceService;
	@Mock
	private EventManager eventManager;
	@Mock
	private PipelineStateStore pipelineStateStore;

	@DisplayName("Profile preparation builds a transient account and applies the event decision")
	@Test
	void profileStageBuildsTransientAccountAndHonorsPrepareDecision() {
		UUID identicaUniqueId = UUID.randomUUID();
		ConnectionIdentity identity = identity("PlayerOne");
		TestPrepareStateStore prepareStateStore = new TestPrepareStateStore();
		PreparePipeline pipeline = pipeline(prepareStateStore);
		String connectionKey = "PlayerOne|127.0.0.1|premium.example.com|25565";
		identity.setObservedUniqueId(UUID.randomUUID());

		when(handshakeStore.policies()).thenReturn(java.util.Set.of());
		when(providerOperations.resolveProfile(any()))
				.thenReturn(ProfileResolution.builder()
						.providerId("premium")
						.providerSubject("premium-subject")
						.build());
		when(providerOperations.resolveEntrypoint("premium.example.com", 25565))
				.thenReturn(ResolvedEntrypoint.builder()
						.providerId("premium")
						.host("premium.example.com")
						.port(25565)
						.build());
		when(registrationAccountService.reserve(any())).thenReturn(identicaUniqueId);
		when(providerLinkPersistenceService.findBySubject("premium", "premium-subject"))
				.thenReturn(Optional.empty());
		when(accountPersistenceService.findByUniqueId(identicaUniqueId))
				.thenReturn(Optional.empty());
		when(providerProfilePersistenceService.findBySubject("premium", "premium-subject"))
				.thenReturn(Optional.empty());
		doAnswer(invocation -> {
			Event event = invocation.getArgument(0);
			if (event instanceof AccountPrepareEvent prepareEvent) {
				prepareEvent.setEffectiveUsername("PlayerOne*");
				prepareEvent.setDecision(AccountDecision.deny("use premium entrypoint"));
			}
			return null;
		}).when(eventManager).call(any());

		PrepareDecision decision = pipeline.prepare(PrepareRequest.builder()
						.stage(PrepareStage.PROFILE)
						.connectionKey(connectionKey)
						.identity(identity)
						.build())
				.toCompletableFuture()
				.join();

		assertNotNull(decision);
		assertTrue(decision.isDenied());
		assertEquals("use premium entrypoint", decision.getDenialMessage());
		assertEquals("PlayerOne*", decision.getEffectiveUsername());
		assertEquals(identicaUniqueId, decision.getUniqueId());
		assertNotNull(decision.getProvider());
		assertEquals("premium", decision.getProvider().getProviderId());
		verify(eventManager).call(any(AccountPrepareEvent.class));
	}

	@DisplayName("Handshake preparation reuses the decision that was already stored for the connection")
	@Test
	void handshakeStageReusesPreviousHandshakeDecision() {
		TestPrepareStateStore prepareStateStore = new TestPrepareStateStore();
		PreparePipeline pipeline = pipeline(prepareStateStore);
		ConnectionIdentity identity = identity("PlayerTwo");
		String connectionKey = "PlayerTwo|127.0.0.1|premium.example.com|25565";
		PrepareDecision previous = PrepareDecision.builder()
				.status(PrepareDecision.Status.ALLOW)
				.handshake(HandshakeDecision.allow())
				.effectiveUsername("PlayerTwo")
				.build();
		prepareStateStore.put(connectionKey, previous);

		PrepareDecision decision = pipeline.prepare(PrepareRequest.builder()
						.stage(PrepareStage.HANDSHAKE)
						.connectionKey(connectionKey)
						.identity(identity)
						.build())
				.toCompletableFuture()
				.join();

		assertNotNull(decision);
		assertEquals(PrepareDecision.Status.ALLOW, decision.getStatus());
		assertNotNull(decision.getHandshake());
	}

	@DisplayName("Handshake preparation exposes pending migration provider context before policy evaluation")
	@Test
	void handshakeStageUsesPendingMigrationProviderContext() {
		ConnectionIdentity identity = identity("MigratingPlayer");
		TestPrepareStateStore prepareStateStore = new TestPrepareStateStore();
		PreparePipeline pipeline = pipeline(prepareStateStore);
		String connectionKey = "MigratingPlayer|127.0.0.1|premium.example.com|25565";

		PipelineState pendingMigrationState = PipelineState.initial();
		pendingMigrationState.setPipelineType(PipelineType.MIGRATION);
		MigrationContext migrationContext = MigrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), "MigratingPlayer", "127.0.0.1"))
				.targetProviderId("premium")
				.build();
		migrationContext.setProvider(ProviderContext.of("premium", null, "MigratingPlayer", ProviderOrigin.MANUAL));
		pendingMigrationState.setScenario(migrationContext);
		pendingMigrationState.putItem(new MigrationPendingState(
				"premium",
				1234L,
				MigrationInitiator.USER,
				UUID.randomUUID()
		), 1_000L);

		when(handshakeStore.policies()).thenReturn(java.util.Set.of());
		when(pipelineStateStore.find(argThat((PipelineStateReference reference) -> connectionKey.equals(reference.getConnectionKey()))))
				.thenReturn(Optional.of(pendingMigrationState));

		PrepareDecision decision = pipeline.prepare(PrepareRequest.builder()
						.stage(PrepareStage.HANDSHAKE)
						.connectionKey(connectionKey)
						.identity(identity)
						.build())
				.toCompletableFuture()
				.join();

		assertNotNull(decision);
		assertEquals(PrepareDecision.Status.ALLOW, decision.getStatus());
		assertNotNull(decision.getProvider());
		assertEquals("premium", decision.getProvider().getProviderId());
	}

	@DisplayName("Handshake preparation evaluates only the policy for the selected provider")
	@Test
	void handshakeStageSkipsScopedPoliciesForOtherProviders() {
		ConnectionIdentity identity = identity("MigratingPlayer");
		TestPrepareStateStore prepareStateStore = new TestPrepareStateStore();
		PreparePipeline pipeline = pipeline(prepareStateStore);
		String connectionKey = "MigratingPlayer|127.0.0.1|premium.example.com|25565";

		PipelineState pendingMigrationState = PipelineState.initial();
		pendingMigrationState.setPipelineType(PipelineType.MIGRATION);
		MigrationContext migrationContext = MigrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), "MigratingPlayer", "127.0.0.1"))
				.targetProviderId("password")
				.build();
		migrationContext.setProvider(ProviderContext.of("password", "offline-subject", "MigratingPlayer", ProviderOrigin.MANUAL));
		pendingMigrationState.setScenario(migrationContext);
		pendingMigrationState.putItem(new MigrationPendingState(
				"password",
				1234L,
				MigrationInitiator.USER,
				UUID.randomUUID()
		), 1_000L);

		CountingScopedHandshakePolicy premiumPolicy = new CountingScopedHandshakePolicy("premium");
		when(handshakeStore.policies()).thenReturn(java.util.Set.of(premiumPolicy));
		when(pipelineStateStore.find(argThat((PipelineStateReference reference) -> connectionKey.equals(reference.getConnectionKey()))))
				.thenReturn(Optional.of(pendingMigrationState));

		PrepareDecision decision = pipeline.prepare(PrepareRequest.builder()
						.stage(PrepareStage.HANDSHAKE)
						.connectionKey(connectionKey)
						.identity(identity)
						.build())
				.toCompletableFuture()
				.join();

		assertNotNull(decision);
		assertEquals(PrepareDecision.Status.ALLOW, decision.getStatus());
		assertEquals(0, premiumPolicy.invocations);
	}

	@DisplayName("Premium profile preparation reuses the UUID from an existing linked account")
	@Test
	void profileStageReusesExistingLinkedUuidForPremiumJoin() {
		UUID identicaUniqueId = UUID.randomUUID();
		ConnectionIdentity identity = identity("MigratedPlayer");
		TestPrepareStateStore prepareStateStore = new TestPrepareStateStore();
		PreparePipeline pipeline = pipeline(prepareStateStore);
		String connectionKey = "MigratedPlayer|127.0.0.1|premium.example.com|25565";

		when(handshakeStore.policies()).thenReturn(java.util.Set.of());
		when(providerOperations.resolveProfile(any()))
				.thenReturn(ProfileResolution.builder()
						.providerId("premium")
						.providerSubject("premium-subject")
						.build());
		when(providerOperations.resolveEntrypoint("premium.example.com", 25565))
				.thenReturn(ResolvedEntrypoint.builder()
						.providerId("premium")
						.host("premium.example.com")
						.port(25565)
						.build());
		when(registrationAccountService.reserve(any())).thenReturn(identicaUniqueId);
		when(providerLinkPersistenceService.findBySubject("premium", "premium-subject"))
				.thenReturn(Optional.of(AccountProviderLink.builder()
						.uniqueId(identicaUniqueId)
						.providerId("premium")
						.providerSubject("premium-subject")
						.primaryLink(true)
						.build()));
		when(accountPersistenceService.findByUniqueId(identicaUniqueId))
				.thenReturn(Optional.of(Account.builder()
						.uniqueId(identicaUniqueId)
						.username("MigratedPlayer")
						.source(UsernameSource.PROVIDER)
						.build()));
		when(providerProfilePersistenceService.findBySubject("premium", "premium-subject"))
				.thenReturn(Optional.of(AccountProviderProfile.builder()
						.providerId("premium")
						.providerSubject("premium-subject")
						.providerUsername("MigratedPlayer")
						.build()));

		PrepareDecision decision = pipeline.prepare(PrepareRequest.builder()
						.stage(PrepareStage.PROFILE)
						.connectionKey(connectionKey)
						.identity(identity)
						.build())
				.toCompletableFuture()
				.join();

		assertNotNull(decision);
		assertEquals(PrepareDecision.Status.ALLOW, decision.getStatus());
		assertEquals(identicaUniqueId, decision.getUniqueId());
		assertEquals("MigratedPlayer", decision.getEffectiveUsername());
		assertNotNull(prepareStateStore.peek(identicaUniqueId).orElse(null));
	}

	@DisplayName("Profile preparation reuses the UUID already prepared for the same connection")
	@Test
	void profileStageReusesPreparedUuidForSameConnectionKey() {
		UUID preparedUniqueId = UUID.randomUUID();
		ConnectionIdentity firstIdentity = identity("whereareiam");
		ConnectionIdentity secondIdentity = identity("whereareiam");
		TestPrepareStateStore prepareStateStore = new TestPrepareStateStore();
		PreparePipeline pipeline = pipeline(prepareStateStore);
		String connectionKey = "whereareiam|127.0.0.1|premium.example.com|25565";
		firstIdentity.setObservedUniqueId(UUID.randomUUID());
		secondIdentity.setObservedUniqueId(UUID.randomUUID());

		when(handshakeStore.policies()).thenReturn(java.util.Set.of());
		when(providerOperations.resolveEntrypoint("premium.example.com", 25565))
				.thenReturn(ResolvedEntrypoint.builder()
						.providerId("premium")
						.host("premium.example.com")
						.port(25565)
						.build());
		when(providerOperations.resolveProfile(any()))
				.thenReturn(ProfileResolution.builder()
						.providerId("password")
						.providerSubject("offline-subject")
						.build())
				.thenReturn(ProfileResolution.builder()
						.providerId("premium")
						.providerSubject("premium-subject")
						.build());
		when(registrationAccountService.reserve(any())).thenReturn(preparedUniqueId);
		when(providerLinkPersistenceService.findBySubject("password", "offline-subject"))
				.thenReturn(Optional.empty());
		when(providerLinkPersistenceService.findBySubject("premium", "premium-subject"))
				.thenReturn(Optional.empty());
		when(accountPersistenceService.findByUniqueId(preparedUniqueId))
				.thenReturn(Optional.empty());
		when(providerProfilePersistenceService.findBySubject("password", "offline-subject"))
				.thenReturn(Optional.empty());
		when(providerProfilePersistenceService.findBySubject("premium", "premium-subject"))
				.thenReturn(Optional.empty());

		PrepareDecision first = pipeline.prepare(PrepareRequest.builder()
						.stage(PrepareStage.PROFILE)
						.connectionKey(connectionKey)
						.identity(firstIdentity)
						.build())
				.toCompletableFuture()
				.join();
		PrepareDecision second = pipeline.prepare(PrepareRequest.builder()
						.stage(PrepareStage.PROFILE)
						.connectionKey(connectionKey)
						.identity(secondIdentity)
						.build())
				.toCompletableFuture()
				.join();

		assertNotNull(first);
		assertNotNull(second);
		assertEquals(preparedUniqueId, first.getUniqueId());
		assertEquals(preparedUniqueId, second.getUniqueId());
		verify(registrationAccountService).reserve(any());
	}

	@DisplayName("Pending migration state can supply the target account UUID during profile preparation")
	@Test
	void profileStageReusesPendingMigrationAccountForTargetProvider() {
		UUID identicaUniqueId = UUID.randomUUID();
		ConnectionIdentity identity = identity("MigratingPlayer");
		TestPrepareStateStore prepareStateStore = new TestPrepareStateStore();
		PreparePipeline pipeline = pipeline(prepareStateStore);
		String connectionKey = "MigratingPlayer|127.0.0.1|premium.example.com|25565";
		identity.setObservedUniqueId(UUID.randomUUID());

		PipelineState pendingMigrationState = PipelineState.initial();
		pendingMigrationState.setPipelineType(PipelineType.MIGRATION);
		pendingMigrationState.setScenario(MigrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(identicaUniqueId, "MigratingPlayer", "127.0.0.1"))
				.targetProviderId("premium")
				.build());
		pendingMigrationState.putItem(new MigrationPendingState(
				"premium",
				1234L,
				MigrationInitiator.USER,
				identicaUniqueId
		), 1_000L);

		when(handshakeStore.policies()).thenReturn(java.util.Set.of());
		when(providerOperations.resolveProfile(any()))
				.thenReturn(ProfileResolution.builder()
						.providerId("premium")
						.providerSubject("premium-subject")
						.build());
		when(providerOperations.resolveEntrypoint("premium.example.com", 25565))
				.thenReturn(ResolvedEntrypoint.builder()
						.providerId("premium")
						.host("premium.example.com")
						.port(25565)
						.build());
		when(pipelineStateStore.find(org.mockito.ArgumentMatchers.<PipelineStateReference>any())).thenReturn(Optional.empty());
		when(pipelineStateStore.find(argThat((PipelineStateReference reference) -> connectionKey.equals(reference.getConnectionKey()))))
				.thenReturn(Optional.of(pendingMigrationState));
		when(providerLinkPersistenceService.findBySubject("premium", "premium-subject"))
				.thenReturn(Optional.empty());
		when(accountPersistenceService.findByUniqueId(identicaUniqueId))
				.thenReturn(Optional.of(Account.builder()
						.uniqueId(identicaUniqueId)
						.username("MigratingPlayer")
						.source(UsernameSource.PROVIDER)
						.build()));
		when(providerProfilePersistenceService.findBySubject("premium", "premium-subject"))
				.thenReturn(Optional.empty());

		PrepareDecision decision = pipeline.prepare(PrepareRequest.builder()
						.stage(PrepareStage.PROFILE)
						.connectionKey(connectionKey)
						.identity(identity)
						.build())
				.toCompletableFuture()
				.join();

		assertNotNull(decision);
		assertEquals(PrepareDecision.Status.ALLOW, decision.getStatus());
		assertEquals(identicaUniqueId, decision.getUniqueId());
		verify(registrationAccountService, never()).reserve(any());
	}

	@DisplayName("Profile preparation clears stale migration state when the observed provider no longer matches")
	@Test
	void profileStageClearsPendingMigrationWhenObservedProviderDiffers() {
		UUID identicaUniqueId = UUID.randomUUID();
		ConnectionIdentity identity = identity("MigratingPlayer");
		TestPrepareStateStore prepareStateStore = new TestPrepareStateStore();
		PreparePipeline pipeline = pipeline(prepareStateStore);
		String connectionKey = "MigratingPlayer|127.0.0.1|premium.example.com|25565";
		identity.setObservedUniqueId(UUID.randomUUID());

		PipelineState pendingMigrationState = PipelineState.initial();
		pendingMigrationState.setPipelineType(PipelineType.MIGRATION);
		pendingMigrationState.setScenario(MigrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(identicaUniqueId, "MigratingPlayer", "127.0.0.1"))
				.targetProviderId("premium")
				.build());
		pendingMigrationState.putItem(new MigrationPendingState(
				"premium",
				1234L,
				MigrationInitiator.USER,
				identicaUniqueId
		), 1_000L);

		when(handshakeStore.policies()).thenReturn(java.util.Set.of());
		when(providerOperations.resolveProfile(any()))
				.thenReturn(ProfileResolution.builder()
						.providerId("password")
						.providerSubject("password-subject")
						.build());
		when(providerOperations.resolveEntrypoint("premium.example.com", 25565))
				.thenReturn(ResolvedEntrypoint.builder()
						.providerId("premium")
						.host("premium.example.com")
						.port(25565)
						.build());
		when(pipelineStateStore.find(org.mockito.ArgumentMatchers.<PipelineStateReference>any())).thenReturn(Optional.empty());
		when(pipelineStateStore.find(argThat((PipelineStateReference reference) -> connectionKey.equals(reference.getConnectionKey()))))
				.thenReturn(Optional.of(pendingMigrationState));
		when(registrationAccountService.reserve(any())).thenReturn(identicaUniqueId);
		when(providerLinkPersistenceService.findBySubject("password", "password-subject"))
				.thenReturn(Optional.empty());
		when(accountPersistenceService.findByUniqueId(identicaUniqueId))
				.thenReturn(Optional.empty());
		when(providerProfilePersistenceService.findBySubject("password", "password-subject"))
				.thenReturn(Optional.empty());

		PrepareDecision decision = pipeline.prepare(PrepareRequest.builder()
						.stage(PrepareStage.PROFILE)
						.connectionKey(connectionKey)
						.identity(identity)
						.build())
				.toCompletableFuture()
				.join();

		assertNotNull(decision);
		assertEquals(PrepareDecision.Status.ALLOW, decision.getStatus());
		assertEquals(identicaUniqueId, decision.getUniqueId());
		verify(pipelineStateStore).clear(argThat((PipelineStateReference reference) -> connectionKey.equals(reference.getConnectionKey())));
	}

	private @NotNull PreparePipeline pipeline(@NotNull PrepareStateStore prepareStateStore) {
		ConnectionProviderContextResolver contextResolver = new ConnectionProviderContextResolver(providerOperations);
		PreparePipelineRegistry registry = new PreparePipelineRegistry(
				new ContextGroup(),
				new HandshakeGroup(),
				new ProfileGroup(),
				new PolicyGroup(),
				new FinalizeGroup(),
				new RestorePrepareStatePhase(prepareStateStore),
				new ResolveEntrypointPhase(providerOperations, contextResolver),
				new ResolvePendingMigrationContextPhase(pipelineStateStore),
				new EvaluateHandshakePhase(handshakeStore),
				new FinalizeHandshakePhase(eventManager, Messages::new),
				new ResolveProfilePhase(providerOperations, contextResolver),
				new ResolvePendingMigrationAccountPhase(
						pipelineStateStore,
						accountPersistenceService,
						providerLinkPersistenceService,
						providerProfilePersistenceService
				),
				new ResolvePreparedAccountPhase(
						prepareStateStore,
						accountPersistenceService,
						providerLinkPersistenceService,
						providerProfilePersistenceService
				),
				new LoadPrepareAccountPhase(
						registrationAccountService,
						accountPersistenceService,
						providerLinkPersistenceService,
						providerProfilePersistenceService
				),
				new ApplyPreparePolicyPhase(eventManager, Messages::new),
				new StorePrepareDecisionPhase(prepareStateStore)
		);
		return new PreparePipeline(registry, new PipelineExecutor());
	}

	private @NotNull ConnectionIdentity identity(@NotNull String username) {
		ConnectionIdentity identity = new ConnectionIdentity(username, "127.0.0.1");
		identity.setOrigin(new ConnectionIdentity.Origin("premium.example.com", 25565));
		return identity;
	}

	private static final class TestPrepareStateStore implements PrepareStateStore {
		private final Map<String, PrepareDecision> byKey = new HashMap<>();
		private final Map<UUID, PrepareDecision> byUniqueId = new HashMap<>();

		@Override
		public void put(@NotNull String connectionKey, @NotNull PrepareDecision decision) {
			byKey.put(connectionKey, decision);
		}

		@Override
		public void put(@NotNull UUID uniqueId, String connectionKey, @NotNull PrepareDecision decision) {
			byUniqueId.put(uniqueId, decision);
			if (connectionKey != null && !connectionKey.isBlank())
				byKey.put(connectionKey, decision);
		}

		@Override
		public @NotNull Optional<PrepareDecision> peek(@NotNull String connectionKey) {
			return Optional.ofNullable(byKey.get(connectionKey));
		}

		@Override
		public @NotNull Optional<PrepareDecision> peek(@NotNull UUID uniqueId) {
			return Optional.ofNullable(byUniqueId.get(uniqueId));
		}

		@Override
		public boolean clear(@NotNull UUID uniqueId) {
			return byUniqueId.remove(uniqueId) != null;
		}
	}

	private static final class CountingScopedHandshakePolicy implements ProviderScopedHandshakePolicy {
		private final String providerId;
		private int invocations;

		private CountingScopedHandshakePolicy(String providerId) {
			this.providerId = providerId;
		}

		@Override
		public @NotNull String providerId() {
			return providerId;
		}

		@Override
		public java.util.concurrent.CompletionStage<HandshakeDecision> evaluate(
				HandshakeRequest request
		) {
			invocations++;
			return java.util.concurrent.CompletableFuture.completedFuture(HandshakeDecision.allow());
		}
	}
}

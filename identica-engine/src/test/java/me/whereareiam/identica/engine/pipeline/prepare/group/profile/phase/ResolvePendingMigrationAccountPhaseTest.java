package me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase;

import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.scenario.migration.MigrationResolvedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.pipeline.migration.MigrationPendingState;
import me.whereareiam.identica.model.pipeline.prepare.PrepareAccountCandidateItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Resolve Pending Migration Account Phase")
class ResolvePendingMigrationAccountPhaseTest {
	private static final String USERNAME = "PlayerOne";
	private static final String CONNECTION_KEY = "PlayerOne|127.0.0.1||";
	/** A custom provider whose login the platform verifies before the profile is resolved. */
	private static final String LOGIN_PROVIDER = "token";
	/** A custom provider that derives its subject from the username and proves it in a later step. */
	private static final String STEP_PROVIDER = "pin";
	private static final List<String> CANCELLED = List.of("Identica", "Your pending migration was cancelled.");

	@Mock
	private PipelineStateStore pipelineStateStore;
	@Mock
	private AccountPersistenceService accountPersistenceService;
	@Mock
	private ProviderLinkPersistenceService providerLinkPersistenceService;
	@Mock
	private DeliveryService deliveryService;
	@Mock
	private EventManager eventManager;
	@Mock
	private ProviderOperations providerOperations;

	private final UUID connectionUniqueId = UUID.randomUUID();
	private final UUID accountUniqueId = UUID.randomUUID();
	private List<String> cancelled = CANCELLED;
	private ResolvePendingMigrationAccountPhase phase;

	@BeforeEach
	void setUp() {
		phase = new ResolvePendingMigrationAccountPhase(
				pipelineStateStore,
				accountPersistenceService,
				providerLinkPersistenceService,
				deliveryService,
				eventManager,
				providerOperations,
				this::messages
		);
	}

	@DisplayName("Continues after a verified login with a custom provider the account is leaving and selects the target")
	@Test
	void continuesAfterProofWithTheCustomProviderTheAccountLeaves() {
		pending(pendingState("credential"));
		account();
		linked(LOGIN_PROVIDER, "token-subject");
		when(providerOperations.resolveSelectedSubject(eq("credential"), any())).thenReturn(SubjectResolution.builder()
				.providerId("credential")
				.providerSubject("credential-subject")
				.build());

		PipelineState state = observed(LOGIN_PROVIDER, "token-subject", true);
		execute(state);

		ProviderContext provider = state.item(PrepareContextItem.class).orElseThrow().getProvider();
		assertNotNull(provider);
		assertEquals("credential", provider.getProviderId());
		assertEquals("credential-subject", provider.getProviderSubject());
		assertFalse(provider.isSubjectVerified());
		assertContinued(state);
	}

	@DisplayName("Continues when a custom target provider verified the login itself")
	@Test
	void continuesWhenTheCustomTargetVerifiedTheLogin() {
		pending(pendingState(LOGIN_PROVIDER));
		account();

		PipelineState state = observed(LOGIN_PROVIDER, "token-subject", true);
		execute(state);

		ProviderContext provider = state.item(PrepareContextItem.class).orElseThrow().getProvider();
		assertEquals(LOGIN_PROVIDER, provider.getProviderId());
		assertEquals("token-subject", provider.getProviderSubject());
		assertContinued(state);
		verifyNoInteractions(providerOperations, providerLinkPersistenceService);
	}

	@DisplayName("Cancels when a step-based custom provider the account is leaving is only seen, not verified")
	@Test
	void cancelsWhenTheStepBasedProviderTheAccountLeavesIsOnlySeen() {
		pending(pendingState("credential"));

		PipelineState state = observed(STEP_PROVIDER, "pin-subject", false);
		execute(state);

		assertCancelled(state);
		assertEquals(STEP_PROVIDER, state.item(PrepareContextItem.class).orElseThrow().getProvider().getProviderId());
		verifyNoInteractions(providerOperations, providerLinkPersistenceService);
	}

	@DisplayName("Cancels when the target provider is only seen, not verified")
	@Test
	void cancelsWhenTheTargetIsOnlySeen() {
		pending(pendingState(STEP_PROVIDER));

		PipelineState state = observed(STEP_PROVIDER, "pin-subject", false);
		execute(state);

		assertCancelled(state);
	}

	@DisplayName("Cancels when the verified login belongs to another account of the provider being left")
	@Test
	void cancelsForAnotherSubjectOfTheProviderTheAccountLeaves() {
		pending(pendingState("credential"));
		linked(LOGIN_PROVIDER, "token-subject");

		PipelineState state = observed(LOGIN_PROVIDER, "other-subject", true);
		execute(state);

		assertCancelled(state);
		assertEquals(String.join("\n", CANCELLED), queuedNotice());
	}

	@DisplayName("Cancels with the configured notice when the verified login is with a provider the account does not use")
	@Test
	void cancelsForAProviderTheAccountDoesNotUse() {
		pending(pendingState("credential"));
		when(providerLinkPersistenceService.findByUniqueIdAndProviderId(accountUniqueId, LOGIN_PROVIDER))
				.thenReturn(Optional.empty());

		PipelineState state = observed(LOGIN_PROVIDER, "token-subject", true);
		execute(state);

		assertCancelled(state);
		assertEquals(String.join("\n", CANCELLED), queuedNotice());
	}

	@DisplayName("Cancels without a notice when the configured notice is empty")
	@Test
	void cancelsSilentlyWhenTheNoticeIsEmpty() {
		cancelled = List.of();
		pending(pendingState("credential"));

		PipelineState state = observed(STEP_PROVIDER, "pin-subject", false);
		execute(state);

		assertCancelled(state);
		verify(deliveryService, never()).queue(any());
	}

	@DisplayName("Leaves the connection alone without a pending migration")
	@Test
	void ignoresAConnectionWithoutPendingMigration() {
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());

		PipelineState state = observed(LOGIN_PROVIDER, "token-subject", true);
		execute(state);

		assertUntouched(state);
	}

	@DisplayName("Leaves the connection alone once the pending migration expired")
	@Test
	void ignoresAnExpiredPendingMigration() {
		pending(pendingState("credential", 1L).pruneExpired(System.currentTimeMillis() + 1000L));

		PipelineState state = observed(LOGIN_PROVIDER, "token-subject", true);
		execute(state);

		assertUntouched(state);
	}

	private void assertContinued(PipelineState state) {
		PrepareAccountCandidateItem candidate = state.item(PrepareAccountCandidateItem.class).orElseThrow();
		assertEquals(accountUniqueId, candidate.getUniqueId());
		assertNotNull(candidate.getAccount());
		assertEquals(USERNAME, candidate.getAccount().getUsername());
		verify(pipelineStateStore, never()).clear(any());
		verify(deliveryService, never()).queue(any());
		verifyNoInteractions(eventManager);
	}

	private void assertUntouched(PipelineState state) {
		assertTrue(state.item(PrepareAccountCandidateItem.class).isEmpty());
		assertEquals(LOGIN_PROVIDER, state.item(PrepareContextItem.class).orElseThrow().getProvider().getProviderId());
		verify(pipelineStateStore, never()).clear(any());
		verify(deliveryService, never()).queue(any());
		verifyNoInteractions(eventManager);
	}

	private void assertCancelled(PipelineState state) {
		assertTrue(state.item(PrepareAccountCandidateItem.class).isEmpty());
		ArgumentCaptor<PipelineStateReference> cleared = ArgumentCaptor.forClass(PipelineStateReference.class);
		verify(pipelineStateStore).clear(cleared.capture());
		assertEquals(CONNECTION_KEY, cleared.getValue().getConnectionKey());

		ArgumentCaptor<MigrationResolvedEvent> resolved = ArgumentCaptor.forClass(MigrationResolvedEvent.class);
		verify(eventManager).call(resolved.capture());
		assertEquals(ScenarioResolution.CANCELLED, resolved.getValue().getReason());
	}

	private String queuedNotice() {
		ArgumentCaptor<DeliveryRequest> queued = ArgumentCaptor.forClass(DeliveryRequest.class);
		verify(deliveryService).queue(queued.capture());
		assertEquals(accountUniqueId, queued.getValue().getTarget().getAccountUniqueId());
		return queued.getValue().getPayload().getChatMessage();
	}

	private void execute(PipelineState state) {
		PrepareGroupState groupState = new PrepareGroupState();
		groupState.setRequest(PrepareRequest.builder()
				.connectionKey(CONNECTION_KEY)
				.stage(PrepareStage.PROFILE)
				.identity(new ConnectionIdentity(USERNAME, "127.0.0.1"))
				.build());

		phase.execute(state, groupState).toCompletableFuture().join();
	}

	private PipelineState observed(String providerId, String subject, boolean verified) {
		ProviderContext provider = ProviderContext.of(providerId, subject, USERNAME, ProviderOrigin.AUTO);
		provider.setSubjectVerified(verified);

		PipelineState state = PipelineState.initial();
		state.putItem(new PrepareContextItem(provider, null, null), 0L);
		return state;
	}

	private void linked(String providerId, String subject) {
		when(providerLinkPersistenceService.findByUniqueIdAndProviderId(accountUniqueId, providerId))
				.thenReturn(Optional.of(AccountProviderLink.builder()
						.uniqueId(accountUniqueId)
						.providerId(providerId)
						.providerSubject(subject)
						.primaryLink(true)
						.build()));
	}

	private void pending(PipelineState pendingState) {
		doReturn(Optional.of(pendingState)).when(pipelineStateStore).find(any(PipelineStateReference.class));
	}

	private void account() {
		when(accountPersistenceService.findByUniqueId(accountUniqueId)).thenReturn(Optional.of(Account.builder()
				.uniqueId(accountUniqueId)
				.username(USERNAME)
				.build()));
	}

	private PipelineState pendingState(String targetProviderId) {
		return pendingState(targetProviderId, Duration.ofMinutes(5).toMillis());
	}

	private PipelineState pendingState(String targetProviderId, long ttlMillis) {
		PipelineState pendingState = PipelineState.initial();
		pendingState.setPipelineType(PipelineType.MIGRATION);
		pendingState.setScenario(MigrationContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.identity(new ConnectionIdentity(accountUniqueId, USERNAME, "127.0.0.1"))
				.targetProviderId(targetProviderId)
				.build());
		pendingState.putItem(new MigrationPendingState(
				targetProviderId,
				1234L,
				MigrationInitiator.USER,
				connectionUniqueId
		), ttlMillis);
		pendingState.putItem(new JourneyStateItem(null, null, 0), ttlMillis);
		return pendingState;
	}

	private Messages messages() {
		Messages.Scenarios.Migration migration = new Messages.Scenarios.Migration();
		migration.setCancelled(cancelled);
		Messages.Scenarios scenarios = new Messages.Scenarios();
		scenarios.setMigration(migration);
		Messages messages = new Messages();
		messages.setScenarios(scenarios);
		return messages;
	}
}

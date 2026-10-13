package me.whereareiam.identica.engine.pipeline.username;

import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareAccountCandidateItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.pipeline.state.scenario.type.registration.PolicyState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.identity.username.UsernameConflictHandler;
import me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService;
import me.whereareiam.identica.model.config.UsernameMessages;
import me.whereareiam.identica.model.identity.username.UsernameConflictResult;
import me.whereareiam.identica.engine.pipeline.prepare.group.policy.phase.ApplyAuthoritativeUsernamePhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.policy.phase.ReviewAuthenticationUsernamePhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.policy.phase.ReviewRegistrationUsernamePhase;
import me.whereareiam.identica.pipeline.state.scenario.shared.item.UsernameStateItem;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.type.provider.ProviderTrait;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@DisplayName("Username Conflict Phases")
class UsernameConflictPhasesTest {
	@DisplayName("Prepare phase applies the handler result and stores a denial decision")
	@Test
	void preparePhaseAppliesHandlerResult() {
		UsernameConflictHandler conflictHandler = mock(UsernameConflictHandler.class);
		when(conflictHandler.handle(any(), eq("Player"))).thenReturn(
				UsernameConflictResult.denied("denied", "Resolved")
		);

		ApplyAuthoritativeUsernamePhase phase = new ApplyAuthoritativeUsernamePhase(
				providerManager(),
				usernamePersistenceService(),
				conflictHandler,
				this::messages
		);

		PipelineState pipelineState = PipelineState.initial();
		pipelineState.put(PrepareContextItem.KEY, new PrepareContextItem(null, providerContext(), null, null, null));
		pipelineState.put(PrepareAccountCandidateItem.KEY, candidate());

		phase.execute(pipelineState, new PrepareGroupState()).toCompletableFuture().join();

		PrepareAccountCandidateItem candidate = pipelineState.get(PrepareAccountCandidateItem.KEY).orElseThrow();
		PrepareDecision decision = pipelineState.get(PrepareDecision.KEY).orElseThrow();
		assertEquals("Resolved", candidate.getEffectiveUsername());
		assertEquals("Resolved", candidate.getAccount().getUsername());
		assertEquals("denied", decision.getDenialMessage());
	}

	@DisplayName("Username authority without the trait does not invoke prepare conflicts")
	@Test
	void preparePhaseSkipsNonparticipatingAuthority() {
		UsernameConflictHandler conflictHandler = mock(UsernameConflictHandler.class);
		AccountUsernamePersistenceService persistence = usernamePersistenceService();
		ApplyAuthoritativeUsernamePhase phase = new ApplyAuthoritativeUsernamePhase(
				providerManager(false), persistence, conflictHandler, this::messages
		);
		PipelineState pipelineState = PipelineState.initial();
		pipelineState.put(PrepareContextItem.KEY, new PrepareContextItem(null, providerContext(), null, null, null));
		PrepareAccountCandidateItem candidate = candidate();
		candidate.getProfile().setProviderUsername("ProviderName");
		pipelineState.put(PrepareAccountCandidateItem.KEY, candidate);
		PrepareGroupState state = new PrepareGroupState();

		assertFalse(phase.supports(pipelineState, state));
		phase.execute(pipelineState, state).toCompletableFuture().join();

		assertEquals("Player", candidate.getAccount().getUsername());
		assertNull(candidate.getEffectiveUsername());
		assertNull(state.getResult());
		assertTrue(pipelineState.get(PrepareDecision.KEY).isEmpty());
		verifyNoInteractions(conflictHandler, persistence);
	}

	@DisplayName("Username authority without the trait does not create a scenario snapshot")
	@Test
	void authenticationSynchronizationSkipsNonparticipatingAuthority() {
		AccountUsernamePersistenceService persistence = usernamePersistenceService();
		var phase = new me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.identity.phase.SynchronizeAuthenticationUsernamePhase(
				providerManager(false), persistence, this::messages
		);
		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.AUTHENTICATION);
		AuthContext context = AuthContext.builder()
				.identity(new ConnectionIdentity("ClientName", "127.0.0.1"))
				.provider(providerContext())
				.build();
		pipelineState.setScenario(context);
		PrepareAccountCandidateItem candidate = candidate();
		candidate.getProfile().setProviderUsername("ProviderName");
		var state = new me.whereareiam.identica.pipeline.state.scenario.type.authentication.IdentityState();
		state.setAccount(candidate.getAccount());
		state.setProviderLink(candidate.getLink());
		state.setProfile(candidate.getProfile());
		state.setResult(PipelineResult.complete());

		phase.execute(pipelineState, state).toCompletableFuture().join();

		assertEquals("ClientName", context.getUsername());
		assertEquals("Player", candidate.getAccount().getUsername());
		assertTrue(pipelineState.get(UsernameStateItem.KEY).isEmpty());
		verifyNoInteractions(persistence);
	}

	@DisplayName("Username synchronization failures use the feature-owned identity message")
	@Test
	void synchronizationFailureUsesUsernameMessages() {
		var phase = new me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.identity.phase.SynchronizeAuthenticationUsernamePhase(
				providerManager(), usernamePersistenceService(), this::messages
		);
		var state = new me.whereareiam.identica.pipeline.state.scenario.type.authentication.IdentityState();
		state.setResult(PipelineResult.complete());

		phase.execute(PipelineState.initial(), state).toCompletableFuture().join();

		assertEquals("sync", state.getResult().getMessage());
	}

	@DisplayName("Registration policy phase updates the identity username and denial state")
	@Test
	void registrationPhaseAppliesHandlerResult() {
		UsernameConflictHandler conflictHandler = mock(UsernameConflictHandler.class);
		when(conflictHandler.handle(any(), eq("Player"))).thenReturn(
				UsernameConflictResult.denied("denied", "Resolved")
		);

		ReviewRegistrationUsernamePhase phase = new ReviewRegistrationUsernamePhase(
				conflictHandler,
				accountServiceEmpty(),
				linkServiceEmpty(),
				profileServiceEmpty()
		);

		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.REGISTRATION);
		RegistrationContext context = RegistrationContext.builder()
				.identity(new ConnectionIdentity("Player", "127.0.0.1"))
				.provider(providerContext())
				.build();
		pipelineState.setScenario(context);
		pipelineState.put(UsernameStateItem.KEY, snapshot());

		PolicyState state = new PolicyState();
		state.setResult(PipelineResult.complete());
		phase.execute(pipelineState, state).toCompletableFuture().join();

		assertNotNull(state.getResult());
		assertEquals("denied", state.getResult().getMessage());
		verify(conflictHandler).handle(any(), eq("Player"));
	}

	@DisplayName("Authentication policy phase updates the identity username")
	@Test
	void authenticationPhaseAppliesHandlerResult() {
		UsernameConflictHandler conflictHandler = mock(UsernameConflictHandler.class);
		when(conflictHandler.handle(any(), eq("Player"))).thenReturn(
				UsernameConflictResult.allowed("Resolved")
		);

		ReviewAuthenticationUsernamePhase phase = new ReviewAuthenticationUsernamePhase(
				conflictHandler,
				accountServiceEmpty(),
				linkServiceEmpty(),
				profileServiceEmpty()
		);

		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.AUTHENTICATION);
		AuthContext context = AuthContext.builder()
				.identity(new ConnectionIdentity("Player", "127.0.0.1"))
				.provider(providerContext())
				.build();
		pipelineState.setScenario(context);
		pipelineState.put(UsernameStateItem.KEY, snapshot());

		var state = new me.whereareiam.identica.pipeline.state.scenario.type.authentication.PolicyState();
		state.setResult(PipelineResult.complete());
		phase.execute(pipelineState, state).toCompletableFuture().join();

		verify(conflictHandler).handle(any(), eq("Player"));
		assertEquals("Resolved", pipelineState.getScenario().getUsername());
	}

	private PrepareAccountCandidateItem candidate() {
		PrepareAccountCandidateItem candidate = new PrepareAccountCandidateItem();
		candidate.setUniqueId(UUID.randomUUID());
		candidate.setAccount(Account.builder().uniqueId(candidate.getUniqueId()).username("Player").build());
		candidate.setLink(AccountProviderLink.builder()
				.uniqueId(candidate.getUniqueId())
				.providerId("premium")
				.providerSubject("subject")
				.primaryLink(true)
				.build());
		candidate.setProfile(AccountProviderProfile.builder()
				.providerId("premium")
				.providerSubject("subject")
				.providerUsername("Player")
				.build());
		return candidate;
	}

	private UsernameStateItem snapshot() {
		return UsernameStateItem.builder()
				.uniqueId(UUID.randomUUID())
				.providerId("premium")
				.providerSubject("subject")
				.source(AccountUsernameSource.PROVIDER)
				.build();
	}

	private ProviderLinkPersistenceService linkServiceEmpty() {
		ProviderLinkPersistenceService service = mock(ProviderLinkPersistenceService.class);
		when(service.findBySubject(anyString(), anyString())).thenReturn(Optional.empty());
		return service;
	}

	private ProviderProfilePersistenceService profileServiceEmpty() {
		ProviderProfilePersistenceService service = mock(ProviderProfilePersistenceService.class);
		when(service.findBySubject(anyString(), anyString())).thenReturn(Optional.empty());
		return service;
	}

	private AccountPersistenceService accountServiceEmpty() {
		AccountPersistenceService service = mock(AccountPersistenceService.class);
		when(service.findByUniqueId(any())).thenReturn(Optional.empty());
		return service;
	}

	private AccountUsernamePersistenceService usernamePersistenceService() {
		AccountUsernamePersistenceService service = mock(AccountUsernamePersistenceService.class);
		when(service.find(any())).thenReturn(Optional.empty());
		return service;
	}

	private ProviderManager providerManager() {
		return providerManager(true);
	}

	private ProviderManager providerManager(boolean participates) {
		ProviderManager providerManager = mock(ProviderManager.class);
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId("premium");
		descriptor.setName("Premium");
		descriptor.setVersion("1");
		descriptor.setMain("Main");
		descriptor.setSupportedPlatforms(List.of("velocity"));
		descriptor.setTraits(participates ? java.util.Set.of(ProviderTrait.AUTHORITATIVE_USERNAME) : java.util.Set.of());
		descriptor.setSupportedFeatureIds(List.of());

		InternalProvider provider = InternalProvider.builder()
				.descriptor(descriptor)
				.state(me.whereareiam.identica.type.provider.ProviderState.ENABLED)
				.build();
		when(providerManager.getProviders()).thenReturn(List.of(provider));
		return providerManager;
	}

	private ProviderContext providerContext() {
		return ProviderContext.builder()
				.providerId("premium")
				.providerSubject("subject")
				.providerUsername("Player")
				.build();
	}

	private UsernameMessages messages() {
		UsernameMessages.Pipeline.Prepare prepare = new UsernameMessages.Pipeline.Prepare();
		prepare.setFailed(List.of("failed"));
		UsernameMessages.Pipeline.Identity identity = new UsernameMessages.Pipeline.Identity();
		identity.setSynchronizationFailed(List.of("sync"));
		UsernameMessages.Pipeline.Policy policy = new UsernameMessages.Pipeline.Policy();
		policy.setPersistenceFailed(List.of("persist"));
		policy.setConflictDenied(List.of("denied"));
		policy.setEntrypointRequired(List.of("entrypoint"));

		UsernameMessages.Pipeline pipeline = new UsernameMessages.Pipeline();
		pipeline.setPrepare(prepare);
		pipeline.setIdentity(identity);
		pipeline.setPolicy(policy);

		UsernameMessages messages = new UsernameMessages();
		messages.setPipeline(pipeline);
		return messages;
	}
}

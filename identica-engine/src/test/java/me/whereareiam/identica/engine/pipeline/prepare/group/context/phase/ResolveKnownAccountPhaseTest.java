package me.whereareiam.identica.engine.pipeline.prepare.group.context.phase;

import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Resolve Known Account Phase")
class ResolveKnownAccountPhaseTest {
	@Mock
	private ProviderOperations providerOperations;
	@Mock
	private ProviderLinkPersistenceService providerLinkPersistenceService;
	@Mock
	private AccountPersistenceService accountPersistenceService;

	private final Engine engine = new Engine();
	private ResolveKnownAccountPhase phase;

	@BeforeEach
	void setUp() {
		engine.getScenarios().getAuthentication().setJourneyMode(JourneyMode.SEAMLESS);
		engine.getScenarios().getRegistration().setJourneyMode(JourneyMode.INTERACTIVE);
		phase = new ResolveKnownAccountPhase(
				providerOperations,
				providerLinkPersistenceService,
				accountPersistenceService,
				() -> engine
		);
	}

	@DisplayName("Finds the account through the subject a provider resolves for the connection")
	@Test
	void resolvesTheAccountOfTheSubject() {
		UUID uniqueId = UUID.randomUUID();
		AccountProviderLink link = link(uniqueId, "premium");
		when(providerOperations.discoverSubject(any())).thenReturn(subject("premium", "premium-subject"));
		when(providerLinkPersistenceService.findBySubject("premium", "premium-subject")).thenReturn(Optional.of(link));
		when(providerLinkPersistenceService.findByUniqueId(uniqueId)).thenReturn(List.of(link));
		when(providerOperations.selectPreferredLink(List.of(link))).thenReturn(link);

		PrepareContextItem context = execute(PrepareStage.HANDSHAKE);

		assertEquals(link.getProviderSubject(), context.getPreferredLink().getProviderSubject());
		assertEquals(JourneyMode.SEAMLESS, context.getJourneyMode());
		verify(accountPersistenceService, never()).findByUsername(any());
	}

	@DisplayName("Falls back to the only account that uses the username")
	@Test
	void resolvesTheAccountOfTheUsername() {
		UUID uniqueId = UUID.randomUUID();
		AccountProviderLink link = link(uniqueId, "credential");
		when(providerOperations.discoverSubject(any())).thenReturn(subject("credential", "credential-subject"));
		when(providerLinkPersistenceService.findBySubject("credential", "credential-subject")).thenReturn(Optional.empty());
		when(accountPersistenceService.findByUsername("PlayerOne")).thenReturn(List.of(account(uniqueId)));
		when(providerLinkPersistenceService.findByUniqueId(uniqueId)).thenReturn(List.of(link));
		when(providerOperations.selectPreferredLink(List.of(link))).thenReturn(link);

		PrepareContextItem context = execute(PrepareStage.HANDSHAKE);

		assertEquals("credential", context.getPreferredLink().getProviderId());
	}

	@DisplayName("Knows no account when several accounts use the username and heads to registration")
	@Test
	void resolvesNothingForAnAmbiguousUsername() {
		when(providerOperations.discoverSubject(any())).thenReturn(null);
		when(accountPersistenceService.findByUsername("PlayerOne"))
				.thenReturn(List.of(account(UUID.randomUUID()), account(UUID.randomUUID())));

		PrepareContextItem context = execute(PrepareStage.HANDSHAKE);

		assertNull(context.getPreferredLink());
		assertEquals(JourneyMode.INTERACTIVE, context.getJourneyMode());
	}

	@DisplayName("Only runs during the handshake")
	@Test
	void skipsTheProfileStage() {
		assertFalse(phase.supports(PipelineState.initial(), state(PrepareStage.PROFILE)));
	}

	private PrepareContextItem execute(PrepareStage stage) {
		PipelineState pipelineState = PipelineState.initial();
		phase.execute(pipelineState, state(stage)).toCompletableFuture().join();

		return pipelineState.item(PrepareContextItem.class).orElseThrow();
	}

	private PrepareGroupState state(PrepareStage stage) {
		PrepareGroupState state = new PrepareGroupState();
		state.setRequest(PrepareRequest.builder()
				.stage(stage)
				.identity(new ConnectionIdentity("PlayerOne", "127.0.0.1"))
				.build());

		return state;
	}

	private SubjectResolution subject(String providerId, String providerSubject) {
		return SubjectResolution.builder()
				.providerId(providerId)
				.providerSubject(providerSubject)
				.build();
	}

	private Account account(UUID uniqueId) {
		return Account.builder()
				.uniqueId(uniqueId)
				.username("PlayerOne")
				.build();
	}

	private AccountProviderLink link(UUID uniqueId, String providerId) {
		return AccountProviderLink.builder()
				.uniqueId(uniqueId)
				.providerId(providerId)
				.providerSubject(providerId + "-subject")
				.primaryLink(true)
				.build();
	}
}

package me.whereareiam.identica.engine.pipeline.prepare.group.policy.phase;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.username.UsernameAssigner;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.username.UsernameAssignment;
import me.whereareiam.identica.model.pipeline.prepare.PrepareAccountCandidateItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Apply Username Phase")
class ApplyUsernamePhaseTest {
	private static final String USERNAME = "steve";
	private static final String PROVIDER = "example";
	private static final String TAKEN = "The username steve is already taken.";

	@Mock
	private UsernameAssigner assigner;
	@Mock
	private ProviderLinkPersistenceService links;

	@BeforeEach
	void prepare() {
		when(links.findBySubject(anyString(), anyString())).thenReturn(Optional.empty());
	}

	@DisplayName("Refuses before any prompt when a settled claim chose the provider")
	@ParameterizedTest(name = "{0}")
	@EnumSource(value = ProviderClaimOrigin.class, names = {"MIGRATION", "ACCOUNT_LINK", "HOSTNAME", "VERIFIED_SUBJECT", "PLAYER_CHOICE"})
	void refusesOnASettledProvider(ProviderClaimOrigin origin) {
		when(assigner.preview(any())).thenReturn(UsernameAssignment.refuse(TAKEN));

		PipelineState state = run(origin);

		PrepareDecision decision = state.get(PrepareDecision.KEY).orElseThrow();
		assertTrue(decision.isDenied());
		assertEquals(TAKEN, decision.getDenialMessage());
	}

	@DisplayName("Leaves the refusal to the scenario when the provider is only guessed")
	@ParameterizedTest(name = "{0}")
	@EnumSource(value = ProviderClaimOrigin.class, names = {"NAME_MATCH", "DISCOVERY"})
	void leavesTheRefusalOfAGuessedProviderToTheScenario(ProviderClaimOrigin origin) {
		when(assigner.preview(any())).thenReturn(UsernameAssignment.refuse(TAKEN));

		PipelineState state = run(origin);

		assertTrue(state.get(PrepareDecision.KEY).isEmpty());
		assertEquals(USERNAME, state.get(PrepareAccountCandidateItem.KEY).orElseThrow().getAccount().getUsername());
	}

	@DisplayName("Gives a guessed provider's account the username the rules assign")
	@Test
	void appliesTheAssignedUsernameOfAGuessedProvider() {
		when(assigner.preview(any())).thenReturn(UsernameAssignment.of("Steve", "Steve", AccountUsernameSource.PROVIDER));

		PipelineState state = run(ProviderClaimOrigin.NAME_MATCH);

		PrepareAccountCandidateItem candidate = state.get(PrepareAccountCandidateItem.KEY).orElseThrow();
		assertTrue(state.get(PrepareDecision.KEY).isEmpty());
		assertEquals("Steve", candidate.getAccount().getUsername());
		assertEquals("Steve", candidate.getEffectiveUsername());
	}

	private PipelineState run(ProviderClaimOrigin origin) {
		PrepareGroupState groupState = new PrepareGroupState();
		groupState.setRequest(PrepareRequest.builder()
				.connectionKey("steve|127.0.0.1||")
				.stage(PrepareStage.PROFILE)
				.identity(new ConnectionIdentity(USERNAME, "127.0.0.1"))
				.build());

		PrepareContextItem context = new PrepareContextItem();
		context.setSelection(ProviderSelection.of(claim(origin), List.of(), List.of()));
		context.setProvider(ProviderContext.of(PROVIDER, UUID.randomUUID().toString(), USERNAME));

		UUID account = UUID.randomUUID();
		PipelineState state = PipelineState.initial();
		state.put(PrepareContextItem.KEY, context);
		state.put(PrepareAccountCandidateItem.KEY, new PrepareAccountCandidateItem(
				account,
				null,
				Account.builder().uniqueId(account).username(USERNAME).build(),
				null,
				null
		));
		new ApplyUsernamePhase(assigner, links)
				.execute(state, groupState)
				.toCompletableFuture()
				.join();

		return state;
	}

	private static ProviderClaim claim(ProviderClaimOrigin origin) {
		if (origin == ProviderClaimOrigin.VERIFIED_SUBJECT) return ProviderClaim.verified(PROVIDER, "proof");

		return ProviderClaim.of(PROVIDER, origin);
	}
}

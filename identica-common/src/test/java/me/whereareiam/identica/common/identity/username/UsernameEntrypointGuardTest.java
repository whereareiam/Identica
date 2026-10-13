package me.whereareiam.identica.common.identity.username;

import me.whereareiam.identica.conflict.UsernameConflictSchema;
import me.whereareiam.identica.model.config.UsernameMessages;
import me.whereareiam.identica.model.conflict.ConflictContext;
import me.whereareiam.identica.model.conflict.ConflictResolution;
import me.whereareiam.identica.model.conflict.participant.ConflictParticipant;
import me.whereareiam.identica.model.conflict.participant.ConflictParticipantRole;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("Username Entrypoint Guard")
class UsernameEntrypointGuardTest {
	private static final Set<ProviderClaimOrigin> ENTRYPOINT_OR_STRONGER = Set.of(
			ProviderClaimOrigin.MIGRATION,
			ProviderClaimOrigin.ACCOUNT_LINK,
			ProviderClaimOrigin.ENTRYPOINT
	);

	@Mock
	private ProviderOperations providerOperations;
	@Mock
	private ProviderSelector providerSelector;

	private UsernameEntrypointGuard guard;

	@BeforeEach
	void setUp() {
		lenient().when(providerOperations.hasEntrypoints(any())).thenReturn(true);
		lenient().when(providerSelector.isAtLeast(any(), eq(ProviderClaimOrigin.ENTRYPOINT))).thenAnswer(invocation ->
				ENTRYPOINT_OR_STRONGER.contains(invocation.getArgument(0, ProviderClaimOrigin.class)));
		guard = new UsernameEntrypointGuard(this::messages, providerOperations, providerSelector);
	}

	@DisplayName("Accepts the conflict when the arriving provider won through its hostname")
	@Test
	void acceptsAWinThroughTheHostname() {
		assertNull(guard.guard(context(ProviderClaim.of("alpha", ProviderClaimOrigin.ENTRYPOINT))));
	}

	@DisplayName("Accepts the conflict when the arriving provider won with a stronger claim than its hostname")
	@Test
	void acceptsAWinWithAStrongerClaim() {
		assertNull(guard.guard(context(ProviderClaim.of("alpha", ProviderClaimOrigin.ACCOUNT_LINK))));
	}

	@DisplayName("Requires the hostname when the arriving provider won only by a verified subject without an account")
	@Test
	void requiresTheHostnameForAVerifiedSubjectWithoutAnAccount() {
		ConflictResolution resolution = guard.guard(context(ProviderClaim.verified("alpha", "alpha-proof")));

		assertEquals(ConflictResolution.Decision.DENY, resolution.getDecision());
	}

	@DisplayName("Requires the hostname when the arriving provider won only by a weaker claim")
	@Test
	void requiresTheHostnameForAWeakerClaim() {
		ConflictResolution resolution = guard.guard(context(ProviderClaim.of("alpha", ProviderClaimOrigin.DISCOVERY)));

		assertEquals(ConflictResolution.Decision.DENY, resolution.getDecision());
	}

	@DisplayName("Requires the hostname when another provider won the selection")
	@Test
	void requiresTheHostnameWhenAnotherProviderWon() {
		ConflictResolution resolution = guard.guard(context(ProviderClaim.of("beta", ProviderClaimOrigin.ENTRYPOINT)));

		assertEquals(ConflictResolution.Decision.DENY, resolution.getDecision());
	}

	private ConflictContext context(ProviderClaim winner) {
		ConflictContext context = ConflictContext.builder()
				.key("username")
				.hook("prepare")
				.build();
		context.putParticipant(participant(UsernameConflictSchema.ROLE_INCOMING, "alpha"));
		context.putParticipant(participant(UsernameConflictSchema.ROLE_EXISTING, "beta"));
		context.putAttribute(UsernameConflictSchema.ATTRIBUTE_SELECTION, ProviderSelection.of(winner, List.of(), List.of()));
		return context;
	}

	private static ConflictParticipant participant(ConflictParticipantRole role, String providerId) {
		ConflictParticipant participant = ConflictParticipant.builder()
				.role(role)
				.build();
		participant.putAttribute(UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_ID, providerId);
		return participant;
	}

	private UsernameMessages messages() {
		UsernameMessages.Pipeline.Policy policy = new UsernameMessages.Pipeline.Policy();
		policy.setEntrypointRequired(List.of());
		UsernameMessages.Pipeline pipeline = new UsernameMessages.Pipeline();
		pipeline.setPolicy(policy);
		UsernameMessages messages = new UsernameMessages();
		messages.setPipeline(pipeline);
		return messages;
	}
}

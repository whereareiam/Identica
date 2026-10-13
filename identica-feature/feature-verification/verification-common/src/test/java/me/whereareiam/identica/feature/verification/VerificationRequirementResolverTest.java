package me.whereareiam.identica.feature.verification;

import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeLifecycle;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeStore;
import me.whereareiam.identica.feature.verification.database.VerificationPersistenceService;
import me.whereareiam.identica.feature.verification.model.challenge.VerificationChallengeResult;
import me.whereareiam.identica.feature.verification.model.interaction.CodeVerificationInteraction;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionRequest;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionResult;
import me.whereareiam.identica.feature.verification.resolution.VerificationRequirementResolver;
import me.whereareiam.identica.feature.verification.type.UnavailableSelectionPolicy;
import me.whereareiam.identica.feature.verification.type.status.VerificationChallengeStatus;
import me.whereareiam.identica.feature.verification.type.status.VerificationResolutionStatus;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.provider.ProviderState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class VerificationRequirementResolverTest {
	private final ProviderManager providers = mock(ProviderManager.class);
	private final VerificationPolicyResolver policy = mock(VerificationPolicyResolver.class);
	private final VerificationPersistenceService persistence = mock(VerificationPersistenceService.class);
	private final VerificationChallengeStore challenges = mock(VerificationChallengeStore.class);
	private final VerificationChallengeLifecycle lifecycle = mock(VerificationChallengeLifecycle.class);
	private final VerificationRequirementResolver resolver = new VerificationRequirementResolver(
			persistence,
			challenges,
			policy,
			mock(VerificationRegistry.class),
			providers,
			lifecycle
	);

	@Test
	void advertisedFeatureEnforcesRequiredVerificationWithoutCapabilityMetadata() {
		credentialSupportsRequiredVerification();
		VerificationResolutionRequest request = request(null);

		assertEquals(VerificationResolutionStatus.DENIED, resolver.resolveVerification(request).getStatus());
		verify(persistence).findSelection(request.getUniqueId(), "credential");
		verifyNoInteractions(lifecycle);
	}

	@Test
	void rejectsAWrongCodeAndLeavesTheChallengeWaiting() {
		credentialSupportsRequiredVerification();
		VerificationResolutionRequest request = request("000000");
		doReturn(VerificationChallengeResult.of(VerificationChallengeStatus.INVALID, "challenge", "totp", "credential", true))
				.when(lifecycle).submitChallengeInteraction(eq(request.getUniqueId()), eq("credential"), eq("authentication"), any());

		VerificationResolutionResult result = resolver.resolveVerification(request);

		assertEquals(VerificationResolutionStatus.REJECTED, result.getStatus());
		assertEquals(VerificationChallengeStatus.INVALID, result.getRejection());
		assertEquals("challenge", result.getChallengeId());
		verify(lifecycle).submitChallengeInteraction(eq(request.getUniqueId()), eq("credential"), eq("authentication"),
				argThat(interaction -> interaction instanceof CodeVerificationInteraction code
						&& "000000".equals(code.getCode()) && request.getUniqueId().equals(code.getSubjectUniqueId())));
		verify(persistence, never()).findSelection(any(), any());
	}

	@Test
	void resolvesAVerifiedCodeAsSatisfied() {
		credentialSupportsRequiredVerification();
		VerificationResolutionRequest request = request("123456");
		doReturn(VerificationChallengeResult.of(VerificationChallengeStatus.VERIFIED, "challenge", "totp", "credential", true))
				.when(lifecycle).submitChallengeInteraction(any(UUID.class), any(), any(), any());
		when(challenges.consumeVerified(request.getUniqueId(), "credential", "authentication")).thenReturn(true);

		VerificationResolutionResult result = resolver.resolveVerification(request);

		assertEquals(VerificationResolutionStatus.SATISFIED, result.getStatus());
		assertNull(result.getRejection());
	}

	private void credentialSupportsRequiredVerification() {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId("credential");
		descriptor.setSupportedFeatureIds(List.of("verification"));
		InternalProvider provider = InternalProvider.builder().descriptor(descriptor).state(ProviderState.ENABLED).build();
		when(providers.getProviders()).thenReturn(List.of(provider));
		when(policy.resolveProviderPolicy("credential")).thenReturn(new VerificationPolicyResolver.ResolvedProviderPolicy(
				true, true, UnavailableSelectionPolicy.KEEP_LOCKED));
	}

	private static VerificationResolutionRequest request(String code) {
		return VerificationResolutionRequest.builder()
				.uniqueId(UUID.randomUUID())
				.providerId("credential")
				.purpose("authentication")
				.code(code)
				.build();
	}
}

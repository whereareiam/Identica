package me.whereareiam.identica.feature.verification;

import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeLifecycle;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeStore;
import me.whereareiam.identica.feature.verification.database.VerificationPersistenceService;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionRequest;
import me.whereareiam.identica.feature.verification.resolution.VerificationRequirementResolver;
import me.whereareiam.identica.feature.verification.type.UnavailableSelectionPolicy;
import me.whereareiam.identica.feature.verification.type.status.VerificationResolutionStatus;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.provider.ProviderState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class VerificationRequirementResolverTest {
	@Test
	void advertisedFeatureEnforcesRequiredVerificationWithoutCapabilityMetadata() {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId("credential");
		descriptor.setSupportedFeatureIds(List.of("verification"));
		ProviderManager providers = mock(ProviderManager.class);
		InternalProvider provider = InternalProvider.builder().descriptor(descriptor).state(ProviderState.ENABLED).build();
		when(providers.getProviders()).thenReturn(List.of(provider));
		VerificationPolicyResolver policy = mock(VerificationPolicyResolver.class);
		when(policy.resolveProviderPolicy("credential")).thenReturn(new VerificationPolicyResolver.ResolvedProviderPolicy(
				true, true, UnavailableSelectionPolicy.KEEP_LOCKED));
		VerificationPersistenceService persistence = mock(VerificationPersistenceService.class);
		VerificationRequirementResolver resolver = new VerificationRequirementResolver(
				persistence,
				mock(VerificationChallengeStore.class),
				policy,
				mock(VerificationRegistry.class),
				providers,
				mock(VerificationChallengeLifecycle.class)
		);
		VerificationResolutionRequest request = VerificationResolutionRequest.builder()
				.uniqueId(UUID.randomUUID()).providerId("credential").purpose("authentication").build();

		assertEquals(VerificationResolutionStatus.DENIED, resolver.resolveVerification(request).getStatus());
		verify(persistence).findSelection(request.getUniqueId(), "credential");
	}
}

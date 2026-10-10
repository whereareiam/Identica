package me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase;

import me.whereareiam.identica.engine.pipeline.prepare.runtime.ConnectionProviderContextResolver;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.type.PrepareStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Resolve Profile Phase")
class ResolveProfilePhaseTest {
	private static final String USERNAME = "PlayerOne";

	@Mock
	private ProviderOperations providerOperations;

	@DisplayName("Carries a subject its provider verified into the provider context")
	@Test
	void carriesAVerifiedSubject() {
		assertTrue(resolved(true).isSubjectVerified());
	}

	@DisplayName("Keeps a subject its provider only derived unverified")
	@Test
	void keepsADerivedSubjectUnverified() {
		assertFalse(resolved(false).isSubjectVerified());
	}

	private ProviderContext resolved(boolean verified) {
		when(providerOperations.discoverSubject(any())).thenReturn(SubjectResolution.builder()
				.providerId("token")
				.providerSubject("token-subject")
				.verified(verified)
				.build());

		PrepareGroupState groupState = new PrepareGroupState();
		groupState.setRequest(PrepareRequest.builder()
				.connectionKey("PlayerOne|127.0.0.1||")
				.stage(PrepareStage.PROFILE)
				.identity(new ConnectionIdentity(USERNAME, "127.0.0.1"))
				.build());

		PipelineState state = PipelineState.initial();
		new ResolveProfilePhase(providerOperations, new ConnectionProviderContextResolver(providerOperations))
				.execute(state, groupState)
				.toCompletableFuture()
				.join();

		ProviderContext provider = state.item(PrepareContextItem.class).orElseThrow().getProvider();
		assertNotNull(provider);
		assertEquals("token", provider.getProviderId());
		assertEquals("token-subject", provider.getProviderSubject());
		return provider;
	}
}

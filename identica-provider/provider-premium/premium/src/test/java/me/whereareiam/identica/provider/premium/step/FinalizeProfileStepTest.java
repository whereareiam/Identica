package me.whereareiam.identica.provider.premium.step;

import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.pipeline.step.shared.FinalizeProfileStep;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileSnapshot;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Finalize-Profile Step")
class FinalizeProfileStepTest {
	@Mock
	private PremiumProfileStore profileStore;
	@Mock
	private HandshakeStore handshakeStore;

	private FinalizeProfileStep step;

	@BeforeEach
	void setUp() {
		step = new FinalizeProfileStep(
				PremiumMessages::new,
				profileStore,
				handshakeStore,
				Engine::new
		);
	}

	@DisplayName("Finalize profile completes the provider stage for registration")
	@Test
	void finalizeProfileCompletesRegistration() {
		RegistrationContext context = RegistrationContext.builder()
				.identity(new ConnectionIdentity("whereareiam", "127.0.0.1"))
				.build();
		context.setProvider(ProviderContext.of("premium", null, "whereareiam", null));
		when(profileStore.find("whereareiam")).thenReturn(new PremiumProfileSnapshot("premium-subject", System.currentTimeMillis()));

		StepResult result = step.execute(context, PipelineState.initial()).join();

		assertEquals(StepResult.StepStatus.COMPLETE, result.getStatus());
		assertNotNull(context.getProvider());
		assertEquals("premium-subject", context.getProvider().getProviderSubject());
	}

	@DisplayName("Finalize profile continues to later provider steps during authentication")
	@Test
	void finalizeProfileContinuesAuthentication() {
		AuthContext context = AuthContext.builder()
				.identity(new ConnectionIdentity("whereareiam", "127.0.0.1"))
				.provider(ProviderContext.of("premium", null, "whereareiam", null))
				.build();
		when(profileStore.find("whereareiam")).thenReturn(new PremiumProfileSnapshot("premium-subject", System.currentTimeMillis()));

		StepResult result = step.execute(context, PipelineState.initial()).join();

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
		assertNotNull(context.getProvider());
		assertEquals("premium-subject", context.getProvider().getProviderSubject());
	}

	@DisplayName("Finalize profile continues to later provider steps during migration")
	@Test
	void finalizeProfileContinuesMigration() {
		MigrationContext context = MigrationContext.builder()
				.identity(new ConnectionIdentity("whereareiam", "127.0.0.1"))
				.targetProviderId("premium")
				.build();
		context.setProvider(ProviderContext.of("premium", null, "whereareiam", null));
		when(profileStore.find("whereareiam")).thenReturn(new PremiumProfileSnapshot("premium-subject", System.currentTimeMillis()));

		StepResult result = step.execute(context, PipelineState.initial()).join();

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
		assertNotNull(context.getProvider());
		assertEquals("premium-subject", context.getProvider().getProviderSubject());
	}
}

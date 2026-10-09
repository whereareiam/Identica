package me.whereareiam.identica.provider.premium.pipeline.step.type.authentication;

import com.google.inject.Guice;
import com.google.inject.Injector;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionResult;
import me.whereareiam.identica.feature.verification.type.status.VerificationResolutionStatus;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.config.defaults.PremiumMessagesDefaults;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PremiumVerificationStepTest {
	@Test
	void skipsWithoutVerificationBindingsOrLookingUpAccount() {
		FeatureRegistry features = mock(FeatureRegistry.class);
		ProviderLinkPersistenceService links = mock(ProviderLinkPersistenceService.class);
		Injector injector = Guice.createInjector(binder -> {
			binder.bind(FeatureRegistry.class).toInstance(features);
			binder.bind(ProviderLinkPersistenceService.class).toInstance(links);
			binder.bind(PremiumMessages.class).toInstance(new PremiumMessages());
		});

		StepResult result = injector.getInstance(PremiumVerificationStep.class).execute(context()).join();

		assertEquals(StepResult.StepStatus.COMPLETE, result.getStatus());
		verifyNoInteractions(links);
	}

	@Test
	void preservesRequiredVerificationWhenInstalled() {
		FeatureRegistry features = mock(FeatureRegistry.class);
		when(features.isEnabled("premium", "verification")).thenReturn(true);
		ProviderLinkPersistenceService links = mock(ProviderLinkPersistenceService.class);
		UUID accountId = UUID.randomUUID();
		AccountProviderLink link = mock(AccountProviderLink.class);
		when(link.getUniqueId()).thenReturn(accountId);
		when(links.findBySubject("premium", "subject")).thenReturn(Optional.of(link));
		VerificationService verification = mock(VerificationService.class);
		when(verification.resolveVerification(any())).thenReturn(VerificationResolutionResult.of(
				VerificationResolutionStatus.WAITING, "challenge", "totp", true, false));
		Injector injector = Guice.createInjector(binder -> {
			binder.bind(FeatureRegistry.class).toInstance(features);
			binder.bind(ProviderLinkPersistenceService.class).toInstance(links);
			binder.bind(VerificationService.class).toInstance(verification);
			binder.bind(PremiumMessages.class).toInstance(new PremiumMessagesDefaults().supply(new PremiumMessages()));
		});

		StepResult result = injector.getInstance(PremiumVerificationStep.class).execute(context()).join();

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		verify(verification).resolveVerification(argThat(request -> accountId.equals(request.getUniqueId())
				&& "premium".equals(request.getProviderId()) && "authentication".equals(request.getPurpose())));
	}

	private AuthContext context() {
		return AuthContext.builder()
				.identity(new ConnectionIdentity("PlayerOne", "127.0.0.1"))
				.provider(ProviderContext.of("premium", "subject", "PlayerOne", null))
				.build();
	}
}

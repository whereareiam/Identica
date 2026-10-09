package me.whereareiam.identica.provider.credential.pipeline.step.type.authentication;

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
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.defaults.CredentialMessagesDefaults;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CredentialAuthenticationVerificationStepTest {
	@Test
	void skipsWithoutVerificationBindingsOrLookingUpAccount() {
		FeatureRegistry features = mock(FeatureRegistry.class);
		ProviderLinkPersistenceService links = mock(ProviderLinkPersistenceService.class);
		Injector injector = Guice.createInjector(binder -> {
			binder.bind(FeatureRegistry.class).toInstance(features);
			binder.bind(ProviderLinkPersistenceService.class).toInstance(links);
			binder.bind(CredentialMessages.class).toInstance(new CredentialMessages());
		});

		StepResult result = injector.getInstance(CredentialAuthenticationVerificationStep.class).execute(context()).join();

		assertEquals(StepResult.StepStatus.COMPLETE, result.getStatus());
		verifyNoInteractions(links);
	}

	@Test
	void preservesRequiredVerificationWhenInstalled() {
		FeatureRegistry features = mock(FeatureRegistry.class);
		when(features.isEnabled("credential", "verification")).thenReturn(true);
		ProviderLinkPersistenceService links = mock(ProviderLinkPersistenceService.class);
		UUID accountId = UUID.randomUUID();
		AccountProviderLink link = mock(AccountProviderLink.class);
		when(link.getUniqueId()).thenReturn(accountId);
		when(links.findBySubject("credential", "subject")).thenReturn(Optional.of(link));
		VerificationService verification = mock(VerificationService.class);
		when(verification.resolveVerification(any())).thenReturn(VerificationResolutionResult.of(
				VerificationResolutionStatus.WAITING, "challenge", "totp", true, false));
		Injector injector = Guice.createInjector(binder -> {
			binder.bind(FeatureRegistry.class).toInstance(features);
			binder.bind(ProviderLinkPersistenceService.class).toInstance(links);
			binder.bind(VerificationService.class).toInstance(verification);
			binder.bind(CredentialMessages.class).toInstance(new CredentialMessagesDefaults().supply(new CredentialMessages()));
		});

		StepResult result = injector.getInstance(CredentialAuthenticationVerificationStep.class).execute(context()).join();

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		verify(verification).resolveVerification(argThat(request -> accountId.equals(request.getUniqueId())
				&& "credential".equals(request.getProviderId()) && "authentication".equals(request.getPurpose())));
	}

	private AuthContext context() {
		return AuthContext.builder()
				.identity(new ConnectionIdentity("PlayerOne", "127.0.0.1"))
				.provider(ProviderContext.of("credential", "subject", "PlayerOne", null))
				.build();
	}
}

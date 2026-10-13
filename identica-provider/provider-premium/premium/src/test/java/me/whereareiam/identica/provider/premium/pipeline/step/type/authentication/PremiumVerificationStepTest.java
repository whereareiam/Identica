package me.whereareiam.identica.provider.premium.pipeline.step.type.authentication;

import com.google.inject.Guice;
import com.google.inject.Injector;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationCodeAttempt;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionResult;
import me.whereareiam.identica.feature.verification.type.status.VerificationChallengeStatus;
import me.whereareiam.identica.feature.verification.type.status.VerificationResolutionStatus;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.config.defaults.PremiumMessagesDefaults;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class PremiumVerificationStepTest {
	private static final String INVALID_CODE = "Invalid code.";
	private static final String NO_PENDING = "Nothing to confirm.";

	private final FeatureRegistry features = mock(FeatureRegistry.class);
	private final ProviderLinkPersistenceService links = mock(ProviderLinkPersistenceService.class);
	private final VerificationService verification = mock(VerificationService.class);
	private final UUID accountId = UUID.randomUUID();

	@Test
	void skipsWithoutVerificationBindingsOrLookingUpAccount() {
		Injector injector = Guice.createInjector(binder -> {
			binder.bind(FeatureRegistry.class).toInstance(features);
			binder.bind(ProviderLinkPersistenceService.class).toInstance(links);
			binder.bind(PremiumMessages.class).toInstance(new PremiumMessages());
		});

		StepResult result = injector.getInstance(PremiumVerificationStep.class).execute(context(), PipelineState.initial()).join();

		assertEquals(StepResult.StepStatus.COMPLETE, result.getStatus());
		verifyNoInteractions(links);
	}

	@Test
	void preservesRequiredVerificationWhenInstalled() {
		resolvesTo(VerificationResolutionResult.of(VerificationResolutionStatus.WAITING, "challenge", "totp", true, false));

		StepResult result = step().execute(context(), PipelineState.initial()).join();

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		verify(verification).resolveVerification(argThat(request -> accountId.equals(request.getUniqueId())
				&& "premium".equals(request.getProviderId()) && "authentication".equals(request.getPurpose())
				&& request.getCode() == null));
	}

	@Test
	void hasTheResolutionCheckTheCodeTheCommandHandedToTheRun() {
		resolvesTo(VerificationResolutionResult.of(VerificationResolutionStatus.SATISFIED, null, null, true, false));
		PipelineState state = PipelineState.initial();
		state.put(VerificationCodeAttempt.KEY, new VerificationCodeAttempt("123456"));

		StepResult result = step().execute(context(), state).join();

		assertEquals(StepResult.StepStatus.COMPLETE, result.getStatus());
		assertFalse(state.has(VerificationCodeAttempt.KEY), "the code must not outlive the step that checked it");
		verify(verification).resolveVerification(argThat(request -> "123456".equals(request.getCode())));
	}

	@Test
	void keepsWaitingWithTheInvalidCodeMessageForAWrongCode() {
		resolvesTo(rejected(VerificationChallengeStatus.INVALID));

		StepResult result = step().execute(context(), stateWithCode()).join();

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		assertEquals(INVALID_CODE, result.getMessage());
	}

	@Test
	void keepsWaitingWithTheNoPendingMessageForACodeNoChallengeTakes() {
		resolvesTo(rejected(VerificationChallengeStatus.EXPIRED));

		StepResult result = step().execute(context(), stateWithCode()).join();

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		assertEquals(NO_PENDING, result.getMessage());
	}

	private PremiumVerificationStep step() {
		when(features.isEnabled("premium", "verification")).thenReturn(true);
		AccountProviderLink link = mock(AccountProviderLink.class);
		when(link.getUniqueId()).thenReturn(accountId);
		when(links.findBySubject("premium", "subject")).thenReturn(Optional.of(link));
		Injector injector = Guice.createInjector(binder -> {
			binder.bind(FeatureRegistry.class).toInstance(features);
			binder.bind(ProviderLinkPersistenceService.class).toInstance(links);
			binder.bind(VerificationService.class).toInstance(verification);
			binder.bind(VerificationMessages.class).toInstance(verificationMessages());
			binder.bind(PremiumMessages.class).toInstance(new PremiumMessagesDefaults().supply(new PremiumMessages()));
		});

		return injector.getInstance(PremiumVerificationStep.class);
	}

	private void resolvesTo(VerificationResolutionResult result) {
		when(verification.resolveVerification(any())).thenReturn(result);
	}

	private static VerificationResolutionResult rejected(VerificationChallengeStatus rejection) {
		return VerificationResolutionResult.builder()
				.status(VerificationResolutionStatus.REJECTED)
				.challengeId("challenge")
				.methodId("totp")
				.required(true)
				.rejection(rejection)
				.build();
	}

	private static PipelineState stateWithCode() {
		PipelineState state = PipelineState.initial();
		state.put(VerificationCodeAttempt.KEY, new VerificationCodeAttempt("000000"));
		return state;
	}

	private static VerificationMessages verificationMessages() {
		VerificationMessages.Commands.Confirm confirm = new VerificationMessages.Commands.Confirm();
		confirm.setInvalidCode(INVALID_CODE);
		confirm.setNoPending(NO_PENDING);
		VerificationMessages.Commands commands = new VerificationMessages.Commands();
		commands.setConfirm(confirm);
		VerificationMessages messages = new VerificationMessages();
		messages.setCommands(commands);
		return messages;
	}

	private AuthContext context() {
		return AuthContext.builder()
				.identity(new ConnectionIdentity("PlayerOne", "127.0.0.1"))
				.provider(ProviderContext.of("premium", "subject", "PlayerOne"))
				.build();
	}
}

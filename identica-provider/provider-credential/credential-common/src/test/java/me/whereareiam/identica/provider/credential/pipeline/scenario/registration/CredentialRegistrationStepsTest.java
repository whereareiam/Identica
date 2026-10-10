package me.whereareiam.identica.provider.credential.pipeline.scenario.registration;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.provider.credential.config.defaults.CredentialMessagesDefaults;
import me.whereareiam.identica.provider.credential.config.defaults.CredentialSettingsDefaults;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.cryptography.PasswordCandidate;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.pipeline.CredentialRegisterStateItem;
import me.whereareiam.identica.provider.credential.pipeline.CredentialRegistrationAttempt;
import me.whereareiam.identica.provider.credential.pipeline.step.type.registration.CredentialRegistrationConfirmStep;
import me.whereareiam.identica.provider.credential.pipeline.step.type.registration.CredentialRegistrationStep;
import me.whereareiam.identica.provider.credential.type.PasswordChangeReason;
import me.whereareiam.identica.provider.credential.util.PasswordRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Credential Registration Steps")
class CredentialRegistrationStepsTest {
	private static final String PASSWORD = "Secret-123";
	private static final long TTL = Duration.ofMinutes(5).toMillis();

	@Mock
	private CredentialAccountService accountService;
	@Mock
	private CryptographyService cryptographyService;
	@Mock
	private PasswordRules passwordRules;

	@DisplayName("The confirmation finds the password the first step left in the run's state")
	@Test
	void theConfirmationCompletesWithThePasswordTheFirstStepKept() {
		RegistrationContext context = RegistrationContext.builder()
				.identity(new ConnectionIdentity("whereareiam", "127.0.0.1"))
				.build();
		context.setProvider(ProviderContext.of("credential", "subject", "whereareiam", null));
		PipelineState state = PipelineState.initial();
		when(accountService.find("subject")).thenReturn(Optional.empty());
		when(cryptographyService.hash(PASSWORD)).thenReturn(new PasswordCandidate("hash", "bcrypt"));
		when(cryptographyService.verify(PASSWORD, "hash", "bcrypt")).thenReturn(true);
		when(accountService.register("subject", "hash", "bcrypt", PasswordChangeReason.REGISTER))
				.thenReturn(Optional.of(CredentialAccount.builder()
						.providerId("credential")
						.providerSubject("subject")
						.passwordHash("hash")
						.hashingMethod("bcrypt")
						.build()));

		state.putItem(new CredentialRegistrationAttempt(PASSWORD, false), TTL);
		StepResult first = new CredentialRegistrationStep(this::messages, this::credentialSettings, this::settings, accountService,
				cryptographyService, passwordRules).execute(context, state).join();

		assertEquals(StepResult.StepStatus.CONTINUE, first.getStatus());
		assertTrue(state.item(CredentialRegistrationAttempt.class).isEmpty(), "the step takes the attempt out of the run's state");
		assertTrue(state.item(CredentialRegisterStateItem.class).isPresent(), "the step keeps the pending password in the run's state");

		state.putItem(new CredentialRegistrationAttempt(PASSWORD, true), TTL);
		StepResult confirmation = new CredentialRegistrationConfirmStep(this::messages, this::settings, accountService,
				cryptographyService).execute(context, state).join();

		assertEquals(StepResult.StepStatus.COMPLETE, confirmation.getStatus());
		assertTrue(state.item(CredentialRegisterStateItem.class).isEmpty());
		verify(accountService).register("subject", "hash", "bcrypt", PasswordChangeReason.REGISTER);
	}

	private CredentialMessages messages() {
		return new CredentialMessagesDefaults().supply(new CredentialMessages());
	}

	private CredentialSettings credentialSettings() {
		return new CredentialSettingsDefaults().supply(new CredentialSettings());
	}

	private Engine settings() {
		Engine.Registration registration = new Engine.Registration();
		registration.setPipelineTtl(Duration.ofMinutes(5));
		Engine.Scenarios scenarios = new Engine.Scenarios();
		scenarios.setRegistration(registration);

		Engine settings = new Engine();
		settings.setScenarios(scenarios);
		return settings;
	}
}

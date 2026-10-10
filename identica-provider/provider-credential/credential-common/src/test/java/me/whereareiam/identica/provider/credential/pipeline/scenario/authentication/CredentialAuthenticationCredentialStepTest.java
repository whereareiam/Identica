package me.whereareiam.identica.provider.credential.pipeline.scenario.authentication;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.defaults.CredentialMessagesDefaults;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.pipeline.CredentialAuthenticationAttempt;
import me.whereareiam.identica.provider.credential.pipeline.step.type.authentication.CredentialAuthenticationPasswordStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Credential Authentication Credential Step")
class CredentialAuthenticationCredentialStepTest {
	@Mock
	private CredentialAccountService accountService;
	@Mock
	private CryptographyService cryptographyService;
	@Mock
	private EventManager eventManager;

	private CredentialAuthenticationPasswordStep step;

	@BeforeEach
	void setUp() {
		step = new CredentialAuthenticationPasswordStep(
				this::messages,
				this::settings,
				accountService,
				cryptographyService,
				eventManager
		);
	}

	@DisplayName("Successful password authentication continues to later provider steps")
	@Test
	void successfulPasswordAuthenticationContinues() {
		AuthContext context = AuthContext.builder()
				.identity(new ConnectionIdentity("whereareiam", "127.0.0.1"))
				.provider(me.whereareiam.identica.model.provider.ProviderContext.of("credential", "subject", "whereareiam", null))
				.build();
		PipelineState state = PipelineState.initial();
		state.putItem(new CredentialAuthenticationAttempt("credential"), settings().getScenarios().getAuthentication().pipelineTtlMillis());
		CredentialAccount account = CredentialAccount.builder()
				.providerId("credential")
				.providerSubject("subject")
				.passwordHash("hash")
				.hashingMethod("bcrypt")
				.build();

		when(accountService.find("subject")).thenReturn(Optional.of(account));
		when(cryptographyService.verify(account, "credential")).thenReturn(true);

		StepResult result = step.execute(context, state).join();

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
		assertTrue(state.item(CredentialAuthenticationAttempt.class).isEmpty(), "the step takes the attempt out of the run's state");
	}

	private CredentialMessages messages() {
		return new CredentialMessagesDefaults().supply(new CredentialMessages());
	}

	private Engine settings() {
		Engine.Scenarios connection = new Engine.Scenarios();
		Engine.Authentication authentication = new Engine.Authentication();
		authentication.setPipelineTtl(Duration.ofMinutes(5));
		connection.setAuthentication(authentication);

		Engine settings = new Engine();
		settings.setScenarios(connection);
		return settings;
	}
}

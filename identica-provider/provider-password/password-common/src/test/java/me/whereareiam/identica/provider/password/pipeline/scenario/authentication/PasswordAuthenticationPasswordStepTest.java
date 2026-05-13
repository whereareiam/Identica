package me.whereareiam.identica.provider.password.pipeline.scenario.authentication;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.password.account.PasswordAccountService;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.config.defaults.PasswordMessagesDefaults;
import me.whereareiam.identica.provider.password.cryptography.CryptographyService;
import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.pipeline.PasswordAuthenticationAttempt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Password Authentication Password Step")
class PasswordAuthenticationPasswordStepTest {
	@Mock
	private PasswordAccountService accountService;
	@Mock
	private CryptographyService cryptographyService;
	@Mock
	private PipelineStateStore pipelineStateStore;
	@Mock
	private EventManager eventManager;

	private PasswordAuthenticationPasswordStep step;

	@BeforeEach
	void setUp() {
		step = new PasswordAuthenticationPasswordStep(
				this::messages,
				this::settings,
				accountService,
				cryptographyService,
				pipelineStateStore,
				eventManager
		);
	}

	@DisplayName("Successful password authentication continues to later provider steps")
	@Test
	void successfulPasswordAuthenticationContinues() {
		AuthContext context = AuthContext.builder()
				.identity(new ConnectionIdentity("whereareiam", "127.0.0.1"))
				.provider(me.whereareiam.identica.model.provider.ProviderContext.of("password", "subject", "whereareiam", null))
				.build();
		PipelineState state = PipelineState.initial();
		state.putItem(new PasswordAuthenticationAttempt("password"), settings().getConnection().getAuthentication().pipelineTtlMillis());
		PasswordAccount account = PasswordAccount.builder()
				.providerId("password")
				.providerSubject("subject")
				.passwordHash("hash")
				.hashingMethod("bcrypt")
				.build();

		when(accountService.find("subject")).thenReturn(Optional.of(account));
		when(pipelineStateStore.find(any(me.whereareiam.identica.model.pipeline.state.PipelineStateReference.class)))
				.thenReturn(Optional.of(state));
		when(cryptographyService.verify(account, "password")).thenReturn(true);

		StepResult result = step.execute(context).join();

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
	}

	private PasswordMessages messages() {
		return new PasswordMessagesDefaults().supply(new PasswordMessages());
	}

	private Settings settings() {
		Settings.Connection connection = new Settings.Connection();
		Settings.AuthenticationScenario authentication = new Settings.AuthenticationScenario();
		authentication.setPipelineTtl(Duration.ofMinutes(5));
		connection.setAuthentication(authentication);

		Settings settings = new Settings();
		settings.setConnection(connection);
		return settings;
	}
}

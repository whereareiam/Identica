package me.whereareiam.identica.feature.sentinel.pipeline;

import me.whereareiam.identica.feature.sentinel.SentinelQuota;
import me.whereareiam.identica.feature.sentinel.SentinelService;
import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelDecision;
import me.whereareiam.identica.feature.sentinel.type.SentinelScope;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Registration Sentinel Step")
class RegistrationSentinelStepTest {
	private final SentinelService sentinels = mock(SentinelService.class);
	private final RegistrationSentinelStep step = new RegistrationSentinelStep(sentinels);
	private final ScenarioContext context = context();

	@DisplayName("Refuses the registration with the message of the sentinel that limits it")
	@Test
	void refusesALimitedRegistration() {
		when(sentinels.evaluate(eq(SentinelScope.REGISTRATION), any()))
				.thenReturn(Optional.of(SentinelDecision.exceeded(mock(SentinelQuota.class), 0L, "Too many accounts")));

		StepResult result = step.execute(context).join();

		assertEquals(StepResult.StepStatus.DENIED, result.getStatus());
		assertEquals("Too many accounts", result.getMessage());
	}

	@DisplayName("Lets the registration go on and asks with the provider and address of the connection")
	@Test
	void letsAnUnlimitedRegistrationGoOn() {
		when(sentinels.evaluate(eq(SentinelScope.REGISTRATION), any())).thenReturn(Optional.empty());

		StepResult result = step.execute(context).join();

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
		ArgumentCaptor<SentinelContext> asked = ArgumentCaptor.forClass(SentinelContext.class);
		verify(sentinels).evaluate(eq(SentinelScope.REGISTRATION), asked.capture());
		assertEquals("credential", asked.getValue().getProviderId());
		assertEquals("203.0.113.10", asked.getValue().getIp());
		assertEquals("Alice", asked.getValue().getUsername());
	}

	private static ScenarioContext context() {
		ScenarioContext context = mock(ScenarioContext.class);
		ProviderContext provider = mock(ProviderContext.class);
		when(provider.getProviderId()).thenReturn("credential");
		when(context.getProvider()).thenReturn(provider);
		when(context.getIp()).thenReturn("203.0.113.10");
		when(context.getUsername()).thenReturn("Alice");
		return context;
	}
}

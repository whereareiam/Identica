package me.whereareiam.identica.testing.routing;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.routing.RoutingRetryMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.CLOSED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Players finish registering on a network whose completion server refuses them. The completion target's attempt
 * policy decides how often, and how far apart, the proxy tries to send them there.
 */
@Tag("join")
class RoutingRetryTest {
	private static final String PASSWORD = "Secret-123";
	/** The reason a whitelisted Paper server gives a player who is not on its whitelist. */
	private static final String NOT_WHITELISTED = "You are not whitelisted on this server!";
	private static final int MAX_ATTEMPTS = 3;
	private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

	@Test
	@Identica(providers = Provider.CREDENTIAL, complete = CLOSED, configure = LimitedRetries.class)
	void triesAsOftenAsTheLimitAllowsWithTheConfiguredDelay(ScenarioContext anvil) {
		Journey alice = registered(anvil);

		List<LocalTime> attempts = alice.connectionsTo(CLOSED);
		assertEquals(MAX_ATTEMPTS, attempts.size(), "attempts: " + attempts);
		for (int index = 1; index < attempts.size(); index++) {
			Duration gap = Duration.between(attempts.get(index - 1), attempts.get(index));
			// Console times have whole seconds, so a gap may read one second short.
			assertTrue(gap.compareTo(RETRY_DELAY.minusSeconds(1)) >= 0, "attempts: " + attempts);
		}
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL, complete = CLOSED, configure = NoRetry.class)
	void triesOnceWithoutRetries(ScenarioContext anvil) {
		Journey alice = registered(anvil);

		assertEquals(1, alice.connectionsTo(CLOSED).size());
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL, complete = CLOSED, configure = RetryOnce.class)
	void triesTwiceWhenRetryingOnce(ScenarioContext anvil) {
		Journey alice = registered(anvil);

		assertEquals(2, alice.connectionsTo(CLOSED).size());
	}

	/**
	 * Registers Alice and waits until the proxy gave up on the completion server, which it says by passing the
	 * server's refusal on to her.
	 */
	private static Journey registered(ScenarioContext anvil) {
		return Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.seesText(NOT_WHITELISTED)
				.remainsOn(AUTH);
	}

	private static void completion(ProxyConfiguration proxy, RoutingRetryMode mode) {
		RoutingAttemptPolicy attempts = RoutingAttemptPolicy.defaultCompletion();
		attempts.setMode(mode);
		attempts.setMaxAttempts(MAX_ATTEMPTS);
		attempts.setRetryDelay(RETRY_DELAY);
		proxy.routing().getDefaults().getComplete().setAttempts(attempts);
	}

	static final class LimitedRetries implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			completion(proxy, RoutingRetryMode.RETRY_LIMITED);
		}
	}

	static final class NoRetry implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			completion(proxy, RoutingRetryMode.NONE);
		}
	}

	static final class RetryOnce implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			completion(proxy, RoutingRetryMode.RETRY_ONCE);
		}
	}
}

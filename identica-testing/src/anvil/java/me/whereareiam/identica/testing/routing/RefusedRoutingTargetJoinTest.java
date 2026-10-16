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
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.CLOSED;

/**
 * Players finish authenticating on a network whose completion server refuses them, as a whitelisted server refuses a
 * player who is not on it. Once Identica has no attempt left, the proxy handles the refusal as it handles any other:
 * the player stays where they are and reads the server's reason.
 */
@Tag("join")
class RefusedRoutingTargetJoinTest {
	private static final String PASSWORD = "Secret-123";
	/** The reason a whitelisted Paper server gives a player who is not on its whitelist. */
	private static final String NOT_WHITELISTED = "You are not whitelisted on this server!";

	@Test
	@Identica(providers = Provider.CREDENTIAL, complete = CLOSED, configure = SingleAttempt.class)
	void seesWhyTheCompletionServerRefusedThem(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.seesText(NOT_WHITELISTED)
				.remainsOn(AUTH);
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL, complete = CLOSED, configure = LimitedAttempts.class)
	void seesWhyOnceTheRetriesAreSpent(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.seesText(NOT_WHITELISTED)
				.remainsOn(AUTH);
	}

	/**
	 * Completion routing as Identica ships it: one attempt, no retry.
	 */
	static final class SingleAttempt implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.routing().getDefaults().getComplete().setAttempts(RoutingAttemptPolicy.defaultCompletion());
		}
	}

	static final class LimitedAttempts implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			RoutingAttemptPolicy attempts = RoutingAttemptPolicy.defaultCompletion();
			attempts.setMode(RoutingRetryMode.RETRY_LIMITED);
			attempts.setMaxAttempts(3);
			attempts.setRetryDelay(Duration.ofSeconds(1));
			proxy.routing().getDefaults().getComplete().setAttempts(attempts);
		}
	}
}

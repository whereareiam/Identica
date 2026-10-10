package me.whereareiam.identica.testing.cluster;

import com.redis.testcontainers.RedisContainer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.IdenticaCluster;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY_A;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A password a player types never reaches the replicated state other proxies read, even when the step waiting
 * for the player does not take it.
 */
class ReplicatedPasswordTest {
	private static final String PASSWORD = "Secret-123";
	private static final String TYPED = "Typed-Secret-4711";

	@Test
	@IdenticaCluster
	void aLoginDuringRegistrationIsNotReplicated(ScenarioContext anvil, RedisContainer redis) throws Exception {
		Journey.offline(anvil, "Alice", PROXY_A).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.login(TYPED)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());

		assertNotReplicated(redis, TYPED);
	}

	@Test
	@IdenticaCluster
	void aRegistrationPasswordDuringAuthenticationIsNotReplicated(ScenarioContext anvil, RedisContainer redis) throws Exception {
		Accounts.credential(anvil, "Alice", PASSWORD, PROXY_A).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.command("pass " + TYPED)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());

		assertNotReplicated(redis, TYPED);
	}

	/**
	 * Reads every string value in Redis. The pending journey must be there, so the check cannot pass on an empty
	 * Redis, and the password must not.
	 */
	private static void assertNotReplicated(RedisContainer redis, String password) throws Exception {
		Container.ExecResult dump = redis.execInContainer("sh", "-c",
				"redis-cli --scan | while IFS= read -r key; do printf '%s => ' \"$key\"; redis-cli --raw GET \"$key\"; done");
		assertEquals(0, dump.getExitCode(), dump.getStderr());

		String values = dump.getStdout();
		assertTrue(values.contains("JourneyStateItem"), "the pending journey is replicated:\n" + values);
		assertFalse(values.contains(password), "a typed password is replicated:\n" + values);
	}
}

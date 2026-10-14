package me.whereareiam.identica.feature.sentinel.type;

import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelPolicy;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelMessages;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Contested Connection Sentinel Definition")
class ContestedConnectionSentinelDefinitionTest {
	private final SentinelSettings settings = settings();
	private final ContestedConnectionSentinelDefinition definition = new ContestedConnectionSentinelDefinition(
			() -> settings,
			ContestedConnectionSentinelDefinitionTest::messages
	);

	@DisplayName("Counts the attempts of an address whatever username they use")
	@Test
	void countsPerAddress() {
		SentinelKey alice = definition.key(SentinelContext.builder().username("Alice").ip("203.0.113.10").build());
		SentinelKey bob = definition.key(SentinelContext.builder().username("Bob").ip("203.0.113.10").build());

		assertEquals(alice.getKey(), bob.getKey());
		assertTrue(definition.key(SentinelContext.builder().username("Alice").build()).isBlank());
	}

	@DisplayName("Only checks the lockout when a connection arrives, since refusals record the attempts")
	@Test
	void checksOnArrival() {
		assertEquals(List.of(SentinelScope.PROCESS), List.of(definition.scopes()));
		assertEquals(SentinelMode.CHECK, definition.modeFor(SentinelScope.PROCESS));
	}

	@DisplayName("Words the lockout with the configured message and the seconds left")
	@Test
	void wordsTheLockout() {
		SentinelPolicy policy = definition.policy(SentinelContext.builder().ip("203.0.113.10").build());

		assertTrue(policy.isActive());
		assertEquals("Wait 12s.", policy.getLockout().getMessageSupplier().apply(null, 12L));
	}

	@DisplayName("Takes no part while the sentinel feature is turned off")
	@Test
	void inactiveWhileTurnedOff() {
		settings.setEnabled(false);

		assertFalse(definition.policy(SentinelContext.builder().ip("203.0.113.10").build()).isActive());
	}

	private static SentinelSettings settings() {
		SentinelPolicy policy = new SentinelPolicy();
		policy.setEnabled(true);
		policy.setMaxAttempts(5);
		SentinelPolicy.Lockout lockout = new SentinelPolicy.Lockout();
		lockout.setEnabled(true);
		lockout.setDuration(Duration.ofMinutes(1));
		policy.setLockout(lockout);

		SentinelSettings settings = new SentinelSettings();
		settings.getSentinels().setContestedConnection(policy);
		return settings;
	}

	private static SentinelMessages messages() {
		SentinelMessages.ContestedConnection contestedConnection = new SentinelMessages.ContestedConnection();
		contestedConnection.setDenied(List.of("Wait {seconds}s."));
		SentinelMessages messages = new SentinelMessages();
		messages.setContestedConnection(contestedConnection);
		return messages;
	}
}

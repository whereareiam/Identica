package me.whereareiam.identica.provider.premium.migration;

import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.provider.ProviderAttemptStore;
import me.whereareiam.identica.provider.migration.MigrationPrecheckContext;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.policy.PremiumHandshakeInstructions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Premium Migration Precheck")
class PremiumMigrationPrecheckTest {
	@Mock
	private HandshakeStore handshakeStore;
	@Mock
	private ProviderAttemptStore attemptStore;

	private PremiumMigrationPrecheck precheck;

	@BeforeEach
	void setUp() {
		precheck = new PremiumMigrationPrecheck(
				new PremiumHandshakeInstructions(handshakeStore, this::settings),
				attemptStore,
				this::messages
		);
	}

	@DisplayName("Marks a premium verification attempt when online migration is forced")
	@Test
	void forceOnlineMigrationMarksVerifyAttempt() {
		precheck.precheck(MigrationPrecheckContext.builder()
				.username("whereareiam")
				.ip("127.0.0.1")
				.providerId("premium")
				.build());

		verify(handshakeStore).putInstruction(any());
		verify(attemptStore).markAttempt("premium", "verify", "whereareiam", "127.0.0.1");
	}

	private Engine settings() {
		Engine.Behavior behavior = new Engine.Behavior();
		behavior.setHandshakeInstructionTtl(Duration.ofMinutes(10));

		Engine settings = new Engine();
		settings.setBehavior(behavior);
		return settings;
	}

	private PremiumMessages messages() {
		PremiumMessages.Commands.Premium premium = new PremiumMessages.Commands.Premium();
		premium.setConfirmed(List.of("confirmed"));

		PremiumMessages.Commands commands = new PremiumMessages.Commands();
		commands.setPremium(premium);

		PremiumMessages messages = new PremiumMessages();
		messages.setCommands(commands);
		return messages;
	}
}

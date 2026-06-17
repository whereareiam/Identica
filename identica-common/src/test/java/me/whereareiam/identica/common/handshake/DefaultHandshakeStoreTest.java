package me.whereareiam.identica.common.handshake;

import me.whereareiam.identica.Registry;
import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.common.replication.ReplicationTestFixtures;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.handshake.HandshakeInstruction;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("Default Handshake Store")
class DefaultHandshakeStoreTest {
	@DisplayName("Consumes handshake instructions only when username and IP both match")
	@Test
	void consumeUsesMatchingUsernameAndIp() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		adapter.available = false;
			DefaultHandshakeStore store = new DefaultHandshakeStore(
					new DefaultReplicationSystem(adapter),
					this::replication,
					mock(EventManager.class),
					noopRegistry()
			);
		HandshakeInstruction instruction = HandshakeInstruction.create(
				new ConnectionIdentity("PlayerOne", "1.1.1.1"),
				Duration.ofMinutes(1).toMillis()
		);

		store.putInstruction(instruction);

		Optional<HandshakeInstruction> resolved = store.consumeInstruction("PlayerOne", "1.1.1.1");
		assertTrue(resolved.isPresent());
		assertEquals("PlayerOne", resolved.get().getIdentity().getUsername());
	}

	@DisplayName("Does not consume a handshake instruction for the wrong IP address")
	@Test
	void consumeRequiresMatchingIp() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		adapter.available = false;
			DefaultHandshakeStore store = new DefaultHandshakeStore(
					new DefaultReplicationSystem(adapter),
					this::replication,
					mock(EventManager.class),
					noopRegistry()
			);
		HandshakeInstruction instruction = HandshakeInstruction.create(
				new ConnectionIdentity("PlayerOne", "1.1.1.1"),
				Duration.ofMinutes(1).toMillis()
		);

		store.putInstruction(instruction);

		Optional<HandshakeInstruction> resolved = store.consumeInstruction("PlayerOne", "2.2.2.2");
		assertFalse(resolved.isPresent(), "handshake instructions should not bleed across IPs for the same username");
	}

	private Replication replication() {
		Replication replication = new Replication();
		Replication.Cache cache = new Replication.Cache();
		cache.setInstructions("instructions");
		replication.setCache(cache);
		return replication;
	}

	private Registry<AccountLifecycleParticipant> noopRegistry() {
		return new Registry<>() {
			@Override
			public void register(AccountLifecycleParticipant value) {
			}

			@Override
			public void unregister(AccountLifecycleParticipant value) {
			}

			@Override
			public Set<AccountLifecycleParticipant> values() {
				return Set.of();
			}
		};
	}
}

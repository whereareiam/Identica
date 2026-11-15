package me.whereareiam.identica.common.identity;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.session.SessionOpenedEvent;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.util.EventUtil;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@DisplayName("Default Identity Service")
class DefaultIdentityServiceTest {
	@Mock
	private EventManager eventManager;

	private DefaultIdentityService service;

	@BeforeEach
	void setUp() {
		EventUtil.initialize(eventManager);
		service = new DefaultIdentityService(eventManager);
	}

	@Test
	@DisplayName("Session opened updates the existing live attachment with the resolved account UUID")
	void sessionOpenedUpdatesLiveAttachmentAccountUniqueId() {
		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		TestIdentity identity = new TestIdentity(connectionUniqueId, null, "PlayerOne");

		service.attach(connectionUniqueId, null, identity);
		service.onSessionOpened(new SessionOpenedEvent(
				connectionUniqueId,
				PipelineType.REGISTRATION,
				Session.builder()
						.uniqueId(accountUniqueId)
						.providerId("premium")
						.originalUsername(identity.getUsername())
						.effectiveUsername(identity.getUsername())
						.build()
		));

		assertEquals(accountUniqueId, identity.getAccountUniqueId());
		assertEquals(accountUniqueId, service.findByConnectionUniqueId(connectionUniqueId).orElseThrow().getAccountUniqueId());
		assertTrue(service.findByAccountUniqueId(accountUniqueId).isPresent());
	}

	@Test
	@DisplayName("Session opened does nothing when the live connection is no longer attached")
	void sessionOpenedIgnoresMissingAttachment() {
		service.onSessionOpened(new SessionOpenedEvent(
				UUID.randomUUID(),
				PipelineType.REGISTRATION,
				Session.builder()
						.uniqueId(UUID.randomUUID())
						.providerId("premium")
						.originalUsername("PlayerOne")
						.effectiveUsername("PlayerOne")
						.build()
		));

		assertTrue(service.list().isEmpty());
	}

	@Test
	@DisplayName("An account attached to a second connection is found there, also after its first connection leaves")
	void accountFollowsItsNewestConnection() {
		UUID accountUniqueId = UUID.randomUUID();
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();

		service.attach(first, accountUniqueId, new TestIdentity(first, accountUniqueId, "PlayerOne"));
		service.attach(second, accountUniqueId, new TestIdentity(second, accountUniqueId, "PlayerOne"));
		service.detach(first);

		assertEquals(second, service.findByAccountUniqueId(accountUniqueId).orElseThrow().getConnectionUniqueId());

		service.detach(second);
		assertTrue(service.findByAccountUniqueId(accountUniqueId).isEmpty());
	}

	@Test
	@DisplayName("A connection attached again under another account is no longer found under the old one")
	void reattachedConnectionLeavesOldAccount() {
		UUID connectionUniqueId = UUID.randomUUID();
		UUID oldAccount = UUID.randomUUID();
		UUID newAccount = UUID.randomUUID();
		TestIdentity identity = new TestIdentity(connectionUniqueId, oldAccount, "PlayerOne");

		service.attach(connectionUniqueId, oldAccount, identity);
		service.attach(connectionUniqueId, newAccount, identity);

		assertTrue(service.findByAccountUniqueId(oldAccount).isEmpty());
		assertTrue(service.findByAccountUniqueId(newAccount).isPresent());
	}

	private static final class TestIdentity extends Identity {
		private TestIdentity(
				@NotNull UUID connectionUniqueId,
				UUID accountUniqueId,
				@NotNull String username
		) {
			super(connectionUniqueId, accountUniqueId, username, null);
		}

		@Override
		public void sendMessage(@NotNull Component message) {
		}

		@Override
		public void sendTitle(@NotNull Title title) {
		}

		@Override
		public boolean hasPermission(@NotNull String permission) {
			return true;
		}

		@Override
		public @NotNull Locale getLocale() {
			return Locale.ENGLISH;
		}

		@Override
		public @NotNull Audience getAudience() {
			return Audience.empty();
		}

		@Override
		public void disconnect(@NotNull Component reason) {
		}
	}
}

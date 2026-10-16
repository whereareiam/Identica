package me.whereareiam.identica.adapter.command.executor;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.migration.operation.MigrationResult;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.service.MigrationService;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Migration Command")
class MigrationCommandTest {
	@BeforeAll
	static void initializeSerializer() {
		Serializer.initialize(() -> TEST_SERIALIZER);
	}

	private static final SerializerEngine TEST_SERIALIZER = new SerializerEngine() {
		@Override
		public @NotNull String serialize(@NotNull Component component) {
			return component.toString();
		}

		@Override
		public @NotNull Component serialize(@NotNull SerializerContent content) {
			return Component.text(content.getMessage());
		}

		public @NotNull String renderTemplate(@NotNull SerializerContent content) {
			return content.getMessage();
		}

		@Override
		public @NotNull SerializerOptions.PlaceholderFormat getPlaceholderFormat() {
			return SerializerOptions.PlaceholderFormat.CURLY_BRACES;
		}
	};

	@Mock
	private AccountPersistenceService accountPersistenceService;
	@Mock
	private ProviderLinkPersistenceService providerLinkPersistenceService;
	@Mock
	private ProviderProfilePersistenceService providerProfilePersistenceService;
	@Mock
	private ProviderManager providerManager;
	@Mock
	private MigrationService migrationService;
	@Mock
	private IdentityService identityService;

	@Test
	@DisplayName("Shows provider unsupported when the service rejects the target")
	void startShowsProviderUnsupported() {
		TestActor sender = new TestActor();
		Account target = account();
		when(accountPersistenceService.findByUniqueId(target.getUniqueId())).thenReturn(Optional.of(target));
		when(migrationService.start(any())).thenReturn(MigrationResult.builder()
				.status(MigrationResultStatus.TARGET_UNSUPPORTED)
				.build());

		MigrationCommand command = new MigrationCommand(
				this::messages,
				accountPersistenceService,
				providerLinkPersistenceService,
				providerProfilePersistenceService,
				providerManager,
				migrationService,
				identityService
		);

		command.start(sender, target.getUniqueId().toString(), "premium");

		assertEquals("unsupported", PlainTextComponentSerializer.plainText().serialize(sender.lastMessage()));
	}

	@Test
	@DisplayName("Shows provider unavailable when the service rejects an inactive target")
	void startShowsProviderUnavailable() {
		TestActor sender = new TestActor();
		Account target = account();
		when(accountPersistenceService.findByUniqueId(target.getUniqueId())).thenReturn(Optional.of(target));
		when(migrationService.start(any())).thenReturn(MigrationResult.builder()
				.status(MigrationResultStatus.PROVIDER_UNAVAILABLE)
				.build());

		MigrationCommand command = new MigrationCommand(
				this::messages,
				accountPersistenceService,
				providerLinkPersistenceService,
				providerProfilePersistenceService,
				providerManager,
				migrationService,
				identityService
		);

		command.start(sender, target.getUniqueId().toString(), "premium");

		assertEquals("unavailable", PlainTextComponentSerializer.plainText().serialize(sender.lastMessage()));
	}

	@Test
	@DisplayName("Makes another linked provider primary")
	void primarySetsTheLinkExclusively() {
		TestActor sender = new TestActor();
		Account target = linkedAccount();

		command().primary(sender, target.getUniqueId().toString(), "credential");

		verify(providerLinkPersistenceService).setPrimaryExclusive(target.getUniqueId(), "credential");
		assertEquals("set", PlainTextComponentSerializer.plainText().serialize(sender.lastMessage()));
	}

	@Test
	@DisplayName("Leaves an already primary provider as it is")
	void primaryLeavesTheCurrentPrimary() {
		TestActor sender = new TestActor();
		Account target = linkedAccount();

		command().primary(sender, target.getUniqueId().toString(), "PREMIUM");

		verify(providerLinkPersistenceService, never()).setPrimaryExclusive(any(), any());
		assertEquals("already-primary", PlainTextComponentSerializer.plainText().serialize(sender.lastMessage()));
	}

	@Test
	@DisplayName("Drops a linked provider that is not primary together with its profile")
	void dropRemovesTheLinkAndProfile() {
		TestActor sender = new TestActor();
		Account target = linkedAccount();

		command().drop(sender, target.getUniqueId().toString(), "credential");

		verify(providerLinkPersistenceService).delete(target.getUniqueId(), "credential");
		verify(providerProfilePersistenceService).delete("credential", "alice");
		assertEquals("dropped", PlainTextComponentSerializer.plainText().serialize(sender.lastMessage()));
	}

	@Test
	@DisplayName("Refuses to drop the primary provider")
	void dropRefusesThePrimary() {
		TestActor sender = new TestActor();
		Account target = linkedAccount();

		command().drop(sender, target.getUniqueId().toString(), "premium");

		verify(providerLinkPersistenceService, never()).delete(any(), any());
		verify(providerProfilePersistenceService, never()).delete(any(), any());
		assertEquals("primary-denied", PlainTextComponentSerializer.plainText().serialize(sender.lastMessage()));
	}

	@Test
	@DisplayName("Reports a provider the account is not linked to")
	void dropReportsAMissingLink() {
		TestActor sender = new TestActor();
		Account target = linkedAccount();

		command().drop(sender, target.getUniqueId().toString(), "example");

		verify(providerLinkPersistenceService, never()).delete(any(), any());
		assertEquals("not-linked", PlainTextComponentSerializer.plainText().serialize(sender.lastMessage()));
	}

	@Test
	@DisplayName("Reports an account without provider links")
	void dropReportsNoLinks() {
		TestActor sender = new TestActor();
		Account target = account();
		when(accountPersistenceService.findByUniqueId(target.getUniqueId())).thenReturn(Optional.of(target));
		when(providerLinkPersistenceService.findByUniqueId(target.getUniqueId())).thenReturn(List.of());

		command().drop(sender, target.getUniqueId().toString(), "credential");

		assertEquals("no-links", PlainTextComponentSerializer.plainText().serialize(sender.lastMessage()));
	}

	/**
	 * An account linked to Premium, its primary provider, and to Credential.
	 */
	private Account linkedAccount() {
		Account target = account();
		when(accountPersistenceService.findByUniqueId(target.getUniqueId())).thenReturn(Optional.of(target));
		when(providerLinkPersistenceService.findByUniqueId(target.getUniqueId())).thenReturn(List.of(
				link(target, "premium", true),
				link(target, "credential", false)
		));
		return target;
	}

	private static AccountProviderLink link(Account account, String providerId, boolean primary) {
		return AccountProviderLink.builder()
				.uniqueId(account.getUniqueId())
				.providerId(providerId)
				.providerSubject("alice")
				.primaryLink(primary)
				.build();
	}

	private MigrationCommand command() {
		return new MigrationCommand(
				this::messages,
				accountPersistenceService,
				providerLinkPersistenceService,
				providerProfilePersistenceService,
				providerManager,
				migrationService,
				identityService
		);
	}

	private Account account() {
		return Account.builder()
				.uniqueId(UUID.randomUUID())
				.username("PlayerOne")
				.build();
	}

	private Messages messages() {
		Messages messages = new Messages();
		Messages.Commands commands = new Messages.Commands();
		Messages.Commands.Migration migration = new Messages.Commands.Migration();
		Messages.Commands.Migration.Start start = new Messages.Commands.Migration.Start();
		start.setProviderUnsupported("unsupported");
		start.setProviderUnavailable("unavailable");
		migration.setStart(start);
		Messages.Commands.Migration.Links links = new Messages.Commands.Migration.Links();
		links.setNoLinks("no-links");
		links.setNotLinked("not-linked");
		migration.setLinks(links);
		Messages.Commands.Migration.Primary primary = new Messages.Commands.Migration.Primary();
		primary.setSet("set");
		primary.setAlreadyPrimary("already-primary");
		migration.setPrimary(primary);
		Messages.Commands.Migration.Drop drop = new Messages.Commands.Migration.Drop();
		drop.setDropped("dropped");
		drop.setPrimaryDenied("primary-denied");
		drop.setLastLinkDenied("last-link-denied");
		migration.setDrop(drop);
		commands.setMigration(migration);
		messages.setCommands(commands);
		return messages;
	}

	private static final class TestActor implements Actor {
		private final AtomicReference<Component> lastMessage = new AtomicReference<>();

		@Override
		public @NotNull UUID getUniqueId() {
			return UUID.randomUUID();
		}

		@Override
		public @NotNull String getUsername() {
			return "Admin";
		}

		@Override
		public void sendMessage(@NotNull Component message) {
			lastMessage.set(message);
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

		private Component lastMessage() {
			return lastMessage.get();
		}
	}
}

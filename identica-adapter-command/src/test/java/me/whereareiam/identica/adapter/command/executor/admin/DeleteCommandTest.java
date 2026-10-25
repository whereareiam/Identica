package me.whereareiam.identica.adapter.command.executor.admin;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.AccountOperationRequest;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Delete Command")
class DeleteCommandTest {
	private final AccountService accounts = mock(AccountService.class);
	private final Commands commands = commands(Duration.ofMinutes(1));
	private final DeleteCommand command = new DeleteCommand(() -> commands, DeleteCommandTest::messages, accounts);
	private final Account alice = Account.builder().uniqueId(UUID.randomUUID()).username("Alice").build();
	private final RecordingActor admin = new RecordingActor();

	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class)))
				.thenAnswer(call -> Component.text(call.getArgument(0, SerializerContent.class).getMessage()));
		when(engine.getPlaceholderFormat()).thenReturn(SerializerOptions.PlaceholderFormat.CURLY_BRACES);
		Serializer.initialize(() -> engine);
	}

	@BeforeEach
	void registerAlice() {
		when(accounts.find("Alice")).thenReturn(List.of(alice));
		when(accounts.find(alice.getUniqueId())).thenReturn(Optional.of(alice));
	}

	@DisplayName("Asks for confirmation and deletes nothing yet")
	@Test
	void requestAsksForConfirmation() {
		command.delete(admin, "Alice");

		assertEquals(List.of("confirm"), admin.received);
		verify(accounts, never()).delete(any());
	}

	@DisplayName("Confirming deletes the account and disconnects its player with the configured message")
	@Test
	void confirmDeletesAndDisconnects() {
		command.delete(admin, "Alice");
		command.confirmDelete(admin);

		ArgumentCaptor<AccountOperationRequest> request = ArgumentCaptor.forClass(AccountOperationRequest.class);
		verify(accounts).delete(request.capture());
		assertEquals(alice, request.getValue().getAccount());
		assertTrue(request.getValue().isDisconnect());
		assertEquals("deleted\nby an administrator", request.getValue().getDisconnectMessage().getText());
		assertEquals("admin-delete", request.getValue().getReason());
		assertEquals(List.of("confirm", "success"), admin.received);
	}

	@DisplayName("A target given as a unique id resolves to that account")
	@Test
	void uniqueIdTargetResolvesTheAccount() {
		command.delete(admin, alice.getUniqueId().toString());
		command.confirmDelete(admin);

		ArgumentCaptor<AccountOperationRequest> request = ArgumentCaptor.forClass(AccountOperationRequest.class);
		verify(accounts).delete(request.capture());
		assertEquals(alice, request.getValue().getAccount());
	}

	@DisplayName("Cancelling keeps the account, and a later confirm finds nothing pending")
	@Test
	void cancelKeepsTheAccount() {
		command.delete(admin, "Alice");
		command.cancelDelete(admin);
		command.confirmDelete(admin);

		verify(accounts, never()).delete(any());
		assertEquals(List.of("confirm", "cancelled", "no-pending"), admin.received);
	}

	@DisplayName("Cancelling without a pending request says so")
	@Test
	void cancelWithoutRequest() {
		command.cancelDelete(admin);

		assertEquals(List.of("no-pending"), admin.received);
	}

	@DisplayName("Confirming without a pending request deletes nothing")
	@Test
	void confirmWithoutRequest() {
		command.confirmDelete(admin);

		verify(accounts, never()).delete(any());
		assertEquals(List.of("no-pending"), admin.received);
	}

	@DisplayName("A confirmation after the window has passed deletes nothing and is used up")
	@Test
	void staleConfirmDeletesNothing() throws InterruptedException {
		commands.getBehavior().getClear().setConfirmTtl(Duration.ofMillis(1));
		command.delete(admin, "Alice");
		Thread.sleep(20);

		command.confirmDelete(admin);
		command.confirmDelete(admin);

		verify(accounts, never()).delete(any());
		assertEquals(List.of("confirm", "expired", "no-pending"), admin.received);
	}

	@DisplayName("Another administrator cannot confirm someone else's request")
	@Test
	void confirmationBelongsToItsSender() {
		RecordingActor other = new RecordingActor();
		command.delete(admin, "Alice");
		command.confirmDelete(other);

		verify(accounts, never()).delete(any());
		assertEquals(List.of("no-pending"), other.received);
	}

	@DisplayName("The latest request replaces an earlier one")
	@Test
	void latestRequestWins() {
		Account bob = Account.builder().uniqueId(UUID.randomUUID()).username("Bob").build();
		when(accounts.find("Bob")).thenReturn(List.of(bob));

		command.delete(admin, "Alice");
		command.delete(admin, "Bob");
		command.confirmDelete(admin);

		ArgumentCaptor<AccountOperationRequest> request = ArgumentCaptor.forClass(AccountOperationRequest.class);
		verify(accounts).delete(request.capture());
		assertEquals(bob, request.getValue().getAccount());
	}

	@DisplayName("An unknown target is reported and leaves nothing to confirm")
	@Test
	void unknownTarget() {
		when(accounts.find("Nobody")).thenReturn(List.of());

		command.delete(admin, "Nobody");
		command.confirmDelete(admin);

		verify(accounts, never()).delete(any());
		assertEquals(List.of("not-found", "no-pending"), admin.received);
	}

	@DisplayName("An unknown unique id is reported and leaves nothing to confirm")
	@Test
	void unknownUniqueId() {
		UUID unknown = UUID.randomUUID();
		when(accounts.find(unknown)).thenReturn(Optional.empty());

		command.delete(admin, unknown.toString());

		assertEquals(List.of("not-found"), admin.received);
	}

	@DisplayName("A name shared by several accounts lists them and leaves nothing to confirm")
	@Test
	void ambiguousTargetListsTheMatches() {
		Account other = Account.builder().uniqueId(UUID.randomUUID()).username("Alice").build();
		when(accounts.find("Alice")).thenReturn(List.of(alice, other));

		command.delete(admin, "Alice");
		command.confirmDelete(admin);

		verify(accounts, never()).delete(any());
		assertEquals(2, admin.received.size());
		String listing = admin.received.getFirst();
		assertTrue(listing.contains(alice.getUniqueId().toString()), listing);
		assertTrue(listing.contains(other.getUniqueId().toString()), listing);
		assertEquals("no-pending", admin.received.get(1));
	}

	@DisplayName("A failing deletion is reported instead of the success message")
	@Test
	void failureIsReported() {
		doThrow(new IllegalStateException("storage")).when(accounts).delete(any());

		command.delete(admin, "Alice");
		command.confirmDelete(admin);

		assertEquals(List.of("confirm", "error"), admin.received);
	}

	static Commands commands(Duration confirmTtl) {
		Commands.Behavior.Clear clear = new Commands.Behavior.Clear();
		clear.setConfirmTtl(confirmTtl);
		Commands.Behavior behavior = new Commands.Behavior();
		behavior.setClear(clear);
		Commands commands = new Commands();
		commands.setBehavior(behavior);
		return commands;
	}

	static Messages.Commands.Admin.Clear.Multiple multiple() {
		Messages.Commands.EntryFormat entry = new Messages.Commands.EntryFormat();
		entry.setFormat("{username} {uniqueId}");
		entry.setEmptyFormat("{uniqueId}");
		Messages.Commands.Admin.Clear.Multiple multiple = new Messages.Commands.Admin.Clear.Multiple();
		multiple.setBody(List.of("multiple {target}", "{entries}"));
		multiple.setEntry(entry);
		return multiple;
	}

	private static Messages messages() {
		Messages.Commands.Admin.Delete delete = new Messages.Commands.Admin.Delete();
		delete.setConfirm(List.of("confirm"));
		delete.setNoPending("no-pending");
		delete.setExpired("expired");
		delete.setCancelled("cancelled");
		delete.setNotFound("not-found");
		delete.setMultiple(multiple());
		delete.setSuccess("success");
		delete.setError("error");
		delete.setDisconnect(List.of("deleted", "by an administrator"));
		Messages.Commands.Admin admin = new Messages.Commands.Admin();
		admin.setDelete(delete);
		Messages.Commands commands = new Messages.Commands();
		commands.setAdmin(admin);
		Messages messages = new Messages();
		messages.setCommands(commands);
		return messages;
	}

	/**
	 * An administrator whose received messages a test reads as plain text, one entry per message.
	 */
	static final class RecordingActor implements Actor {
		final List<String> received = new ArrayList<>();
		private final UUID uniqueId = UUID.randomUUID();

		@Override
		public UUID getUniqueId() {
			return uniqueId;
		}

		@Override
		public String getUsername() {
			return "Admin";
		}

		@Override
		public void sendMessage(@NotNull Component message) {
			received.add(((TextComponent) message).content());
		}

		@Override
		public boolean hasPermission(String permission) {
			return true;
		}

		@Override
		public Locale getLocale() {
			return Locale.ENGLISH;
		}

		@Override
		public Audience getAudience() {
			return Audience.empty();
		}
	}
}

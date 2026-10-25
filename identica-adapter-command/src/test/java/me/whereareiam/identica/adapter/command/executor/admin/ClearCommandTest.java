package me.whereareiam.identica.adapter.command.executor.admin;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.adapter.command.executor.admin.DeleteCommandTest.RecordingActor;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.AccountOperationRequest;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Clear Command")
class ClearCommandTest {
	private final AccountService accounts = mock(AccountService.class);
	private final Commands commands = DeleteCommandTest.commands(Duration.ofMinutes(1));
	private final ClearCommand command = new ClearCommand(() -> commands, ClearCommandTest::messages, accounts);
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
	}

	@DisplayName("Confirming clears the account, not deletes it, and disconnects its player")
	@Test
	void confirmClearsAndDisconnects() {
		command.clear(admin, "Alice");
		verify(accounts, never()).clear(any());

		command.confirmClear(admin);

		ArgumentCaptor<AccountOperationRequest> request = ArgumentCaptor.forClass(AccountOperationRequest.class);
		verify(accounts).clear(request.capture());
		verify(accounts, never()).delete(any());
		assertEquals(alice, request.getValue().getAccount());
		assertTrue(request.getValue().isDisconnect());
		assertEquals("cleared", request.getValue().getDisconnectMessage().getText());
		assertEquals("admin-clear", request.getValue().getReason());
		assertEquals(List.of("confirm", "success"), admin.received);
	}

	@DisplayName("Cancelling keeps the account")
	@Test
	void cancelKeepsTheAccount() {
		command.clear(admin, "Alice");
		command.cancelClear(admin);
		command.confirmClear(admin);

		verify(accounts, never()).clear(any());
		assertEquals(List.of("confirm", "cancelled", "no-pending"), admin.received);
	}

	@DisplayName("A confirmation after the window has passed clears nothing")
	@Test
	void staleConfirmClearsNothing() throws InterruptedException {
		commands.getBehavior().getClear().setConfirmTtl(Duration.ofMillis(1));
		command.clear(admin, "Alice");
		Thread.sleep(20);

		command.confirmClear(admin);

		verify(accounts, never()).clear(any());
		assertEquals(List.of("confirm", "expired"), admin.received);
	}

	@DisplayName("A pending clear and a pending delete of one administrator stay apart")
	@Test
	void clearAndDeleteKeepTheirOwnRequests() {
		DeleteCommand delete = new DeleteCommand(() -> commands, ClearCommandTest::messages, accounts);
		command.clear(admin, "Alice");

		delete.confirmDelete(admin);

		verify(accounts, never()).delete(any());
		verify(accounts, never()).clear(any());
	}

	private static Messages messages() {
		Messages.Commands.Admin.Clear clear = new Messages.Commands.Admin.Clear();
		clear.setConfirm(List.of("confirm"));
		clear.setNoPending("no-pending");
		clear.setExpired("expired");
		clear.setCancelled("cancelled");
		clear.setNotFound("not-found");
		clear.setMultiple(DeleteCommandTest.multiple());
		clear.setSuccess("success");
		clear.setError("error");
		clear.setDisconnect(List.of("cleared"));
		Messages.Commands.Admin.Delete delete = new Messages.Commands.Admin.Delete();
		delete.setNoPending("delete-no-pending");
		Messages.Commands.Admin admin = new Messages.Commands.Admin();
		admin.setClear(clear);
		admin.setDelete(delete);
		Messages.Commands commands = new Messages.Commands();
		commands.setAdmin(admin);
		Messages messages = new Messages();
		messages.setCommands(commands);
		return messages;
	}
}

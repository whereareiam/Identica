package me.whereareiam.identica.feature.verification.command.executor.admin;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.VerificationResetResult;
import me.whereareiam.identica.feature.verification.type.status.VerificationResetStatus;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Verification Reset Command")
class VerificationResetCommandTest {
	private final VerificationService verification = mock(VerificationService.class);
	private final IdentityService identities = mock(IdentityService.class);
	private final AccountPersistenceService accounts = mock(AccountPersistenceService.class);
	private final VerificationResetCommand command = new VerificationResetCommand(
			VerificationResetCommandTest::messages,
			verification,
			identities,
			accounts
	);
	private final Account alice = Account.builder().uniqueId(UUID.randomUUID()).username("Alice").build();
	private final Actor admin = mock(Actor.class);

	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class)))
				.thenAnswer(call -> Component.text(call.getArgument(0, SerializerContent.class).getMessage()));
		Serializer.initialize(() -> engine);
	}

	@BeforeEach
	void registerAlice() {
		when(identities.find(anyString())).thenReturn(Optional.empty());
		when(accounts.findByUsername("Alice")).thenReturn(List.of(alice));
		when(accounts.findByUniqueId(alice.getUniqueId())).thenReturn(Optional.of(alice));
		when(verification.reset(any(UUID.class), any()))
				.thenReturn(VerificationResetResult.builder().status(VerificationResetStatus.RESET).build());
	}

	@DisplayName("Resets every method of an offline player found by username")
	@Test
	void resetsAnOfflinePlayerByUsername() {
		command.reset(admin, "Alice", null);

		verify(verification).reset(eq(alice.getUniqueId()), isNull());
		assertEquals("completed", sent());
	}

	@DisplayName("Resets only the named provider's selections when a provider is given")
	@Test
	void resetsOneProvider() {
		command.reset(admin, "Alice", "credential");

		verify(verification).reset(alice.getUniqueId(), "credential");
	}

	@DisplayName("A blank provider resets everything")
	@Test
	void blankProviderResetsEverything() {
		command.reset(admin, "Alice", " ");

		verify(verification).reset(eq(alice.getUniqueId()), isNull());
	}

	@DisplayName("A target given as a unique id resets that account")
	@Test
	void resetsByUniqueId() {
		command.reset(admin, alice.getUniqueId().toString(), null);

		verify(verification).reset(eq(alice.getUniqueId()), isNull());
	}

	@DisplayName("An unknown username is reported and resets nothing")
	@Test
	void unknownUsername() {
		when(accounts.findByUsername("Nobody")).thenReturn(List.of());

		command.reset(admin, "Nobody", null);

		verify(verification, never()).reset(any(UUID.class), any());
		assertEquals("not-found", sent());
	}

	private String sent() {
		ArgumentCaptor<Component> message = ArgumentCaptor.forClass(Component.class);
		verify(admin).sendMessage(message.capture());
		return ((TextComponent) message.getValue()).content();
	}

	private static VerificationMessages messages() {
		VerificationMessages.Commands.Admin.Reset reset = new VerificationMessages.Commands.Admin.Reset();
		reset.setCompleted("completed");
		reset.setTargetNotFound("not-found");
		VerificationMessages.Commands.Admin admin = new VerificationMessages.Commands.Admin();
		admin.setReset(reset);
		VerificationMessages.Commands commands = new VerificationMessages.Commands();
		commands.setAdmin(admin);
		VerificationMessages messages = new VerificationMessages();
		messages.setCommands(commands);
		return messages;
	}
}

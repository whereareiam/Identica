package me.whereareiam.identica.engine.step;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.UsernameMessages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.provider.ProviderState;
import me.whereareiam.identica.type.provider.ProviderTrait;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Username Availability Step")
class UsernameAvailabilityStepTest {
	private static final UUID ARRIVING = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID HOLDER = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private final AccountPersistenceService accounts = mock(AccountPersistenceService.class);
	private final ProviderManager providerManager = mock(ProviderManager.class);
	private final UsernameAvailabilityStep step = new UsernameAvailabilityStep(accounts, providerManager, UsernameAvailabilityStepTest::messages);

	@BeforeAll
	static void initializeSerializer() {
		Serializer.initialize(() -> new SerializerEngine() {
			@Override
			public @NotNull String serialize(Component component) {
				return component.toString();
			}

			@Override
			public @NotNull Component serialize(SerializerContent content) {
				return Component.text(content.getMessage());
			}

			public @NotNull String renderTemplate(@NotNull SerializerContent content) {
				String message = content.getMessage();
				for (var placeholder : content.getPlaceholders().entrySet())
					message = message.replace("{" + placeholder.getKey() + "}", placeholder.getValue());

				return message;
			}

			@Override
			public @NotNull SerializerOptions.PlaceholderFormat getPlaceholderFormat() {
				return SerializerOptions.PlaceholderFormat.CURLY_BRACES;
			}
		});
	}

	@Test
	@DisplayName("Refuses a username another account holds in another case")
	void refusesAHeldUsername() {
		when(providerManager.findProvidersByTraits(ProviderTrait.AUTHORITATIVE_USERNAME)).thenReturn(List.of());
		when(accounts.findByUsername("alice")).thenReturn(List.of(account(HOLDER, "Alice")));

		StepResult result = step.execute(context("alice", "chosen")).join();

		assertEquals(StepResult.StepStatus.DENIED, result.getStatus());
		assertEquals("taken alice", result.getMessage());
	}

	@Test
	@DisplayName("Lets a free username through")
	void passesAFreeUsername() {
		when(providerManager.findProvidersByTraits(ProviderTrait.AUTHORITATIVE_USERNAME)).thenReturn(List.of());
		when(accounts.findByUsername("alice")).thenReturn(List.of());

		StepResult result = step.execute(context("alice", "chosen")).join();

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
	}

	@Test
	@DisplayName("Does not count the account the player registers as a holder")
	void ignoresTheArrivingAccount() {
		when(providerManager.findProvidersByTraits(ProviderTrait.AUTHORITATIVE_USERNAME)).thenReturn(List.of());
		when(accounts.findByUsername("alice")).thenReturn(List.of(account(ARRIVING, "alice")));

		StepResult result = step.execute(context("alice", "chosen")).join();

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
	}

	@Test
	@DisplayName("Never refuses a provider that vouches for its usernames")
	void passesAProviderThatVouchesForItsUsernames() {
		when(providerManager.findProvidersByTraits(ProviderTrait.AUTHORITATIVE_USERNAME)).thenReturn(List.of(provider("vouched")));

		StepResult result = step.execute(context("alice", "Vouched")).join();

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
		verify(accounts, never()).findByUsername(any());
	}

	private static RegistrationContext context(String username, String providerId) {
		RegistrationContext context = RegistrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(ARRIVING, username, "127.0.0.1"))
				.build();
		context.setProvider(ProviderContext.builder()
				.providerId(providerId)
				.providerSubject("subject")
				.providerUsername(username)
				.build());

		return context;
	}

	private static Account account(UUID uniqueId, String username) {
		return Account.builder().uniqueId(uniqueId).username(username).build();
	}

	private static InternalProvider provider(String id) {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId(id);
		descriptor.setTraits(Set.of(ProviderTrait.AUTHORITATIVE_USERNAME));

		return InternalProvider.builder().descriptor(descriptor).state(ProviderState.ENABLED).build();
	}

	private static UsernameMessages messages() {
		UsernameMessages.Pipeline.Policy policy = new UsernameMessages.Pipeline.Policy();
		policy.setTaken(List.of("taken {username}"));
		UsernameMessages.Pipeline pipeline = new UsernameMessages.Pipeline();
		pipeline.setPolicy(policy);
		UsernameMessages messages = new UsernameMessages();
		messages.setPipeline(pipeline);

		return messages;
	}
}

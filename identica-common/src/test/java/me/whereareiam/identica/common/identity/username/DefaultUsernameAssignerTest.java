package me.whereareiam.identica.common.identity.username;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.configura.Config;
import me.whereareiam.identica.common.identity.username.outcome.RefuseOutcome;
import me.whereareiam.identica.common.identity.username.outcome.RenameArrivingOutcome;
import me.whereareiam.identica.common.identity.username.outcome.RenameHolderOutcome;
import me.whereareiam.identica.model.conflict.ConflictRuleOutcome;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.common.config.defaults.UsernameConflictsDefaults;
import me.whereareiam.identica.common.config.defaults.messages.UsernameMessagesDefaults;
import me.whereareiam.identica.common.config.provider.UsernameConflictsProvider;
import me.whereareiam.identica.common.conflict.DefaultConflictService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.identity.UsernameChangedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.config.UsernameConflicts;
import me.whereareiam.identica.model.config.UsernameMessages;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.username.AccountUsername;
import me.whereareiam.identica.model.identity.username.AccountUsernameHistoryEntry;
import me.whereareiam.identica.model.identity.username.UsernameArrival;
import me.whereareiam.identica.model.identity.username.UsernameAssignment;
import me.whereareiam.identica.model.identity.username.UsernameWriteResult;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.type.provider.ProviderGuarantee;
import me.whereareiam.identica.type.provider.ProviderState;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Default Username Assigner")
class DefaultUsernameAssignerTest {
	private static final String OWNING = "alpha";
	private static final String CHOOSING = "beta";
	private static final UUID ARRIVING = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID HOLDER = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private final InMemoryUsernames usernames = new InMemoryUsernames();
	private final ProviderLinkPersistenceService links = mock(ProviderLinkPersistenceService.class);
	private final ProviderManager providers = mock(ProviderManager.class);
	private final DefaultConflictService conflicts = new DefaultConflictService(Set::of);
	private final UsernameConflictsProvider settingsProvider = mock(UsernameConflictsProvider.class);
	private final SessionService sessions = mock(SessionService.class);
	private final IdentityService identities = mock(IdentityService.class);
	private final EventManager events = mock(EventManager.class);
	private final UsernameMessages messages = new UsernameMessagesDefaults().supply(new UsernameMessages());
	private final UsernameConflicts settings = new UsernameConflictsDefaults().supply(new UsernameConflicts());
	private final DefaultUsernameAssigner assigner = new DefaultUsernameAssigner(
			usernames,
			links,
			providers,
			conflicts,
			settingsProvider,
			sessions,
			identities,
			events,
			new UsernameRenamer(usernames),
			() -> messages
	);

	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.renderTemplate(any(SerializerContent.class))).thenAnswer(call -> {
			SerializerContent content = call.getArgument(0, SerializerContent.class);
			String message = content.getMessage();
			for (Map.Entry<String, String> placeholder : content.getPlaceholders().entrySet())
				message = message.replace("{" + placeholder.getKey() + "}", placeholder.getValue());

			return message;
		});
		Serializer.initialize(() -> engine);
	}

	@BeforeEach
	void setUp() {
		messages.getPipeline().getPolicy().setTaken(List.of("taken {username}"));
		messages.getPipeline().getPolicy().setHolderReplaced(List.of("replaced {username} by {renamed}"));
		when(settingsProvider.get()).thenReturn(settings);
		when(providers.getProviders()).thenReturn(List.of(provider(OWNING, ProviderGuarantee.OWNED_USERNAME), provider(CHOOSING)));
		when(sessions.findByUniqueId(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
		when(sessions.close(any(SessionCloseRequest.class))).thenReturn(CompletableFuture.completedFuture(null));
		when(settingsProvider.mapper()).thenReturn(Config.configured().mapper());
		conflicts.register(new RefuseOutcome());
		conflicts.register(new RenameArrivingOutcome());
		conflicts.register(new RenameHolderOutcome());
	}

	@DisplayName("Gives a new account a username nobody holds")
	@Test
	void takesAFreeUsername() {
		UsernameAssignment assignment = assigner.assign(firstJoin(null, CHOOSING, "Steve"));

		assertAssigned(assignment, "Steve", "Steve", AccountUsernameSource.PROVIDER);
	}

	@DisplayName("Gives an owned username to its owner on a first join and renames a holder whose name nobody vouches for")
	@Test
	void renamesAHolderForTheOwner() {
		holder(CHOOSING, "Steve", AccountUsernameSource.PROVIDER);

		UsernameAssignment assignment = assigner.assign(firstJoin(ARRIVING, OWNING, "Steve"));

		assertAssigned(assignment, "Steve", "Steve", AccountUsernameSource.PROVIDER);
		assertEquals("Steve_beta", usernames.name(HOLDER));
		assertEquals(AccountUsernameSource.SYSTEM, usernames.source(HOLDER));
		ArgumentCaptor<UsernameChangedEvent> event = ArgumentCaptor.forClass(UsernameChangedEvent.class);
		verify(events).call(event.capture());
		assertEquals("Steve_beta", event.getValue().getNewUsername());
	}

	@DisplayName("Lets an online holder keep playing and gives the owner a temporary name for this session")
	@Test
	void keepsAnOnlineHolderPlaying() {
		holder(CHOOSING, "Steve", AccountUsernameSource.PROVIDER);
		online(HOLDER);

		UsernameAssignment assignment = assigner.assign(firstJoin(ARRIVING, OWNING, "Steve"));

		assertAssigned(assignment, "Steve", "Steve_alpha", AccountUsernameSource.PROVIDER);
		assertEquals("Steve_beta", usernames.name(HOLDER));
		verify(sessions, never()).close(any(SessionCloseRequest.class));
	}

	@DisplayName("Disconnects an online holder when the rule says so, and the owner plays under the name at once")
	@Test
	void kicksAnOnlineHolder() {
		settings.getOutcomes().get("rename-holder").put("holderOnline", "KICK_HOLDER");
		holder(CHOOSING, "Steve", AccountUsernameSource.PROVIDER);
		online(HOLDER);

		UsernameAssignment assignment = assigner.assign(firstJoin(ARRIVING, OWNING, "Steve"));

		assertAssigned(assignment, "Steve", "Steve", AccountUsernameSource.PROVIDER);
		ArgumentCaptor<SessionCloseRequest> close = ArgumentCaptor.forClass(SessionCloseRequest.class);
		verify(sessions).close(close.capture());
		assertEquals(HOLDER, close.getValue().getUniqueId());
		assertTrue(close.getValue().isDisconnect());
		assertEquals("replaced Steve by Steve_beta", close.getValue().getDisconnectMessage());
	}

	@DisplayName("Gives a temporary name while a renamed holder still plays under the account's name")
	@Test
	void waitsForARenamedHolderToLeave() {
		stored(ARRIVING, OWNING, "Steve", AccountUsernameSource.PROVIDER);
		Identity holder = mock(Identity.class);
		when(holder.getAccountUniqueId()).thenReturn(HOLDER);
		when(identities.find("Steve")).thenReturn(Optional.of(holder));

		UsernameAssignment assignment = assigner.preview(returning(OWNING, "Steve", "Steve"));

		assertAssigned(assignment, "Steve", "Steve_alpha", AccountUsernameSource.PROVIDER);
	}

	@DisplayName("Takes a rule's settings over the provider's, and the provider's over the outcome's")
	@Test
	void layersOutcomeSettings() {
		settings.getProviders().put(CHOOSING, new LinkedHashMap<>(Map.of("rename-holder", node("pattern", "{username}_offline"))));
		holder(CHOOSING, "Steve", AccountUsernameSource.PROVIDER);

		assigner.assign(firstJoin(ARRIVING, OWNING, "Steve"));
		assertEquals("Steve_offline", usernames.name(HOLDER));

		settings.getRules().getFirst().setOutcome(ConflictRuleOutcome.of("rename-holder", node("pattern", "{username}_was")));
		holder(CHOOSING, "Bob", AccountUsernameSource.PROVIDER);
		assigner.assign(firstJoin(UUID.randomUUID(), OWNING, "Bob"));
		assertEquals("Bob_was", usernames.name(HOLDER));
	}

	@DisplayName("Refuses a chosen username another account holds")
	@Test
	void refusesAChosenHeldUsername() {
		holder(OWNING, "Steve", AccountUsernameSource.PROVIDER);

		UsernameAssignment assignment = assigner.assign(firstJoin(ARRIVING, CHOOSING, "steve"));

		assertTrue(assignment.isRefused());
		assertEquals("taken steve", assignment.getMessage());
		assertEquals("Steve", usernames.name(HOLDER));
	}

	@DisplayName("Renames an account whose owning provider renamed it into a held username")
	@Test
	void renamesTheArrivingAccountOnAProviderRename() {
		stored(ARRIVING, OWNING, "Steve", AccountUsernameSource.PROVIDER);
		holder(CHOOSING, "Bob", AccountUsernameSource.PROVIDER);

		UsernameAssignment assignment = assigner.assign(returning(OWNING, "Bob", "Steve"));

		assertAssigned(assignment, "Bob_alpha", "Bob_alpha", AccountUsernameSource.SYSTEM);
		assertEquals("Bob_alpha", usernames.name(ARRIVING));
		assertEquals(AccountUsernameSource.SYSTEM, usernames.source(ARRIVING));
	}

	@DisplayName("Follows a provider rename into a free username")
	@Test
	void followsAProviderRename() {
		stored(ARRIVING, OWNING, "Steve", AccountUsernameSource.PROVIDER);

		UsernameAssignment assignment = assigner.assign(returning(OWNING, "Bob", "Steve"));

		assertAssigned(assignment, "Bob", "Bob", AccountUsernameSource.PROVIDER);
		assertEquals("Bob", usernames.name(ARRIVING));
	}

	@DisplayName("Keeps a renamed account under its renamed name while its own name is held")
	@Test
	void waitsForItsOwnUsername() {
		stored(ARRIVING, CHOOSING, "Steve_beta", AccountUsernameSource.SYSTEM);
		holder(OWNING, "Steve", AccountUsernameSource.PROVIDER);

		UsernameAssignment assignment = assigner.assign(returning(CHOOSING, "Steve", "Steve"));

		assertAssigned(assignment, "Steve_beta", "Steve_beta", AccountUsernameSource.SYSTEM);
		assertEquals("Steve", usernames.name(HOLDER));
	}

	@DisplayName("Gives a renamed account its own name back once nobody holds it")
	@Test
	void takesItsOwnUsernameBack() {
		stored(ARRIVING, CHOOSING, "Steve_beta", AccountUsernameSource.SYSTEM);

		UsernameAssignment assignment = assigner.assign(returning(CHOOSING, "Steve", "Steve"));

		assertAssigned(assignment, "Steve", "Steve", AccountUsernameSource.PROVIDER);
		assertEquals(AccountUsernameSource.PROVIDER, usernames.source(ARRIVING));
	}

	@DisplayName("Never changes a username an administrator set")
	@Test
	void keepsAManualUsername() {
		stored(ARRIVING, OWNING, "Admin", AccountUsernameSource.MANUAL);

		UsernameAssignment assignment = assigner.assign(returning(OWNING, "Bob", "Steve"));

		assertAssigned(assignment, "Admin", "Admin", AccountUsernameSource.MANUAL);
		assertEquals("Admin", usernames.name(ARRIVING));
	}

	@DisplayName("Renames the arriving account instead of a holder an administrator named")
	@Test
	void renamesTheArrivingAccountInsteadOfAManualHolder() {
		holder(CHOOSING, "Steve", AccountUsernameSource.MANUAL);

		UsernameAssignment assignment = assigner.assign(firstJoin(ARRIVING, OWNING, "Steve"));

		assertAssigned(assignment, "Steve_alpha", "Steve_alpha", AccountUsernameSource.SYSTEM);
		assertEquals("Steve", usernames.name(HOLDER));
	}

	@DisplayName("Gives a stale name of an owning provider to its current owner and the holder a temporary one")
	@Test
	void givesAStaleUsernameToItsCurrentOwner() {
		holder(OWNING, "Steve", AccountUsernameSource.PROVIDER);

		UsernameAssignment assignment = assigner.assign(firstJoin(ARRIVING, OWNING, "Steve"));

		assertAssigned(assignment, "Steve", "Steve", AccountUsernameSource.PROVIDER);
		assertEquals("Steve_alpha", usernames.name(HOLDER));
		assertEquals(AccountUsernameSource.SYSTEM, usernames.source(HOLDER));
	}

	@DisplayName("Keeps the name of an account that arrives through a provider its name does not follow")
	@Test
	void ignoresASecondaryProvider() {
		stored(ARRIVING, CHOOSING, "Steve", AccountUsernameSource.PROVIDER);
		when(links.findByUniqueIdAndProviderId(ARRIVING, OWNING)).thenReturn(Optional.of(link(ARRIVING, OWNING, false)));

		UsernameAssignment assignment = assigner.assign(returning(OWNING, "Notch", "Notch"));

		assertAssigned(assignment, "Steve", "Steve", AccountUsernameSource.PROVIDER);
	}

	@DisplayName("Refuses the arriving player when the rule names an outcome nobody registered")
	@Test
	void refusesAnUnknownOutcome() {
		settings.getRules().getFirst().setOutcome(ConflictRuleOutcome.of("longest-playtime"));
		holder(CHOOSING, "Steve", AccountUsernameSource.PROVIDER);

		assertTrue(assigner.assign(firstJoin(ARRIVING, OWNING, "Steve")).isRefused());
		assertEquals("Steve", usernames.name(HOLDER));
	}

	@DisplayName("Refuses the arriving player when the renamed name would be too long and shortening is off")
	@Test
	void refusesATooLongRename() {
		settings.getOutcomes().get("rename-holder").put("tooLong", "REFUSE");
		holder(CHOOSING, "Christopherson", AccountUsernameSource.PROVIDER);

		assertTrue(assigner.assign(firstJoin(ARRIVING, OWNING, "Christopherson")).isRefused());
		assertEquals("Christopherson", usernames.name(HOLDER));
	}

	@DisplayName("A preview decides like an assignment but changes no account")
	@Test
	void previewWritesNothing() {
		holder(CHOOSING, "Steve", AccountUsernameSource.PROVIDER);

		UsernameAssignment assignment = assigner.preview(firstJoin(ARRIVING, OWNING, "Steve"));

		assertAssigned(assignment, "Steve", "Steve", AccountUsernameSource.PROVIDER);
		assertEquals("Steve", usernames.name(HOLDER));
		verify(events, never()).call(any());
	}

	private static ObjectNode node(String field, String value) {
		return JsonNodeFactory.instance.objectNode().put(field, value);
	}

	private void holder(String providerId, String username, AccountUsernameSource source) {
		stored(HOLDER, providerId, username, source);
	}

	private void stored(UUID uniqueId, String providerId, String username, AccountUsernameSource source) {
		usernames.put(new AccountUsername(uniqueId, username, source, null, 0L));
		AccountProviderLink link = link(uniqueId, providerId, true);
		when(links.findByUniqueId(uniqueId)).thenReturn(List.of(link));
		when(links.findByUniqueIdAndProviderId(uniqueId, providerId)).thenReturn(Optional.of(link));
	}

	private void online(UUID uniqueId) {
		when(sessions.findByUniqueId(uniqueId)).thenReturn(CompletableFuture.completedFuture(Optional.of(mock(Session.class))));
	}

	private static UsernameArrival firstJoin(UUID uniqueId, String providerId, String username) {
		return UsernameArrival.builder()
				.accountUniqueId(uniqueId)
				.providerId(providerId)
				.username(username)
				.firstJoin(true)
				.build();
	}

	private static UsernameArrival returning(String providerId, String username, String knownUsername) {
		return UsernameArrival.builder()
				.accountUniqueId(ARRIVING)
				.providerId(providerId)
				.username(username)
				.knownUsername(knownUsername)
				.build();
	}

	private static AccountProviderLink link(UUID uniqueId, String providerId, boolean primary) {
		return AccountProviderLink.builder()
				.uniqueId(uniqueId)
				.providerId(providerId)
				.providerSubject(uniqueId + "-" + providerId)
				.primaryLink(primary)
				.build();
	}

	private static InternalProvider provider(String id, ProviderGuarantee... guarantees) {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId(id);
		descriptor.setGuarantees(Set.of(guarantees));

		return InternalProvider.builder().descriptor(descriptor).state(ProviderState.ENABLED).build();
	}

	private static void assertAssigned(UsernameAssignment assignment, String username, String sessionUsername, AccountUsernameSource source) {
		assertFalse(assignment.isRefused(), String.valueOf(assignment.getMessage()));
		assertEquals(username, assignment.getUsername());
		assertEquals(sessionUsername, assignment.getSessionUsername());
		assertEquals(source, assignment.getSource());
	}

	/**
	 * Keeps usernames in memory with unique keys, as the database does.
	 */
	private static final class InMemoryUsernames implements AccountUsernamePersistenceService {
		private final Map<UUID, AccountUsername> rows = new HashMap<>();

		void put(AccountUsername username) {
			rows.put(username.getUniqueId(), username);
		}

		String name(UUID uniqueId) {
			return rows.get(uniqueId).getUsername();
		}

		AccountUsernameSource source(UUID uniqueId) {
			return rows.get(uniqueId).getSource();
		}

		@Override
		public @NotNull Optional<AccountUsername> find(@NotNull UUID uniqueId) {
			return Optional.ofNullable(rows.get(uniqueId));
		}

		@Override
		public @NotNull Optional<AccountUsername> findHolder(@NotNull String username) {
			return rows.values().stream()
					.filter(row -> row.getUsername().toLowerCase(Locale.ROOT).equals(username.toLowerCase(Locale.ROOT)))
					.findFirst();
		}

		@Override
		public void setSource(@NotNull UUID uniqueId, @NotNull AccountUsernameSource source) {
			rows.computeIfPresent(uniqueId, (key, row) -> row.toBuilder().source(source).build());
		}

		@Override
		public @NotNull UsernameWriteResult rename(@NotNull AccountUsernameHistoryEntry change) {
			Optional<AccountUsername> holder = findHolder(change.getNewUsername())
					.filter(row -> !row.getUniqueId().equals(change.getUniqueId()));
			if (holder.isPresent()) return UsernameWriteResult.taken(holder.get().getUniqueId());

			rows.computeIfPresent(change.getUniqueId(), (key, row) -> row.toBuilder()
					.username(change.getNewUsername())
					.source(change.getSource())
					.providerId(change.getProviderId())
					.build());
			return UsernameWriteResult.written();
		}
	}
}

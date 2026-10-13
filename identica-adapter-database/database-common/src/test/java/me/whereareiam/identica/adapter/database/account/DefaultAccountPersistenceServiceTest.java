package me.whereareiam.identica.adapter.database.account;

import com.google.inject.util.Providers;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameEntity;
import me.whereareiam.identica.adapter.database.repository.account.AccountRepository;
import me.whereareiam.identica.adapter.database.repository.username.UsernameHistoryRepository;
import me.whereareiam.identica.adapter.database.repository.username.UsernameRepository;
import me.whereareiam.identica.adapter.database.testing.TestDataFactory;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountClearEvent;
import me.whereareiam.identica.event.account.AccountDeleteEvent;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.username.UsernameWriteResult;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Default Account Persistence Service")
class DefaultAccountPersistenceServiceTest {
	@Mock
	private Jdbi jdbi;
	@Mock
	private EntityAccess<AccountEntity> accounts;
	@Mock
	private EntityAccess<UsernameEntity> usernames;
	@Mock
	private AccountRepository accountRepository;
	@Mock
	private UsernameRepository usernameRepository;
	@Mock
	private UsernameHistoryRepository usernameHistoryRepository;
	@Mock
	private EventManager eventManager;

	private DefaultAccountPersistenceService service;

	@BeforeEach
	void setUp() {
		service = new DefaultAccountPersistenceService(
				Providers.of(jdbi),
				Providers.of(accounts),
				Providers.of(usernames),
				Providers.of(accountRepository),
				Providers.of(usernameRepository),
				Providers.of(usernameHistoryRepository),
				eventManager
		);
	}

	@DisplayName("Maps stored account entities back to domain models")
	@Test
	void findByUniqueIdMapsEntityToModel() {
		UUID uniqueId = UUID.randomUUID();
		AccountEntity entity = AccountEntity.builder()
				.uniqueId(uniqueId)
				.createdAt(TestDataFactory.CREATED_AT)
				.lastSeenAt(TestDataFactory.LAST_SEEN_AT)
				.build();
		when(usernames.find(uniqueId)).thenReturn(Optional.of(UsernameEntity.builder().uniqueId(uniqueId).username("PlayerOne").build()));
		when(accounts.find(uniqueId)).thenReturn(Optional.of(entity));

		Optional<Account> result = service.findByUniqueId(uniqueId);

		assertTrue(result.isPresent());
		assertEquals(uniqueId, result.get().getUniqueId());
		assertEquals("PlayerOne", result.get().getUsername());
		assertEquals(TestDataFactory.CREATED_AT, result.get().getCreatedAt());
		assertEquals(TestDataFactory.LAST_SEEN_AT, result.get().getLastSeenAt());
	}

	@DisplayName("Returns an empty result when loading an account throws")
	@Test
	void findByUniqueIdReturnsEmptyOnException() {
		UUID uniqueId = UUID.randomUUID();
		when(usernames.find(uniqueId)).thenThrow(new RuntimeException("boom"));

		assertTrue(service.findByUniqueId(uniqueId).isEmpty());
	}

	@DisplayName("Ignores blank usernames when searching")
	@Test
	void findByUsernameReturnsEmptyForBlankInput() {
		List<Account> result = service.findByUsername("  ");

		assertTrue(result.isEmpty());
		verifyNoInteractions(usernameRepository);
	}

	@DisplayName("Returns no username matches when the repository throws")
	@Test
	void findByUsernameReturnsEmptyOnException() {
		when(usernameRepository.findByUsername("Player")).thenThrow(new RuntimeException("boom"));

		List<Account> result = service.findByUsername("Player");

		assertTrue(result.isEmpty());
	}

	@DisplayName("Creates an account with its username, which holds its key, in one transaction")
	@Test
	void createWritesTheAccountAndItsUsername() {
		Handle handle = runCreation();
		UUID uniqueId = UUID.randomUUID();
		when(usernames.insertIfAbsent(any())).thenReturn(true);

		UsernameWriteResult result = service.create(TestDataFactory.account(uniqueId, "PlayerOne"), AccountUsernameSource.SYSTEM, "alpha");

		verify(accounts).insert(argThat(entity -> uniqueId.equals(entity.getUniqueId())
				&& entity.getCreatedAt() == TestDataFactory.CREATED_AT
				&& entity.getLastSeenAt() == TestDataFactory.LAST_SEEN_AT));
		verify(usernames).insertIfAbsent(argThat(row -> uniqueId.equals(row.getUniqueId())
				&& "PlayerOne".equals(row.getUsername())
				&& "playerone".equals(row.getUsernameKey())
				&& "system".equals(row.getSource())
				&& "alpha".equals(row.getProviderId())));
		verify(handle, never()).rollback();
		assertTrue(result.isWritten());
	}

	@DisplayName("Rolls the account back and names the holder when another account holds its username")
	@Test
	void createRollsBackWhenTheUsernameIsHeld() {
		Handle handle = runCreation();
		UUID uniqueId = UUID.randomUUID();
		UUID holder = UUID.randomUUID();
		when(usernames.insertIfAbsent(any())).thenReturn(false);
		when(usernameRepository.findByUsernameKey("playerone"))
				.thenReturn(Optional.of(UsernameEntity.builder().uniqueId(holder).username("PLAYERONE").usernameKey("playerone").build()));

		UsernameWriteResult result = service.create(TestDataFactory.account(uniqueId, "PlayerOne"), AccountUsernameSource.PROVIDER, null);

		verify(handle).rollback();
		verify(usernames, never()).insert(any());
		assertFalse(result.isWritten());
		assertEquals(holder, result.getHolderUniqueId());
	}

	@DisplayName("Swallows database errors when updating the last-seen timestamp")
	@Test
	void updateLastSeenSwallowsExceptions() {
		UUID uniqueId = UUID.randomUUID();
		when(accounts.update(uniqueId)).thenThrow(new RuntimeException("boom"));

		assertDoesNotThrow(() -> service.updateLastSeen(uniqueId, 123L));
	}

	@DisplayName("Swallows repository errors when deleting an account")
	@Test
	void deleteSwallowsExceptions() {
		runTransactions();
		UUID uniqueId = UUID.randomUUID();
		doThrow(new RuntimeException("boom")).when(accounts).delete(uniqueId);

		assertDoesNotThrow(() -> service.delete(uniqueId));
	}

	@DisplayName("Deletes persisted account data when an account is cleared")
	@Test
	void onAccountLifecycleDeletesOnClear() {
		runTransactions();
		UUID uniqueId = UUID.randomUUID();
		ConnectionIdentity identity = new ConnectionIdentity(uniqueId, "Player", null);
		AccountLifecycleEvent event = new AccountClearEvent(identity);

		service.onAccountLifecycle(event);

		verify(usernameHistoryRepository).deleteAll(uniqueId);
		verify(usernames).delete(uniqueId);
		verify(accounts).delete(uniqueId);
	}

	@DisplayName("Deletes persisted account data when an account is deleted")
	@Test
	void onAccountLifecycleDeletesOnDelete() {
		runTransactions();
		UUID uniqueId = UUID.randomUUID();
		ConnectionIdentity identity = new ConnectionIdentity(uniqueId, "Player", null);
		AccountLifecycleEvent event = new AccountDeleteEvent(identity);

		service.onAccountLifecycle(event);

		verify(accounts).delete(uniqueId);
	}

	@DisplayName("Ignores lifecycle events that do not carry an account UUID")
	@Test
	void onAccountLifecycleIgnoresNullUniqueId() {
		ConnectionIdentity identity = new ConnectionIdentity(null, "Player", null);
		AccountLifecycleEvent event = new AccountDeleteEvent(identity);

		service.onAccountLifecycle(event);

		verify(accounts, never()).delete(org.mockito.ArgumentMatchers.any());
	}

	/**
	 * Runs each transaction's work as the database would, on a handle of the transaction.
	 */
	@SuppressWarnings("unchecked")
	private Handle runCreation() {
		Handle handle = mock(Handle.class);
		doAnswer(call -> call.getArgument(0, HandleCallback.class).withHandle(handle)).when(jdbi).inTransaction(any());
		return handle;
	}

	private void runTransactions() {
		doAnswer(call -> {
			call.getArgument(0, HandleConsumer.class).useHandle(mock(Handle.class));
			return null;
		}).when(jdbi).useTransaction(any());
	}
}

package me.whereareiam.identica.adapter.database.account;

import com.google.inject.util.Providers;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.repository.account.AccountRepository;
import me.whereareiam.identica.adapter.database.testing.TestDataFactory;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountClearEvent;
import me.whereareiam.identica.event.account.AccountDeleteEvent;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.Account;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Default Account Persistence Service")
class DefaultAccountPersistenceServiceTest {
	@Mock
	private EntityAccess<AccountEntity> accounts;
	@Mock
	private AccountRepository accountRepository;
	@Mock
	private EventManager eventManager;

	private DefaultAccountPersistenceService service;

	@BeforeEach
	void setUp() {
		service = new DefaultAccountPersistenceService(Providers.of(accounts), Providers.of(accountRepository), eventManager);
	}

	@DisplayName("Maps stored account entities back to domain models")
	@Test
	void findByUniqueIdMapsEntityToModel() {
		UUID uniqueId = UUID.randomUUID();
		AccountEntity entity = AccountEntity.builder()
				.uniqueId(uniqueId)
				.username("PlayerOne")
				.createdAt(TestDataFactory.CREATED_AT)
				.lastSeenAt(TestDataFactory.LAST_SEEN_AT)
				.build();
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
		when(accounts.find(uniqueId)).thenThrow(new RuntimeException("boom"));

		assertTrue(service.findByUniqueId(uniqueId).isEmpty());
	}

	@DisplayName("Ignores blank usernames when searching")
	@Test
	void findByUsernameReturnsEmptyForBlankInput() {
		List<Account> result = service.findByUsername("  ");

		assertTrue(result.isEmpty());
		verifyNoInteractions(accountRepository);
	}

	@DisplayName("Returns no username matches when the repository throws")
	@Test
	void findByUsernameReturnsEmptyOnException() {
		when(accountRepository.findByUsername("Player")).thenThrow(new RuntimeException("boom"));

		List<Account> result = service.findByUsername("Player");

		assertTrue(result.isEmpty());
	}

	@DisplayName("Persists new accounts through the repository")
	@Test
	void createDelegatesToRepository() {
		UUID uniqueId = UUID.randomUUID();
		Account account = TestDataFactory.account(uniqueId, "PlayerOne");

		Account created = service.create(account);

		verify(accounts).insert(argThat(entity -> uniqueId.equals(entity.getUniqueId())
				&& "PlayerOne".equals(entity.getUsername())
				&& entity.getCreatedAt() == TestDataFactory.CREATED_AT
				&& entity.getLastSeenAt() == TestDataFactory.LAST_SEEN_AT));
		assertEquals(uniqueId, created.getUniqueId());
	}

	@DisplayName("Swallows repository errors when updating the last-seen timestamp")
	@Test
	void updateLastSeenSwallowsExceptions() {
		UUID uniqueId = UUID.randomUUID();
		doThrow(new RuntimeException("boom")).when(accountRepository).updateLastSeen(uniqueId, 123L);

		assertDoesNotThrow(() -> service.updateLastSeen(uniqueId, 123L));
	}

	@DisplayName("Skips username updates when the new username is blank")
	@Test
	void updateUsernameIgnoresBlankInput() {
		service.updateUsername(UUID.randomUUID(), " ");

		verify(accountRepository, never()).updateUsername(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@DisplayName("Delegates username updates to the repository")
	@Test
	void updateUsernameCallsRepository() {
		UUID uniqueId = UUID.randomUUID();

		service.updateUsername(uniqueId, "PlayerTwo");

		verify(accountRepository).updateUsername(uniqueId, "PlayerTwo");
	}

	@DisplayName("Swallows repository errors when deleting an account")
	@Test
	void deleteSwallowsExceptions() {
		UUID uniqueId = UUID.randomUUID();
		doThrow(new RuntimeException("boom")).when(accounts).delete(uniqueId);

		assertDoesNotThrow(() -> service.delete(uniqueId));
	}

	@DisplayName("Deletes persisted account data when an account is cleared")
	@Test
	void onAccountLifecycleDeletesOnClear() {
		UUID uniqueId = UUID.randomUUID();
		ConnectionIdentity identity = new ConnectionIdentity(uniqueId, "Player", null);
		AccountLifecycleEvent event = new AccountClearEvent(identity);

		service.onAccountLifecycle(event);

		verify(accounts).delete(uniqueId);
	}

	@DisplayName("Deletes persisted account data when an account is deleted")
	@Test
	void onAccountLifecycleDeletesOnDelete() {
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
}

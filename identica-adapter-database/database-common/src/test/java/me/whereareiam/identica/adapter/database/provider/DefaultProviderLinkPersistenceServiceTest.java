package me.whereareiam.identica.adapter.database.provider;

import com.google.inject.util.Providers;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderLinkEntity;
import me.whereareiam.identica.adapter.database.mapper.account.AccountProviderLinkMapper;
import me.whereareiam.identica.adapter.database.repository.provider.ProviderLinkRepository;
import me.whereareiam.identica.adapter.database.testing.TestDataFactory;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountClearEvent;
import me.whereareiam.identica.event.account.AccountDeleteEvent;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Default Provider-Link Persistence Service")
class DefaultProviderLinkPersistenceServiceTest {
	@Mock
	private EntityAccess<AccountProviderLinkEntity> links;
	@Mock
	private ProviderLinkRepository repository;
	@Mock
	private EventManager eventManager;

	private DefaultProviderLinkPersistenceService service;

	@BeforeEach
	void setUp() {
		service = new DefaultProviderLinkPersistenceService(Providers.of(links), Providers.of(repository), eventManager);
	}

	@DisplayName("Refuses blank provider IDs and subjects when looking up links by subject")
	@Test
	void findBySubjectReturnsEmptyForBlankValues() {
		assertTrue(service.findBySubject("", "subject").isEmpty());
		assertTrue(service.findBySubject("provider", " ").isEmpty());
		verifyNoInteractions(links, repository);
	}

	@DisplayName("Requires a provider ID when looking up a link by account and provider")
	@Test
	void findByUniqueIdAndProviderIdReturnsEmptyForBlankProviderId() {
		UUID uniqueId = UUID.randomUUID();
		assertTrue(service.findByUniqueIdAndProviderId(uniqueId, " ").isEmpty());
		verifyNoInteractions(links, repository);
	}

	@DisplayName("Rejects upserts with a blank provider ID")
	@Test
	void upsertRejectsBlankProviderId() {
		AccountProviderLink link = TestDataFactory.providerLink(UUID.randomUUID(), " ", "subject", false);

		assertThrows(IllegalArgumentException.class, () -> service.upsert(link));
	}

	@DisplayName("Rejects upserts with a blank provider subject")
	@Test
	void upsertRejectsBlankProviderSubject() {
		AccountProviderLink link = TestDataFactory.providerLink(UUID.randomUUID(), "provider", " ", false);

		assertThrows(IllegalArgumentException.class, () -> service.upsert(link));
	}

	@DisplayName("Returns grouped provider-link usage counts")
	@Test
	void countByProviderReturnsRepositorySnapshot() {
		Map<String, Long> counts = Map.of("example", 4L, "another", 2L);
		when(repository.countByProvider()).thenReturn(counts);

		Map<String, Long> result = service.countByProvider();

		assertEquals(counts, result);
		verify(repository).countByProvider();
	}

	@DisplayName("Writes a link in one upsert that changes only the primary flag and last-seen time of a known link")
	@Test
	void upsertWritesKnownColumnsOnly() {
		UUID uniqueId = UUID.randomUUID();
		AccountProviderLink link = TestDataFactory.providerLink(uniqueId, "provider", "subject", true)
				.toBuilder()
				.linkedAt(1L)
				.lastSeenAt(999L)
				.build();
		AccountProviderLinkEntity stored = AccountProviderLinkEntity.builder()
				.uniqueId(uniqueId)
				.providerId("provider")
				.providerSubject("subject")
				.primaryLink(true)
				.linkedAt(TestDataFactory.LINKED_AT)
				.lastSeenAt(999L)
				.build();
		when(links.find("provider", "subject")).thenReturn(Optional.of(stored));

		AccountProviderLink result = service.upsert(link);

		verify(links).upsert(argThat(entity -> uniqueId.equals(entity.getUniqueId())
				&& "provider".equals(entity.getProviderId())
				&& "subject".equals(entity.getProviderSubject())
				&& entity.isPrimaryLink()
				&& entity.getLinkedAt() == 1L
				&& entity.getLastSeenAt() == 999L), eq("is_primary"), eq("last_seen_at"));
		verify(repository).updatePrimary(uniqueId, "provider", true);
		assertTrue(result.isPrimaryLink());
		assertEquals(TestDataFactory.LINKED_AT, result.getLinkedAt());
		assertEquals(999L, result.getLastSeenAt());
	}

	@DisplayName("Leaves the primary flags of an account alone when saving a link that is not primary")
	@Test
	void upsertKeepsPrimaryFlagsForSecondaryLinks() {
		UUID uniqueId = UUID.randomUUID();
		AccountProviderLink link = TestDataFactory.providerLink(uniqueId, "provider", "subject", false);
		when(links.find("provider", "subject")).thenReturn(Optional.of(AccountProviderLinkMapper.toEntity(link)));

		AccountProviderLink result = service.upsert(link);

		verify(repository, never()).updatePrimary(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
		assertEquals(uniqueId, result.getUniqueId());
	}

	@DisplayName("Delegates provider-link deletion by account UUID")
	@Test
	void deleteAllDelegatesToRepository() {
		UUID uniqueId = UUID.randomUUID();

		service.deleteAll(uniqueId);

		verify(repository).deleteAll(uniqueId);
	}

	@DisplayName("Deletes provider links when an account is cleared")
	@Test
	void onAccountLifecycleDeletesOnClear() {
		UUID uniqueId = UUID.randomUUID();
		ConnectionIdentity identity = new ConnectionIdentity(uniqueId, "Player", null);
		AccountLifecycleEvent event = new AccountClearEvent(identity);

		service.onAccountLifecycle(event);

		verify(repository).deleteAll(uniqueId);
	}

	@DisplayName("Deletes provider links when an account is deleted")
	@Test
	void onAccountLifecycleDeletesOnDelete() {
		UUID uniqueId = UUID.randomUUID();
		ConnectionIdentity identity = new ConnectionIdentity(uniqueId, "Player", null);
		AccountLifecycleEvent event = new AccountDeleteEvent(identity);

		service.onAccountLifecycle(event);

		verify(repository).deleteAll(uniqueId);
	}

	@DisplayName("Ignores lifecycle events without an account UUID when deleting links")
	@Test
	void onAccountLifecycleIgnoresNullUniqueId() {
		ConnectionIdentity identity = new ConnectionIdentity(null, "Player", null);
		AccountLifecycleEvent event = new AccountClearEvent(identity);

		service.onAccountLifecycle(event);

		verify(repository, never()).deleteAll(org.mockito.ArgumentMatchers.any());
	}
}

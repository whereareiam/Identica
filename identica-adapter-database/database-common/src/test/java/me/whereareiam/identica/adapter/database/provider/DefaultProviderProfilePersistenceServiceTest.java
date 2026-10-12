package me.whereareiam.identica.adapter.database.provider;

import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderProfileEntity;
import me.whereareiam.identica.adapter.database.testing.TestDataFactory;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Default Provider-Profile Persistence Service")
class DefaultProviderProfilePersistenceServiceTest {
	@Mock
	private EntityAccess<AccountProviderProfileEntity> profiles;

	private DefaultProviderProfilePersistenceService service;

	@BeforeEach
	void setUp() {
		service = new DefaultProviderProfilePersistenceService(profiles);
	}

	@DisplayName("Refuses blank provider IDs and subjects when looking up profiles")
	@Test
	void findBySubjectReturnsEmptyForBlankValues() {
		assertTrue(service.findBySubject("", "subject").isEmpty());
		assertTrue(service.findBySubject("provider", " ").isEmpty());
		verifyNoInteractions(profiles);
	}

	@DisplayName("Rejects profile upserts with a blank provider ID")
	@Test
	void upsertRejectsBlankProviderId() {
		AccountProviderProfile profile = TestDataFactory.providerProfile(" ", "subject", "user");

		assertThrows(IllegalArgumentException.class, () -> service.upsert(profile));
	}

	@DisplayName("Rejects profile upserts with a blank provider subject")
	@Test
	void upsertRejectsBlankProviderSubject() {
		AccountProviderProfile profile = TestDataFactory.providerProfile("provider", " ", "user");

		assertThrows(IllegalArgumentException.class, () -> service.upsert(profile));
	}

	@DisplayName("Updates an existing provider profile when the subject already exists")
	@Test
	void upsertUpdatesExistingProfile() {
		AccountProviderProfile profile = TestDataFactory.providerProfile("provider", "subject", "user");
		AccountProviderProfileEntity existing = AccountProviderProfileEntity.builder()
				.providerId("provider")
				.providerSubject("subject")
				.providerUsername("old")
				.build();
		when(profiles.find("provider", "subject")).thenReturn(Optional.of(existing));

		AccountProviderProfile result = service.upsert(profile);

		verify(profiles).update(argThat(entity -> "user".equals(entity.getProviderUsername())));
		assertEquals("user", result.getProviderUsername());
	}

	@DisplayName("Inserts a new provider profile when no subject exists yet")
	@Test
	void upsertInsertsNewProfile() {
		AccountProviderProfile profile = TestDataFactory.providerProfile("provider", "subject", "user");
		when(profiles.find("provider", "subject")).thenReturn(Optional.empty());

		AccountProviderProfile result = service.upsert(profile);

		verify(profiles).insert(argThat(entity -> "provider".equals(entity.getProviderId())
				&& "subject".equals(entity.getProviderSubject())
				&& "user".equals(entity.getProviderUsername())));
		assertEquals("user", result.getProviderUsername());
	}

	@DisplayName("Ignores delete requests with blank provider identifiers")
	@Test
	void deleteIgnoresBlankInputs() {
		service.delete(" ", "subject");
		service.delete("provider", " ");

		verify(profiles, never()).delete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}
}

package me.whereareiam.identica.common.identity.username;

import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.logging.LoggingHelper;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.username.AccountUsernameDuplicate;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.username.AccountUsernameDuplicatePersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Username Duplicate Warning")
class UsernameDuplicateWarningTest {
	private static final UUID OLDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID NEWER = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private final AccountUsernameDuplicatePersistenceService duplicates = mock(AccountUsernameDuplicatePersistenceService.class);
	private final ProviderLinkPersistenceService links = mock(ProviderLinkPersistenceService.class);
	private final LoggingHelper log = mock(LoggingHelper.class);
	private final UsernameDuplicateWarning warning = new UsernameDuplicateWarning(duplicates, links);

	@AfterEach
	void detachLog() {
		Logger.init(null);
	}

	@Test
	@DisplayName("Warns once per shared username and names its accounts with their providers and last logins")
	void warnsOncePerSharedUsername() {
		Logger.init(log);
		when(duplicates.findAll()).thenReturn(List.of(
				duplicate("alice", account(OLDER, "Alice", 1000L), account(NEWER, "ALICE", 0L)),
				duplicate("bob", account(OLDER, "Bob", 0L), account(NEWER, "bob", 0L))
		));
		when(links.findByUniqueId(OLDER)).thenReturn(List.of(link(OLDER, "first"), link(OLDER, "second")));
		when(links.findByUniqueId(NEWER)).thenReturn(List.of());

		warning.warn();

		ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
		verify(log, times(2)).warn(anyString(), values.capture());
		Object[] alice = values.getAllValues().getFirst();
		assertEquals("alice", alice[0]);
		assertEquals("Alice (" + OLDER + ", providers first/second, last seen 1970-01-01T00:00:01Z), "
				+ "ALICE (" + NEWER + ", providers none, last seen never)", alice[1]);
	}

	@Test
	@DisplayName("Stays silent while every username belongs to one account")
	void staysSilentWithoutDuplicates() {
		Logger.init(log);
		when(duplicates.findAll()).thenReturn(List.of());

		warning.warn();

		verify(log, never()).warn(anyString(), any(Object[].class));
	}

	private static AccountUsernameDuplicate duplicate(String username, Account... accounts) {
		return AccountUsernameDuplicate.builder().username(username).accounts(List.of(accounts)).build();
	}

	private static Account account(UUID uniqueId, String username, long lastSeenAt) {
		return Account.builder().uniqueId(uniqueId).username(username).lastSeenAt(lastSeenAt).build();
	}

	private static AccountProviderLink link(UUID uniqueId, String providerId) {
		return AccountProviderLink.builder().uniqueId(uniqueId).providerId(providerId).providerSubject(providerId).build();
	}
}

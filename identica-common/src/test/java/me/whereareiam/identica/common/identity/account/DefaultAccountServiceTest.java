package me.whereareiam.identica.common.identity.account;

import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.AccountReservationPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountClearEvent;
import me.whereareiam.identica.event.account.AccountDeleteEvent;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.AccountOperationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Default Account Service")
class DefaultAccountServiceTest {
	@Mock
	private AccountPersistenceService accountPersistenceService;
	@Mock
	private AccountReservationPersistenceService reservationPersistenceService;
	@Mock
	private SessionService sessionService;
	@Mock
	private EventManager eventManager;

	private DefaultAccountService service;

	@BeforeEach
	void setUp() {
		service = new DefaultAccountService(
				accountPersistenceService,
				reservationPersistenceService,
				sessionService,
				eventManager,
				this::settings
		);
	}

	@DisplayName("Clearing an account closes its session, reserves the username, and publishes a clear event")
	@Test
	void clearClosesSessionAndFiresClearLifecycleEvent() {
		Account account = account();
		when(sessionService.close(org.mockito.ArgumentMatchers.any(SessionCloseRequest.class)))
				.thenReturn(CompletableFuture.completedFuture(null));

		service.clear(AccountOperationRequest.builder()
				.account(account)
				.disconnect(true)
				.disconnectMessage("line one\nline two")
				.build());

		ArgumentCaptor<SessionCloseRequest> requestCaptor = ArgumentCaptor.forClass(SessionCloseRequest.class);
		verify(sessionService).close(requestCaptor.capture());
		assertEquals(account.getUniqueId(), requestCaptor.getValue().getUniqueId());
		assertEquals("line one\nline two", requestCaptor.getValue().getDisconnectMessage());
		assertTrue(requestCaptor.getValue().isDisconnect());
		verify(reservationPersistenceService).reserve(
				org.mockito.ArgumentMatchers.eq("username:player"),
				org.mockito.ArgumentMatchers.eq(account.getUniqueId()),
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyLong()
		);

		ArgumentCaptor<AccountLifecycleEvent> eventCaptor = ArgumentCaptor.forClass(AccountLifecycleEvent.class);
		verify(eventManager).call(eventCaptor.capture());
		assertInstanceOf(AccountClearEvent.class, eventCaptor.getValue());
		assertEquals(account.getUniqueId(), eventCaptor.getValue().getIdentity().getAccountUniqueId());
	}

	@DisplayName("The disconnect decision is passed on without a message, and its absence with one")
	@Test
	void disconnectDecisionIsIndependentOfMessage() {
		Account account = account();
		when(sessionService.close(org.mockito.ArgumentMatchers.any(SessionCloseRequest.class)))
				.thenReturn(CompletableFuture.completedFuture(null));

		service.delete(AccountOperationRequest.builder().account(account).disconnect(true).build());
		service.delete(AccountOperationRequest.builder().account(account).disconnectMessage("text").build());

		ArgumentCaptor<SessionCloseRequest> requestCaptor = ArgumentCaptor.forClass(SessionCloseRequest.class);
		verify(sessionService, org.mockito.Mockito.times(2)).close(requestCaptor.capture());
		assertTrue(requestCaptor.getAllValues().get(0).isDisconnect());
		assertEquals("", requestCaptor.getAllValues().get(0).getDisconnectMessage());
		assertFalse(requestCaptor.getAllValues().get(1).isDisconnect());
	}

	@DisplayName("Deleting an account closes its session and publishes a delete event")
	@Test
	void deleteClosesSessionAndFiresDeleteLifecycleEvent() {
		Account account = account();
		when(sessionService.close(org.mockito.ArgumentMatchers.any(SessionCloseRequest.class)))
				.thenReturn(CompletableFuture.completedFuture(null));

		service.delete(AccountOperationRequest.builder()
				.account(account)
				.disconnectMessage("deleted")
				.build());

		ArgumentCaptor<AccountLifecycleEvent> eventCaptor = ArgumentCaptor.forClass(AccountLifecycleEvent.class);
		verify(eventManager).call(eventCaptor.capture());
		assertInstanceOf(AccountDeleteEvent.class, eventCaptor.getValue());
		assertEquals(account.getUniqueId(), eventCaptor.getValue().getIdentity().getAccountUniqueId());
	}

	private Account account() {
		return Account.builder()
				.uniqueId(UUID.randomUUID())
				.username("Player")
				.build();
	}

	private Settings settings() {
		Settings settings = new Settings();
		Settings.Identity identity = new Settings.Identity();
		identity.setReservationTtl(Duration.ofMinutes(1));
		settings.setIdentity(identity);
		return settings;
	}
}

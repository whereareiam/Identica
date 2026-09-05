package me.whereareiam.identica.trait.authoritative.username.database;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.trait.authoritative.username.database.repository.AuthoritativeUsernameHistoryRepository;
import me.whereareiam.identica.trait.authoritative.username.database.repository.AuthoritativeUsernameStateRepository;
import me.whereareiam.identica.trait.authoritative.username.database.service.DefaultAccountUsernameHistoryPersistenceService;
import me.whereareiam.identica.trait.authoritative.username.database.service.DefaultAccountUsernameStatePersistenceService;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Authoritative Username Database Module")
class AuthoritativeUsernameDatabaseModuleTest {
	@Test
	void bindsPersistenceServices() {
		Jdbi jdbi = mock(Jdbi.class);
		EventManager events = mock(EventManager.class);
		AuthoritativeUsernameStateRepository stateRepository = mock(AuthoritativeUsernameStateRepository.class);
		AuthoritativeUsernameHistoryRepository historyRepository = mock(AuthoritativeUsernameHistoryRepository.class);
		when(jdbi.onDemand(AuthoritativeUsernameStateRepository.class)).thenReturn(stateRepository);
		when(jdbi.onDemand(AuthoritativeUsernameHistoryRepository.class)).thenReturn(historyRepository);

		Injector injector = Guice.createInjector(
				new AuthoritativeUsernameDatabaseModule(),
				new AbstractModule() {
					@Override
					protected void configure() {
						bind(Jdbi.class).toInstance(jdbi);
						bind(EventManager.class).toInstance(events);
					}
				}
		);

		assertInstanceOf(
				DefaultAccountUsernameStatePersistenceService.class,
				injector.getInstance(AccountUsernameStatePersistenceService.class)
		);
		assertInstanceOf(
				DefaultAccountUsernameHistoryPersistenceService.class,
				injector.getInstance(AccountUsernameHistoryPersistenceService.class)
		);
		verifyNoInteractions(events);
	}
}

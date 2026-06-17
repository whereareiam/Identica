package me.whereareiam.identica.provider.capability.authoritative.username.database;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.provider.capability.authoritative.username.database.repository.AuthoritativeUsernameHistoryRepository;
import me.whereareiam.identica.provider.capability.authoritative.username.database.repository.AuthoritativeUsernameStateRepository;
import me.whereareiam.identica.provider.capability.authoritative.username.database.service.DefaultAccountUsernameHistoryPersistenceService;
import me.whereareiam.identica.provider.capability.authoritative.username.database.service.DefaultAccountUsernameStatePersistenceService;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Authoritative Username Database Module")
class AuthoritativeUsernameDatabaseModuleTest {
	@Test
	void bindsPersistenceServices() {
		Jdbi jdbi = mock(Jdbi.class);
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
							bind(new TypeLiteral<Registry<AccountLifecycleParticipant>>() {})
									.toInstance(new Registry<>() {
										@Override
										public void register(AccountLifecycleParticipant value) {
										}

										@Override
										public void unregister(AccountLifecycleParticipant value) {
										}

										@Override
										public java.util.Set<AccountLifecycleParticipant> values() {
											return java.util.Set.of();
										}
									});
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
	}
}

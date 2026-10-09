package me.whereareiam.identica.trait.authoritative.username.database;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import me.whereareiam.identica.trait.authoritative.username.database.repository.AuthoritativeUsernameHistoryRepository;
import me.whereareiam.identica.trait.authoritative.username.database.repository.AuthoritativeUsernameStateRepository;
import me.whereareiam.identica.trait.authoritative.username.database.service.DefaultAccountUsernameHistoryPersistenceService;
import me.whereareiam.identica.trait.authoritative.username.database.service.DefaultAccountUsernameStatePersistenceService;
import org.jdbi.v3.core.Jdbi;

public class AuthoritativeUsernameDatabaseModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(AccountUsernameStatePersistenceService.class).to(DefaultAccountUsernameStatePersistenceService.class).asEagerSingleton();
		bind(AccountUsernameHistoryPersistenceService.class).to(DefaultAccountUsernameHistoryPersistenceService.class).asEagerSingleton();
		bind(AuthoritativeUsernameSchemaContributor.class).asEagerSingleton();
	}

	@Provides
	@Singleton
	AuthoritativeUsernameStateRepository provideStateRepository(Jdbi jdbi) {
		return jdbi.onDemand(AuthoritativeUsernameStateRepository.class);
	}

	@Provides
	@Singleton
	AuthoritativeUsernameHistoryRepository provideHistoryRepository(Jdbi jdbi) {
		return jdbi.onDemand(AuthoritativeUsernameHistoryRepository.class);
	}
}

package me.whereareiam.identica.provider.password.database;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import me.whereareiam.identica.provider.password.database.repository.PasswordAccountPasswordRepository;
import me.whereareiam.identica.provider.password.database.repository.PasswordAccountRepository;
import org.jdbi.v3.core.Jdbi;

public class DatabaseModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(PasswordAccountPersistenceService.class).to(DefaultPasswordAccountPersistenceService.class).asEagerSingleton();
		bind(DatabaseInitializer.class).asEagerSingleton();
	}

	@Provides
	@Singleton
	public PasswordAccountRepository providePasswordAccountRepository(Jdbi jdbi) {
		return jdbi.onDemand(PasswordAccountRepository.class);
	}

	@Provides
	@Singleton
	public PasswordAccountPasswordRepository providePasswordAccountPasswordRepository(Jdbi jdbi) {
		return jdbi.onDemand(PasswordAccountPasswordRepository.class);
	}
}

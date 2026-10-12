package me.whereareiam.identica.provider.credential.database;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.database.schema.DatabaseSchema;
import me.whereareiam.identica.provider.credential.database.entity.CredentialAccountEntity;
import me.whereareiam.identica.provider.credential.database.entity.CredentialAccountHistoryEntity;
import me.whereareiam.identica.provider.credential.database.repository.CredentialAccountRepository;
import org.jdbi.v3.core.Jdbi;

public class DatabaseModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(CredentialAccountPersistenceService.class).to(DefaultCredentialAccountPersistenceService.class).asEagerSingleton();
		Multibinder.newSetBinder(binder(), DatabaseSchema.class)
				.addBinding()
				.to(CredentialSchema.class);
	}

	@Provides
	@Singleton
	public EntityAccess<CredentialAccountEntity> provideCredentialAccounts(Jdbi jdbi) {
		return EntityAccess.of(jdbi, Dialectica.entity(CredentialAccountEntity.class));
	}

	@Provides
	@Singleton
	public EntityAccess<CredentialAccountHistoryEntity> provideCredentialAccountHistory(Jdbi jdbi) {
		return EntityAccess.of(jdbi, Dialectica.entity(CredentialAccountHistoryEntity.class));
	}

	@Provides
	@Singleton
	public CredentialAccountRepository provideCredentialAccountRepository(Jdbi jdbi) {
		return jdbi.onDemand(CredentialAccountRepository.class);
	}
}

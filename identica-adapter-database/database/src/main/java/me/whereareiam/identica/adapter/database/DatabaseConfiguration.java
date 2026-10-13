package me.whereareiam.identica.adapter.database;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.account.DefaultAccountPersistenceService;
import me.whereareiam.identica.adapter.database.account.DefaultAccountReservationPersistenceService;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderLinkEntity;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderProfileEntity;
import me.whereareiam.identica.adapter.database.entity.account.AccountReservationEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameHistoryEntity;
import me.whereareiam.identica.adapter.database.provider.DefaultProviderLinkPersistenceService;
import me.whereareiam.identica.adapter.database.provider.DefaultProviderProfilePersistenceService;
import me.whereareiam.identica.adapter.database.repository.account.AccountRepository;
import me.whereareiam.identica.adapter.database.repository.account.AccountReservationRepository;
import me.whereareiam.identica.adapter.database.repository.provider.ProviderLinkRepository;
import me.whereareiam.identica.adapter.database.repository.username.UsernameHistoryRepository;
import me.whereareiam.identica.adapter.database.repository.username.UsernameRepository;
import me.whereareiam.identica.adapter.database.username.DefaultAccountUsernameDuplicatePersistenceService;
import me.whereareiam.identica.adapter.database.username.DefaultAccountUsernamePersistenceService;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.AccountReservationPersistenceService;
import me.whereareiam.identica.database.DatabaseService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.database.schema.SchemaInstaller;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.persistence.username.AccountUsernameDuplicatePersistenceService;
import me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService;
import org.jdbi.v3.core.Jdbi;

import javax.sql.DataSource;

public class DatabaseConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(Jdbi.class).toProvider(JdbiProvider.class);
		bind(DatabaseService.class).to(DefaultDatabaseService.class).asEagerSingleton();
		Multibinder.newSetBinder(binder(), RuntimeLifecycle.class).addBinding().to(DefaultDatabaseService.class);
		bind(AccountPersistenceService.class).to(DefaultAccountPersistenceService.class).asEagerSingleton();
		bind(AccountReservationPersistenceService.class).to(DefaultAccountReservationPersistenceService.class).asEagerSingleton();
		bind(ProviderLinkPersistenceService.class).to(DefaultProviderLinkPersistenceService.class).asEagerSingleton();
		bind(ProviderProfilePersistenceService.class).to(DefaultProviderProfilePersistenceService.class).asEagerSingleton();
		bind(AccountUsernamePersistenceService.class).to(DefaultAccountUsernamePersistenceService.class).asEagerSingleton();
		bind(AccountUsernameDuplicatePersistenceService.class).to(DefaultAccountUsernameDuplicatePersistenceService.class).asEagerSingleton();
	}

	@Provides
	public DataSource provideDataSource(DefaultDatabaseService databaseService) {
		return open(databaseService).getDataSource();
	}

	@Provides
	@Singleton
	public SchemaInstaller provideSchemaInstaller(DefaultDatabaseService databaseService) {
		return open(databaseService).getSchemaInstaller();
	}

	@Provides
	@Singleton
	public EntityAccess<AccountEntity> provideAccounts(Jdbi jdbi) {
		return EntityAccess.of(jdbi, Dialectica.entity(AccountEntity.class));
	}

	@Provides
	@Singleton
	public EntityAccess<AccountReservationEntity> provideAccountReservations(Jdbi jdbi) {
		return EntityAccess.of(jdbi, Dialectica.entity(AccountReservationEntity.class));
	}

	@Provides
	@Singleton
	public EntityAccess<AccountProviderLinkEntity> provideProviderLinks(Jdbi jdbi) {
		return EntityAccess.of(jdbi, Dialectica.entity(AccountProviderLinkEntity.class));
	}

	@Provides
	@Singleton
	public EntityAccess<AccountProviderProfileEntity> provideProviderProfiles(Jdbi jdbi) {
		return EntityAccess.of(jdbi, Dialectica.entity(AccountProviderProfileEntity.class));
	}

	@Provides
	@Singleton
	public EntityAccess<UsernameEntity> provideUsernames(Jdbi jdbi) {
		return EntityAccess.of(jdbi, Dialectica.entity(UsernameEntity.class));
	}

	@Provides
	@Singleton
	public EntityAccess<UsernameHistoryEntity> provideUsernameHistory(Jdbi jdbi) {
		return EntityAccess.of(jdbi, Dialectica.entity(UsernameHistoryEntity.class));
	}

	@Provides
	@Singleton
	public AccountRepository provideAccountRepository(Jdbi jdbi) {
		return jdbi.onDemand(AccountRepository.class);
	}

	@Provides
	@Singleton
	public AccountReservationRepository provideAccountReservationRepository(Jdbi jdbi) {
		return jdbi.onDemand(AccountReservationRepository.class);
	}

	@Provides
	@Singleton
	public ProviderLinkRepository provideProviderLinkRepository(Jdbi jdbi) {
		return jdbi.onDemand(ProviderLinkRepository.class);
	}

	@Provides
	@Singleton
	public UsernameRepository provideUsernameRepository(Jdbi jdbi) {
		return jdbi.onDemand(UsernameRepository.class);
	}

	@Provides
	@Singleton
	public UsernameHistoryRepository provideUsernameHistoryRepository(Jdbi jdbi) {
		return jdbi.onDemand(UsernameHistoryRepository.class);
	}

	private static DefaultDatabaseService open(DefaultDatabaseService databaseService) {
		if (!databaseService.isInitialized())
			throw new IllegalStateException("The database is not open yet; it opens in the persistence phase of the startup");

		return databaseService;
	}
}

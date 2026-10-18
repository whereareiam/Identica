package me.whereareiam.identica.adapter.database.testing;

import com.google.inject.util.Providers;
import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.account.DefaultAccountPersistenceService;
import me.whereareiam.identica.adapter.database.address.DefaultAccountAddressPersistenceService;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.entity.address.AccountAddressEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameHistoryEntity;
import me.whereareiam.identica.adapter.database.repository.account.AccountRepository;
import me.whereareiam.identica.adapter.database.repository.address.AccountAddressRepository;
import me.whereareiam.identica.adapter.database.repository.username.UsernameRepository;
import me.whereareiam.identica.adapter.database.username.DefaultAccountUsernameDuplicatePersistenceService;
import me.whereareiam.identica.adapter.database.username.DefaultAccountUsernamePersistenceService;
import me.whereareiam.identica.event.EventManager;
import org.jdbi.v3.core.Jdbi;
import org.mockito.Mockito;

/**
 * Builds the account and username persistence services on an open database, as the plugin wires them.
 */
public final class PersistenceServices {
	public static DefaultAccountPersistenceService accounts(Jdbi jdbi) {
		return new DefaultAccountPersistenceService(
				Providers.of(jdbi),
				Providers.of(EntityAccess.of(jdbi, Dialectica.entity(AccountEntity.class))),
				Providers.of(EntityAccess.of(jdbi, Dialectica.entity(UsernameEntity.class))),
				Providers.of(jdbi.onDemand(AccountRepository.class)),
				Providers.of(jdbi.onDemand(UsernameRepository.class)),
				Mockito.mock(EventManager.class)
		);
	}

	public static DefaultAccountUsernamePersistenceService usernames(Jdbi jdbi) {
		return new DefaultAccountUsernamePersistenceService(
				Providers.of(jdbi),
				Providers.of(EntityAccess.of(jdbi, Dialectica.entity(UsernameEntity.class))),
				Providers.of(EntityAccess.of(jdbi, Dialectica.entity(UsernameHistoryEntity.class))),
				Providers.of(jdbi.onDemand(UsernameRepository.class))
		);
	}

	public static DefaultAccountUsernameDuplicatePersistenceService duplicates(Jdbi jdbi) {
		return new DefaultAccountUsernameDuplicatePersistenceService(
				Providers.of(EntityAccess.of(jdbi, Dialectica.entity(AccountEntity.class))),
				Providers.of(jdbi.onDemand(UsernameRepository.class))
		);
	}

	public static DefaultAccountAddressPersistenceService addresses(Jdbi jdbi) {
		return new DefaultAccountAddressPersistenceService(
				Providers.of(jdbi),
				Providers.of(EntityAccess.of(jdbi, Dialectica.entity(AccountAddressEntity.class))),
				Providers.of(jdbi.onDemand(AccountAddressRepository.class))
		);
	}
}

package me.whereareiam.identica.common.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.config.defaults.AccountAddressesDefaults;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.model.config.AccountAddresses;

import java.nio.file.Path;

/**
 * Loads {@code identity/addresses}, which says which IP addresses Identica keeps for accounts.
 */
@Singleton
public class AccountAddressesProvider extends ConfigProvider<AccountAddresses> {
	@Inject
	public AccountAddressesProvider(
			@Named("identityPath") Path identityPath,
			Registry<Reloadable> reloadables
	) {
		super(identityPath, "addresses", AccountAddresses.class, reloadables);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(AccountAddressesDefaults.class);
	}
}

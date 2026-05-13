package me.whereareiam.identica.provider.password.cryptography.bcrypt;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import me.whereareiam.identica.provider.password.CryptographyAlgorithm;

public class BcryptCryptographyModule extends AbstractModule {
	@Override
	protected void configure() {
		Multibinder.newSetBinder(binder(), CryptographyAlgorithm.class)
				.addBinding()
				.to(BcryptCryptographyAlgorithm.class);
	}
}

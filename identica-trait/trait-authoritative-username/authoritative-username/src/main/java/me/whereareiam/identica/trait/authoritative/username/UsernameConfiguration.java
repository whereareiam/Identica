package me.whereareiam.identica.trait.authoritative.username;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.trait.authoritative.username.database.AuthoritativeUsernameDatabaseModule;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;

import java.nio.file.Path;

/** Installs the mandatory username identity subsystem and its lifecycle. */
@RequiredArgsConstructor
public class UsernameConfiguration extends AbstractModule {
	private final Path path;

	@Override
	protected void configure() {
		install(new UsernameModule(path));
		install(new AuthoritativeUsernameDatabaseModule());
		Multibinder.newSetBinder(binder(), RuntimeLifecycle.class).addBinding().to(UsernameLifecycle.class);
	}
}

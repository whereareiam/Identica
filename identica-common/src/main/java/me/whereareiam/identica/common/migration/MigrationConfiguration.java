package me.whereareiam.identica.common.migration;

import com.google.inject.AbstractModule;
import me.whereareiam.identica.common.migration.confirmation.MigrationConfirmationStore;
import me.whereareiam.identica.service.MigrationService;

public class MigrationConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(MigrationConfirmationStore.class).asEagerSingleton();
		bind(MigrationService.class).to(DefaultMigrationService.class).asEagerSingleton();
		bind(MigrationConfirmationLifecycle.class).asEagerSingleton();
	}
}

package me.whereareiam.identica.feature.sentinel;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.feature.sentinel.SentinelDefinition;
import me.whereareiam.identica.feature.sentinel.SentinelService;
import me.whereareiam.identica.feature.sentinel.config.migration.SentinelConfigMigrations;
import me.whereareiam.identica.feature.sentinel.config.provider.SentinelMessagesProvider;
import me.whereareiam.identica.feature.sentinel.config.provider.SentinelSettingsProvider;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelMessages;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelSettings;
import me.whereareiam.identica.feature.sentinel.type.ContestedConnectionSentinelDefinition;
import me.whereareiam.identica.feature.sentinel.type.ResumeSpamSentinelDefinition;
import me.whereareiam.strata.MigrationStream;
import me.whereareiam.strata.adapter.configura.ConfigContext;

import java.nio.file.Path;

public class SentinelCommonConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(SentinelMessages.class).toProvider(SentinelMessagesProvider.class);
		bind(SentinelSettings.class).toProvider(SentinelSettingsProvider.class);
		bind(new TypeLiteral<Registry<SentinelDefinition>>() {})
				.to(SentinelRegistry.class)
				.in(com.google.inject.Singleton.class);
		bind(SentinelService.class).to(DefaultSentinelService.class).in(com.google.inject.Singleton.class);
		bind(ConnectionAttemptSentinelLifecycle.class).in(com.google.inject.Singleton.class);
		bind(ResumeSpamSentinelDefinition.class).in(com.google.inject.Singleton.class);
		bind(ContestedConnectionSentinelDefinition.class).in(com.google.inject.Singleton.class);
		bind(ContestedLoginSentinelLifecycle.class).in(com.google.inject.Singleton.class);
	}

	/**
	 * Adds this feature's configuration migrations to those Identica runs before any document is read.
	 */
	@ProvidesIntoSet
	@Singleton
	MigrationStream<ConfigContext> sentinelConfigMigrations(Configura configura, @Named("dataPath") Path dataPath) {
		return SentinelConfigMigrations.on(configura, dataPath);
	}
}

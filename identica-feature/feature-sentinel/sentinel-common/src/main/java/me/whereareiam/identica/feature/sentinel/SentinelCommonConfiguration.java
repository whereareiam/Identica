package me.whereareiam.identica.feature.sentinel;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.feature.sentinel.config.provider.SentinelMessagesProvider;
import me.whereareiam.identica.feature.sentinel.config.provider.SentinelSettingsProvider;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelMessages;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelSettings;
import me.whereareiam.identica.feature.sentinel.type.ResumeSpamSentinelDefinition;
import me.whereareiam.identica.feature.sentinel.SentinelDefinition;
import me.whereareiam.identica.feature.sentinel.SentinelService;

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
	}
}

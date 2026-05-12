package me.whereareiam.identica.common;

import com.google.inject.Inject;
import com.google.inject.Injector;
import me.whereareiam.identica.IdenticaAPI;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.common.config.ConfigInitializer;
import me.whereareiam.identica.common.logging.WelcomeBannerPrinter;
import me.whereareiam.identica.database.DatabaseService;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaBootstrappedEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaReadyEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.listener.ListenerRegistrar;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.logging.LoggingHelper;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.sentinel.SentinelDefinition;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.type.event.EventOrder;
import me.whereareiam.identica.common.sentinel.ResumeSpamSentinelDefinition;

public class Identica implements EventListener {
	private final Injector injector;
	private final ListenerRegistrar listenerRegistrar;
	private final Registry<SentinelDefinition> sentinelRegistry;
	private final ResumeSpamSentinelDefinition resumeSpamSentinelDefinition;

	@Inject
	public Identica(
			Injector injector,
			EventManager eventManager,
			ListenerRegistrar listenerRegistrar,
			Registry<SentinelDefinition> sentinelRegistry,
			ResumeSpamSentinelDefinition resumeSpamSentinelDefinition
	) {
		this.injector = injector;
		this.listenerRegistrar = listenerRegistrar;
		this.sentinelRegistry = sentinelRegistry;
		this.resumeSpamSentinelDefinition = resumeSpamSentinelDefinition;

		eventManager.register(this);
	}

	@IdenticEvent
	public void onBootstrapped(IdenticaBootstrappedEvent event) {
		Logger.init(injector.getInstance(LoggingHelper.class));

		sentinelRegistry.register(resumeSpamSentinelDefinition);

		ConfigInitializer.initialize(injector);
		injector.getInstance(DatabaseService.class);

		IdenticaAPI.initialize(injector);
	}

	@IdenticEvent(EventOrder.LOW)
	public void onReady(IdenticaReadyEvent event) {
		injector.getInstance(CommandService.class);
		injector.getInstance(ProviderManager.class).loadProviders();
		listenerRegistrar.registerListeners();

		injector.getInstance(WelcomeBannerPrinter.class).print();
	}

	@IdenticEvent(EventOrder.LOW)
	public void onShutdown(IdenticaShutdownEvent event) {
		injector.getInstance(ProviderManager.class).unloadProviders();

		sentinelRegistry.unregister(resumeSpamSentinelDefinition);

		IdenticaAPI.shutdown();
	}
}

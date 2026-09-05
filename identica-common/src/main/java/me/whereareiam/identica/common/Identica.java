package me.whereareiam.identica.common;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import me.whereareiam.identica.IdenticaAPI;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.common.config.ConfigInitializer;
import me.whereareiam.identica.common.logging.WelcomeBannerPrinter;
import me.whereareiam.identica.database.DatabaseService;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.common.feature.FeatureRuntime;
import me.whereareiam.identica.common.provider.classloader.SharedLibraryClassLoaderFactory;
import me.whereareiam.identica.event.lifecycle.IdenticaReadyEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.listener.ListenerRegistrar;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.logging.LoggingHelper;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.event.EventOrder;

public class Identica implements EventListener {
	private final Injector injector;
	private final List<RuntimeLifecycle> lifecycles = new ArrayList<>();
	private final ListenerRegistrar listenerRegistrar;

	@Inject
	public Identica(
			Injector injector,
			EventManager eventManager,
			ListenerRegistrar listenerRegistrar
	) {
		this.injector = injector;
		this.listenerRegistrar = listenerRegistrar;

		eventManager.register(this);
	}

	public void bootstrap() {
		Logger.init(injector.getInstance(LoggingHelper.class));

		ConfigInitializer.initialize(injector);
		injector.getInstance(DatabaseService.class);

		IdenticaAPI.initialize(injector);
		for (RuntimeLifecycle lifecycle : injector.getInstance(Key.get(new TypeLiteral<Set<RuntimeLifecycle>>() {}))) {
			lifecycles.add(lifecycle);
			lifecycle.initialize();
		}
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
		try {
			injector.getInstance(ProviderManager.class).unloadProviders();
		} finally {
			try {
				injector.getInstance(FeatureRuntime.class).shutdown();
			} finally {
				for (int i = lifecycles.size() - 1; i >= 0; i--) {
					try {
						lifecycles.get(i).shutdown();
					} catch (RuntimeException failure) {
						Logger.warn("Failed to stop identity subsystem: %s", failure.getMessage());
					}
				}
				lifecycles.clear();
				IdenticaAPI.shutdown();
				try {
					injector.getInstance(SharedLibraryClassLoaderFactory.class).close();
				} catch (java.io.IOException exception) {
					Logger.warn("Failed to close shared provider libraries: %s", exception.getMessage());
				}
			}
		}
	}
}

package me.whereareiam.identica.common.listener;

import com.google.inject.Inject;
import me.whereareiam.identica.IdenticaAPI;
import me.whereareiam.identica.common.logging.WelcomeBannerPrinter;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import me.whereareiam.identica.listener.ListenerRegistrar;
import org.jetbrains.annotations.NotNull;

/**
 * Lets Identica handle player connections as the last step of its startup: registers the platform's listeners,
 * marks the API ready, which runs the actions waiting for it, and prints the welcome banner.
 */
public final class ConnectionLifecycle implements RuntimeLifecycle {
	private final ListenerRegistrar listenerRegistrar;
	private final WelcomeBannerPrinter bannerPrinter;

	@Inject
	public ConnectionLifecycle(@NotNull ListenerRegistrar listenerRegistrar, @NotNull WelcomeBannerPrinter bannerPrinter) {
		this.listenerRegistrar = listenerRegistrar;
		this.bannerPrinter = bannerPrinter;
	}

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.CONNECTIONS;
	}

	@Override
	public void initialize() {
		listenerRegistrar.registerListeners();
		IdenticaAPI.markReady();
		bannerPrinter.print();
	}

	/**
	 * Leaves the platform's listeners registered: the proxy removes them with the plugin.
	 */
	@Override
	public void shutdown() {
	}
}

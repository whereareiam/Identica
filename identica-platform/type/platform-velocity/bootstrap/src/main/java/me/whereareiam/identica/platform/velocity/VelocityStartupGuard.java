package me.whereareiam.identica.platform.velocity;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import net.kyori.adventure.text.Component;

/**
 * Refuses every connection until Identica has finished its startup.
 * <p>
 * A proxy keeps running when a plugin fails to initialize. Without this guard, players would then join
 * the default server without passing any authentication pipeline. The guard depends only on classes the
 * proxy provides, so it also works when the runtime libraries or the configuration cannot be loaded.
 */
public class VelocityStartupGuard {
	/**
	 * The client's own text for unavailable authentication, shown in each player's language. Identica's messages
	 * cannot be used here: the configuration may be what failed to load.
	 */
	static final String REASON_KEY = "multiplayer.disconnect.authservers_down";
	private static final Component REASON = Component.translatable(REASON_KEY);

	private volatile boolean ready;

	/**
	 * Lets connections through to the regular listeners. Called once startup has completed.
	 */
	public void open() {
		ready = true;
	}

	@Subscribe(priority = Short.MAX_VALUE)
	public void onPreLogin(PreLoginEvent event) {
		if (ready) return;
		event.setResult(PreLoginEvent.PreLoginComponentResult.denied(REASON));
	}

	// Runs last, so a listener that allowed the connection again cannot let it through.
	@Subscribe(priority = Short.MIN_VALUE)
	public void onLogin(LoginEvent event) {
		if (ready) return;
		event.setResult(ResultedEvent.ComponentResult.denied(REASON));
	}
}

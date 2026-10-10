package me.whereareiam.identica.platform.bungeecord;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BungeeCord Startup Guard")
class BungeeCordStartupGuardTest {
	private final BungeeCordStartupGuard guard = new BungeeCordStartupGuard();
	private PluginManager pluginManager;

	@BeforeEach
	void setUp() {
		ProxyServer proxy = mock(ProxyServer.class);
		when(proxy.getLogger()).thenReturn(Logger.getLogger(getClass().getName()));
		pluginManager = new PluginManager(proxy);
		pluginManager.registerListener(mock(Plugin.class), guard);
	}

	@DisplayName("Cancels a connection before startup has completed")
	@Test
	void cancelsBeforeStartupCompleted() {
		PreLoginEvent preLogin = pluginManager.callEvent(preLogin());
		LoginEvent login = pluginManager.callEvent(login());

		assertTrue(preLogin.isCancelled());
		assertNotNull(preLogin.getReason());
		assertTrue(login.isCancelled());
		assertEquals(BungeeCordStartupGuard.REASON_KEY, ((TranslatableComponent) login.getReason()).getTranslate(),
				"the client translates the reason itself");
	}

	@DisplayName("Leaves connections to the regular listeners once startup has completed")
	@Test
	void allowsAfterStartupCompleted() {
		guard.open();

		assertFalse(pluginManager.callEvent(preLogin()).isCancelled());
		assertFalse(pluginManager.callEvent(login()).isCancelled());
	}

	private PreLoginEvent preLogin() {
		return new PreLoginEvent(mock(PendingConnection.class), (event, failure) -> {
		});
	}

	private LoginEvent login() {
		return new LoginEvent(mock(PendingConnection.class), (event, failure) -> {
		});
	}
}

package me.whereareiam.identica.platform.velocity;

import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("Velocity Startup Guard")
class VelocityStartupGuardTest {
	private final VelocityStartupGuard guard = new VelocityStartupGuard();

	@DisplayName("Denies a connection before startup has completed")
	@Test
	void deniesBeforeStartupCompleted() {
		PreLoginEvent preLogin = preLogin();
		LoginEvent login = login();

		guard.onPreLogin(preLogin);
		guard.onLogin(login);

		assertFalse(preLogin.getResult().isAllowed());
		assertTrue(preLogin.getResult().getReasonComponent().isPresent());
		assertFalse(login.getResult().isAllowed());
		assertEquals(Component.translatable(VelocityStartupGuard.REASON_KEY), login.getResult().getReasonComponent().orElseThrow(),
				"the client translates the reason itself");
	}

	@DisplayName("Leaves connections to the regular listeners once startup has completed")
	@Test
	void allowsAfterStartupCompleted() {
		PreLoginEvent preLogin = preLogin();
		LoginEvent login = login();

		guard.open();
		guard.onPreLogin(preLogin);
		guard.onLogin(login);

		assertTrue(preLogin.getResult().isAllowed());
		assertTrue(login.getResult().isAllowed());
	}

	private LoginEvent login() {
		return new LoginEvent(mock(Player.class), null);
	}

	private PreLoginEvent preLogin() {
		return new PreLoginEvent(mock(InboundConnection.class), "Player", null);
	}
}

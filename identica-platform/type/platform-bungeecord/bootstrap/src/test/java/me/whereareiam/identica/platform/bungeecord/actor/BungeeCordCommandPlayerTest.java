package me.whereareiam.identica.platform.bungeecord.actor;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("BungeeCord Command Player")
class BungeeCordCommandPlayerTest {
	private static final UUID ALICE = UUID.nameUUIDFromBytes("Alice".getBytes());

	private final ProxiedPlayer player = mock(ProxiedPlayer.class);
	private final Audience audience = mock(Audience.class);
	private final BungeeCordCommandPlayer actor = new BungeeCordCommandPlayer(ALICE, null, player, audience, "Alice", null);

	@DisplayName("Sends components directly, which the client shows once it can")
	@Test
	void sendsComponentsDirectly() {
		actor.sendMessage(Component.text("Welcome back"));

		verify(audience).sendMessage(Component.text("Welcome back"));
	}
}

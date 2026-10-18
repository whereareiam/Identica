package me.whereareiam.identica.platform.bungeecord.actor;

import me.whereareiam.identica.platform.common.checkpoint.LoadingScreenReplies;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BungeeCord Command Player")
class BungeeCordCommandPlayerTest {
	private static final UUID ALICE = UUID.nameUUIDFromBytes("Alice".getBytes());

	private final ProxiedPlayer player = mock(ProxiedPlayer.class);
	private final Audience audience = mock(Audience.class);
	private final LoadingScreenReplies replies = mock(LoadingScreenReplies.class);
	private final BungeeCordCommandPlayer actor = new BungeeCordCommandPlayer(ALICE, null, player, audience, "Alice", null, replies);

	@DisplayName("Defers a message text while BungeeCord holds the player on the loading screen")
	@Test
	void defersTextWhileHeld() {
		when(replies.defer(ALICE, "Wrong password")).thenReturn(true);

		actor.send("Wrong password", Map.of());

		verify(replies).defer(ALICE, "Wrong password");
		verify(audience, never()).sendMessage(any(Component.class));
	}

	@DisplayName("Sends components directly, which the client shows once it can")
	@Test
	void sendsComponentsDirectly() {
		actor.sendMessage(Component.text("Welcome back"));

		verify(audience).sendMessage(Component.text("Welcome back"));
	}
}

package me.whereareiam.identica.platform.velocity.actor;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Velocity Command Player")
class VelocityCommandPlayerTest {
	private static final UUID ALICE = UUID.nameUUIDFromBytes("Alice".getBytes());

	private final Player player = mock(Player.class);
	private final VelocityCommandPlayer actor = new VelocityCommandPlayer(ALICE, null, player, "Alice", null);

	@DisplayName("Sends components and titles directly, which the client shows once it can")
	@Test
	void sendsComponentsDirectly() {
		Title title = Title.title(Component.text("Log in"), Component.empty());

		actor.sendMessage(Component.text("Welcome back"));
		actor.sendTitle(title);

		verify(player).sendMessage(Component.text("Welcome back"));
		verify(player).showTitle(title);
	}
}

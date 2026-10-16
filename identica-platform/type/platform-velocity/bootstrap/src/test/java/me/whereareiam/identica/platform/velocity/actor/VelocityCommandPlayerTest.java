package me.whereareiam.identica.platform.velocity.actor;

import com.velocitypowered.api.proxy.Player;
import me.whereareiam.identica.checkpoint.Checkpoints;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Velocity Command Player")
class VelocityCommandPlayerTest {
	private static final UUID ALICE = UUID.nameUUIDFromBytes("Alice".getBytes());

	@DisplayName("Holds back chat and titles while a checkpoint holds the player, and sends them otherwise")
	@Test
	void holdsBackMessagesWhileHeld() {
		Player player = mock(Player.class);
		Checkpoints checkpoints = mock(Checkpoints.class);
		VelocityCommandPlayer actor = new VelocityCommandPlayer(ALICE, null, player, "Alice", null, checkpoints);
		Title title = Title.title(Component.text("Log in"), Component.empty());

		when(checkpoints.holds(ALICE)).thenReturn(true);
		actor.sendMessage(Component.text("Use /login"));
		actor.sendTitle(title);
		verify(player, never()).sendMessage(any(Component.class));
		verify(player, never()).showTitle(any());

		when(checkpoints.holds(ALICE)).thenReturn(false);
		actor.sendMessage(Component.text("Welcome back"));
		actor.sendTitle(title);
		verify(player).sendMessage(Component.text("Welcome back"));
		verify(player).showTitle(title);
	}
}

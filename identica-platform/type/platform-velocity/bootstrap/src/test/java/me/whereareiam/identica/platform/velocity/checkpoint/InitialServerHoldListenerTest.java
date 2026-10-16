package me.whereareiam.identica.platform.velocity.checkpoint;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.whereareiam.identica.checkpoint.Checkpoint;
import me.whereareiam.identica.checkpoint.Checkpoints;
import me.whereareiam.identica.checkpoint.HeldConnection;
import me.whereareiam.identica.platform.velocity.adapter.auth.VelocityResumeDecisionAdapter;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Initial Server Hold Listener")
class InitialServerHoldListenerTest {
	private static final UUID ALICE = UUID.nameUUIDFromBytes("Alice".getBytes());

	private final Checkpoints checkpoints = mock(Checkpoints.class);
	private final VelocityResumeDecisionAdapter resumeAdapter = mock(VelocityResumeDecisionAdapter.class);
	private final ProxyHoldKeepAlive keepAlive = mock(ProxyHoldKeepAlive.class);
	private final ScheduledTask alive = mock(ScheduledTask.class);
	private final Player player = mock(Player.class);
	private final InitialServerHoldListener listener = new InitialServerHoldListener(mock(ProxyServer.class), checkpoints, resumeAdapter, keepAlive);

	@DisplayName("Does not hold a player whose step waits on the step's server")
	@Test
	void holdsNoPlayerWithoutAProxyCheckpoint() {
		when(player.getUniqueId()).thenReturn(ALICE);
		when(checkpoints.selected(ALICE)).thenReturn(Optional.empty());
		assertNull(listener.executeAsync(event()));

		Checkpoint stepServer = checkpoint(CheckpointPlacement.STEP_SERVER);
		when(checkpoints.selected(ALICE)).thenReturn(Optional.of(stepServer));
		assertNull(listener.executeAsync(event()));

		verify(checkpoints, never()).arrive(any());
	}

	@DisplayName("Holds a player at a proxy checkpoint until Identica releases them, keeping the connection alive meanwhile")
	@Test
	void holdsUntilReleased() {
		hold();
		PlayerChooseInitialServerEvent event = event();
		when(player.isActive()).thenReturn(true);
		RegisteredServer lobby = mock(RegisteredServer.class);
		event.setInitialServer(lobby);

		EventTask task = listener.executeAsync(event);
		assertNotNull(task);
		HeldConnection arrived = arrived();
		assertEquals(ALICE, arrived.getConnectionUniqueId());
		verify(alive, never()).cancel();

		arrived.release("lobby");

		assertTrue(arrived.released().toCompletableFuture().isDone());
		verify(alive).cancel();
		assertEquals(Optional.of(lobby), event.getInitialServer());
	}

	@DisplayName("Chooses no server for a player who left while held")
	@Test
	void choosesNoServerForAPlayerWhoLeft() {
		hold();
		PlayerChooseInitialServerEvent event = event();
		event.setInitialServer(mock(RegisteredServer.class));
		when(player.isActive()).thenReturn(false);

		listener.executeAsync(event);
		listener.left(new DisconnectEvent(player, DisconnectEvent.LoginStatus.CANCELLED_BY_USER));

		assertTrue(arrived().released().toCompletableFuture().isDone());
		verify(alive).cancel();
		verify(resumeAdapter).forget(player);
		assertEquals(Optional.empty(), event.getInitialServer());
	}

	private void hold() {
		when(player.getUniqueId()).thenReturn(ALICE);
		Checkpoint loadingScreen = checkpoint(CheckpointPlacement.PROXY);
		when(checkpoints.selected(ALICE)).thenReturn(Optional.of(loadingScreen));
		when(keepAlive.keepAlive(player)).thenReturn(alive);
	}

	private HeldConnection arrived() {
		ArgumentCaptor<HeldConnection> captor = ArgumentCaptor.forClass(HeldConnection.class);
		verify(checkpoints).arrive(captor.capture());
		return captor.getValue();
	}

	private PlayerChooseInitialServerEvent event() {
		return new PlayerChooseInitialServerEvent(player, null);
	}

	private static Checkpoint checkpoint(CheckpointPlacement placement) {
		Checkpoint checkpoint = mock(Checkpoint.class);
		when(checkpoint.placement()).thenReturn(placement);
		when(checkpoint.id()).thenReturn("loading-screen");
		return checkpoint;
	}
}

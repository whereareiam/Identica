package me.whereareiam.identica.platform.velocity.checkpoint;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.whereareiam.identica.checkpoint.Checkpoint;
import me.whereareiam.identica.checkpoint.Checkpoints;
import me.whereareiam.identica.checkpoint.HeldConnection;
import me.whereareiam.identica.model.checkpoint.CheckpointSelection;
import me.whereareiam.identica.platform.common.checkpoint.LoadingScreenReplies;
import me.whereareiam.identica.platform.velocity.adapter.auth.VelocityResumeDecisionAdapter;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	private final VelocityCheckpointAdapter adapter = mock(VelocityCheckpointAdapter.class);
	private final ProxyHoldKeepAlive keepAlive = mock(ProxyHoldKeepAlive.class);
	private final ScheduledTask keepingAlive = mock(ScheduledTask.class);
	private final LoadingScreenReplies replies = new LoadingScreenReplies(mock(DeliveryService.class));
	private final Player player = mock(Player.class);
	private final InitialServerHoldListener listener =
			new InitialServerHoldListener(checkpoints, resumeAdapter, adapter, keepAlive, replies);

	@DisplayName("Does not hold a player whose step waits on the step's server")
	@Test
	void holdsNoPlayerWithoutALoadingScreenCheckpoint() {
		when(player.getUniqueId()).thenReturn(ALICE);
		when(checkpoints.selected(ALICE)).thenReturn(Optional.empty());
		assertNull(listener.executeAsync(event()));

		CheckpointSelection selection = selection(CheckpointPlacement.STEP_SERVER);
		when(checkpoints.selected(ALICE)).thenReturn(Optional.of(selection));
		assertNull(listener.executeAsync(event()));

		verify(checkpoints, never()).arrive(any());
	}

	@DisplayName("Holds a player on the loading screen and keeps them connected until Identica releases them")
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
		verify(keepAlive).keepAlive(player);
		assertTrue(replies.defer(ALICE, "Wrong password"));

		arrived.release("lobby");

		assertTrue(arrived.released().toCompletableFuture().isDone());
		assertEquals(Optional.of(lobby), event.getInitialServer());
		verify(keepingAlive).cancel();
		assertFalse(replies.defer(ALICE, "Welcome"));
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
		verify(resumeAdapter).forget(player);
		verify(adapter).left(ALICE);
		assertEquals(Optional.empty(), event.getInitialServer());
	}

	private void hold() {
		when(player.getUniqueId()).thenReturn(ALICE);
		CheckpointSelection selection = selection(CheckpointPlacement.LOADING_SCREEN);
		when(checkpoints.selected(ALICE)).thenReturn(Optional.of(selection));
		when(keepAlive.keepAlive(player)).thenReturn(keepingAlive);
	}

	private HeldConnection arrived() {
		ArgumentCaptor<HeldConnection> captor = ArgumentCaptor.forClass(HeldConnection.class);
		verify(checkpoints).arrive(captor.capture());
		return captor.getValue();
	}

	private PlayerChooseInitialServerEvent event() {
		return new PlayerChooseInitialServerEvent(player, null);
	}

	private static CheckpointSelection selection(CheckpointPlacement placement) {
		Checkpoint checkpoint = mock(Checkpoint.class);
		when(checkpoint.id()).thenReturn("loading-screen");
		return new CheckpointSelection(checkpoint, placement);
	}
}

package me.whereareiam.identica.platform.bungeecord.checkpoint;

import me.whereareiam.identica.checkpoint.Checkpoint;
import me.whereareiam.identica.checkpoint.Checkpoints;
import me.whereareiam.identica.checkpoint.HeldConnection;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.checkpoint.CheckpointSelection;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.platform.bungeecord.BungeeCordIdentica;
import me.whereareiam.identica.platform.bungeecord.adapter.auth.BungeeCordResumeDecisionAdapter;
import me.whereareiam.identica.platform.common.checkpoint.LoadingScreenReplies;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PlayerConfigurationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Configuration Hold Listener")
class ConfigurationHoldListenerTest {
	private static final UUID ALICE = UUID.nameUUIDFromBytes("Alice".getBytes());

	private final Checkpoints checkpoints = mock(Checkpoints.class);
	private final BungeeCordResumeDecisionAdapter resumeAdapter = mock(BungeeCordResumeDecisionAdapter.class);
	private final LoadingScreenReplies replies = new LoadingScreenReplies(mock(DeliveryService.class));
	private final ProxiedPlayer player = mock(ProxiedPlayer.class);
	private final AtomicBoolean configured = new AtomicBoolean();
	private final ConfigurationHoldListener listener =
			new ConfigurationHoldListener(mock(BungeeCordIdentica.class), checkpoints, resumeAdapter, replies);

	@DisplayName("Lets the configuration of a player whose step waits on the step's server finish")
	@Test
	void holdsNoPlayerWithoutALoadingScreenCheckpoint() {
		when(player.getUniqueId()).thenReturn(ALICE);
		CheckpointSelection selection = selection(CheckpointPlacement.STEP_SERVER);
		when(checkpoints.selected(ALICE)).thenReturn(Optional.of(selection));

		configure(PlayerConfigurationEvent.Reason.LOGIN);

		assertTrue(configured.get());
		verify(checkpoints, never()).arrive(any());
	}

	@DisplayName("Holds only the configuration of the first server")
	@Test
	void holdsOnlyTheFirstConfiguration() {
		hold("lobby");

		configure(PlayerConfigurationEvent.Reason.RECONFIGURE);

		assertTrue(configured.get());
		verify(checkpoints, never()).arrive(any());
	}

	@DisplayName("Holds the first configuration until the journey completes on that server")
	@Test
	void holdsUntilReleasedToTheSameServer() {
		hold("lobby");

		configure(PlayerConfigurationEvent.Reason.LOGIN);
		HeldConnection arrived = arrived();
		assertFalse(configured.get());
		assertTrue(replies.hold(alice(), message("Wrong password")));

		arrived.release("lobby");

		assertTrue(configured.get());
		assertTrue(arrived.released().toCompletableFuture().isDone());
		assertFalse(replies.hold(alice(), message("Welcome")));
	}

	@DisplayName("Lets the first configuration finish also when the journey completes on another server")
	@Test
	void finishesTheConfigurationWhenTheJourneyCompletesElsewhere() {
		hold("lobby");

		configure(PlayerConfigurationEvent.Reason.LOGIN);
		arrived().release("survival");

		assertTrue(configured.get());
	}

	@DisplayName("Ends the hold of a player who left while held")
	@Test
	void endsTheHoldOfAPlayerWhoLeft() {
		hold("lobby");

		configure(PlayerConfigurationEvent.Reason.LOGIN);
		listener.left(ALICE);

		assertTrue(arrived().released().toCompletableFuture().isDone());
		verify(resumeAdapter).forget(ALICE);
		assertTrue(configured.get());
	}

	private void hold(String server) {
		when(player.getUniqueId()).thenReturn(ALICE);
		CheckpointSelection selection = selection(CheckpointPlacement.LOADING_SCREEN);
		when(checkpoints.selected(ALICE)).thenReturn(Optional.of(selection));
		ServerInfo info = mock(ServerInfo.class);
		when(info.getName()).thenReturn(server);
		Server connected = mock(Server.class);
		when(connected.getInfo()).thenReturn(info);
		when(player.getServer()).thenReturn(connected);
	}

	private void configure(PlayerConfigurationEvent.Reason reason) {
		PlayerConfigurationEvent event = new PlayerConfigurationEvent(player, reason, (result, error) -> configured.set(true));
		listener.onEvent(event);
		event.postCall();
	}

	private HeldConnection arrived() {
		ArgumentCaptor<HeldConnection> captor = ArgumentCaptor.forClass(HeldConnection.class);
		verify(checkpoints).arrive(captor.capture());
		assertEquals(ALICE, captor.getValue().getConnectionUniqueId());
		return captor.getValue();
	}

	private static CheckpointSelection selection(CheckpointPlacement placement) {
		Checkpoint checkpoint = mock(Checkpoint.class);
		when(checkpoint.id()).thenReturn("loading-screen");
		return new CheckpointSelection(checkpoint, placement);
	}

	private static Identity alice() {
		Identity alice = mock(Identity.class);
		when(alice.getConnectionUniqueId()).thenReturn(ALICE);

		return alice;
	}

	private static PlayerMessage message(String text) {
		return PlayerMessage.builder().text(text).build();
	}
}

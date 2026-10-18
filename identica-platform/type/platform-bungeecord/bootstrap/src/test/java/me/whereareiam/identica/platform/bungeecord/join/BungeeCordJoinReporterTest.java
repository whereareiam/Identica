package me.whereareiam.identica.platform.bungeecord.join;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.model.scheduler.RunnableTask;
import me.whereareiam.identica.platform.bungeecord.actor.BungeeCordCommandPlayer;
import me.whereareiam.identica.platform.common.checkpoint.LoadingScreenReplies;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.identica.util.EventUtil;
import net.kyori.adventure.audience.Audience;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BungeeCord Join Reporter")
class BungeeCordJoinReporterTest {
	private final Scheduler scheduler = mock(Scheduler.class);
	private final EventManager eventManager = mock(EventManager.class);
	private final IdentityService identityService = mock(IdentityService.class);
	private final BungeeCordJoinReporter reporter = new BungeeCordJoinReporter(scheduler, identityService);
	private final ProxiedPlayer player = mock(ProxiedPlayer.class);
	private final BungeeCordCommandPlayer identity = identity(player);

	@BeforeEach
	void initialize() {
		EventUtil.initialize(eventManager);
		when(identityService.findByConnectionUniqueId(player.getUniqueId())).thenReturn(Optional.of(identity));
	}

	@Test
	@DisplayName("Reports the join from the scheduler once the current server hands the client its join")
	void reportsTheJoinFromTheScheduler() {
		currentServer("auth");

		reporter.joining(player, "auth");
		verify(eventManager, never()).call(any());

		ServerJoinedEvent joined = runScheduled();
		assertSame(identity, joined.getIdentity());
		assertEquals("auth", joined.getServer());
	}

	@Test
	@DisplayName("Reports a switch only once the new server hands the client its join")
	void reportsASwitchOnceTheNewServerHandsTheJoin() {
		currentServer("auth");
		reporter.joining(player, "auth");
		runScheduled();
		clearInvocations(scheduler, eventManager);

		currentServer("lobby");
		reporter.switched(player);
		verify(scheduler, never()).schedule(any(RunnableTask.class));

		reporter.joining(player, "lobby");
		assertEquals("lobby", runScheduled().getServer());
	}

	@Test
	@DisplayName("Reports a client without configuration once it switches to the server that handed it the join")
	void reportsAClientWithoutConfigurationOnTheSwitch() {
		reporter.joining(player, "auth");
		verify(scheduler, never()).schedule(any(RunnableTask.class));

		currentServer("auth");
		reporter.switched(player);

		assertEquals("auth", runScheduled().getServer());
	}

	@Test
	@DisplayName("Reports each join once")
	void reportsEachJoinOnce() {
		currentServer("auth");
		reporter.joining(player, "auth");

		reporter.switched(player);

		runScheduled();
	}

	@Test
	@DisplayName("Reports nothing for a connection without an attached identity")
	void reportsNothingWithoutAnIdentity() {
		when(identityService.findByConnectionUniqueId(player.getUniqueId())).thenReturn(Optional.empty());
		currentServer("auth");
		reporter.joining(player, "auth");

		ArgumentCaptor<RunnableTask> task = ArgumentCaptor.forClass(RunnableTask.class);
		verify(scheduler).schedule(task.capture());
		task.getValue().getRunnable().run();

		verify(eventManager, never()).call(any());
	}

	@Test
	@DisplayName("Forgets a disconnected player")
	void forgetsADisconnectedPlayer() {
		reporter.joining(player, "auth");

		reporter.clear(player.getUniqueId());
		currentServer("auth");
		reporter.switched(player);

		verify(scheduler, never()).schedule(any(RunnableTask.class));
	}

	private ServerJoinedEvent runScheduled() {
		ArgumentCaptor<RunnableTask> task = ArgumentCaptor.forClass(RunnableTask.class);
		verify(scheduler).schedule(task.capture());
		task.getValue().getRunnable().run();

		ArgumentCaptor<ServerJoinedEvent> event = ArgumentCaptor.forClass(ServerJoinedEvent.class);
		verify(eventManager).call(event.capture());
		List<ServerJoinedEvent> events = event.getAllValues();
		assertEquals(1, events.size());
		return events.getFirst();
	}

	private void currentServer(String name) {
		ServerInfo info = mock(ServerInfo.class);
		when(info.getName()).thenReturn(name);
		Server server = mock(Server.class);
		when(server.getInfo()).thenReturn(info);
		when(player.getServer()).thenReturn(server);
	}

	private static BungeeCordCommandPlayer identity(ProxiedPlayer player) {
		UUID uniqueId = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(uniqueId);
		when(player.getName()).thenReturn("Alice");
		when(player.getSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 41294));
		return new BungeeCordCommandPlayer(player, mock(Audience.class), mock(LoadingScreenReplies.class));
	}
}

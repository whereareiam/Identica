package me.whereareiam.identica.platform.bungeecord.join;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.model.scheduler.JobKey;
import me.whereareiam.identica.model.scheduler.Origin;
import me.whereareiam.identica.model.scheduler.Purpose;
import me.whereareiam.identica.model.scheduler.RunnableTask;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.identica.util.EventUtil;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fires {@link ServerJoinedEvent} once a player's client is in the world of its current server, on the first join and
 * after every switch.
 *
 * <p>No BungeeCord event fires after the client joined the world. BungeeCord fires {@code ServerConnectedEvent} while
 * it hands a server's join to the client, and writes that join packet on the player's network thread in the same step,
 * right after the event. So the event is fired from Identica's scheduler once that server's
 * {@code ServerConnectedEvent} fired and the server is the player's current one: anything a listener sends then is
 * written on the player's network thread after the join packet. A client before Minecraft 1.20.2 has no configuration,
 * so BungeeCord hands it the join before it switches the player's current server; {@link #switched} covers that.</p>
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BungeeCordJoinReporter {
	private static final Origin ORIGIN = Origin.core(BungeeCordJoinReporter.class);
	private static final Purpose PURPOSE = Purpose.of("server-joined");

	private final Scheduler scheduler;
	private final IdentityService identityService;
	private final Map<UUID, String> joiningByConnection = new ConcurrentHashMap<>();

	/**
	 * Records that BungeeCord hands the join of a server to the player, from the server's {@code ServerConnectedEvent}.
	 *
	 * @param player player the server accepted
	 * @param server name of the server
	 */
	public void joining(@NotNull ProxiedPlayer player, @NotNull String server) {
		joiningByConnection.put(player.getUniqueId(), server);
		release(player);
	}

	/**
	 * Rechecks a player whose current server changed, from {@code ServerSwitchEvent}.
	 *
	 * @param player player whose current server changed
	 */
	public void switched(@NotNull ProxiedPlayer player) {
		release(player);
	}

	/**
	 * Forgets a player that left the proxy.
	 *
	 * @param connectionUniqueId live connection UUID
	 */
	public void clear(@NotNull UUID connectionUniqueId) {
		scheduler.cancel(jobKey(connectionUniqueId));
		joiningByConnection.remove(connectionUniqueId);
	}

	private void release(@NotNull ProxiedPlayer player) {
		UUID connectionUniqueId = player.getUniqueId();
		Server current = player.getServer();
		if (current == null) return;

		String server = current.getInfo().getName();
		if (!joiningByConnection.remove(connectionUniqueId, server)) return;

		scheduler.schedule(RunnableTask.builder()
				.key(jobKey(connectionUniqueId))
				.runnable(() -> identityService.findByConnectionUniqueId(connectionUniqueId)
						.ifPresent(identity -> EventUtil.callEvent(new ServerJoinedEvent(identity, server))))
				.build());
	}

	private JobKey jobKey(@NotNull UUID connectionUniqueId) {
		return JobKey.of(ORIGIN, PURPOSE, connectionUniqueId.toString());
	}
}

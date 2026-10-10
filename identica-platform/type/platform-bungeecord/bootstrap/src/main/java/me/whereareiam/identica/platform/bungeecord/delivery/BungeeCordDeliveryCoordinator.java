package me.whereareiam.identica.platform.bungeecord.delivery;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.delivery.DeliveryCheckpointReachedEvent;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.scheduler.JobKey;
import me.whereareiam.identica.model.scheduler.Origin;
import me.whereareiam.identica.model.scheduler.PeriodicalRunnableTask;
import me.whereareiam.identica.model.scheduler.Purpose;
import me.whereareiam.identica.platform.bungeecord.actor.BungeeCordCommandPlayer;
import me.whereareiam.identica.service.PlatformDeliveryAdapter;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.identica.type.messaging.DeliveryCheckpoint;
import me.whereareiam.identica.util.EventUtil;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BungeeCordDeliveryCoordinator implements PlatformDeliveryAdapter {
	private static final Origin ORIGIN = Origin.core(BungeeCordDeliveryCoordinator.class);
	private static final Purpose PURPOSE = Purpose.of("initial-ready");
	private static final long INITIAL_DELAY_MILLIS = 100L;
	private static final long PERIOD_MILLIS = 100L;

	private final Scheduler scheduler;
	private final Map<UUID, Integer> initialPingByConnection = new ConcurrentHashMap<>();
	private final Map<UUID, String> currentServerByConnection = new ConcurrentHashMap<>();
	private final Map<UUID, Identity> identityByConnection = new ConcurrentHashMap<>();

	@Override
	public @NotNull DeliveryCheckpoint initialPromptCheckpoint() {
		return DeliveryCheckpoint.PLATFORM_READY_INITIAL;
	}

	@Override
	public void armInitialReady(@NotNull Identity identity, @Nullable String currentServer) {
		if (!(identity instanceof BungeeCordCommandPlayer playerIdentity)) {
			Logger.warn("Bungee messaging adapter received unsupported identity type=%s",
					identity.getClass().getName());
			return;
		}

		ProxiedPlayer player = playerIdentity.getSource();
		UUID connectionUniqueId = identity.getConnectionUniqueId();
		if (connectionUniqueId == null || player == null) return;

		initialPingByConnection.put(connectionUniqueId, player.getPing());
		currentServerByConnection.put(connectionUniqueId, currentServer == null ? "" : currentServer);
		identityByConnection.put(connectionUniqueId, identity);

		scheduler.cancel(jobKey(connectionUniqueId));
		scheduler.schedule(PeriodicalRunnableTask.builder()
				.key(jobKey(connectionUniqueId))
				.delay(INITIAL_DELAY_MILLIS)
				.period(PERIOD_MILLIS)
				.runnable(() -> poll(player, connectionUniqueId))
				.build());
	}

	@Override
	public void clear(@NotNull UUID connectionUniqueId) {
		scheduler.cancel(jobKey(connectionUniqueId));
		initialPingByConnection.remove(connectionUniqueId);
		currentServerByConnection.remove(connectionUniqueId);
		identityByConnection.remove(connectionUniqueId);
	}

	private void poll(@NotNull ProxiedPlayer player, @NotNull UUID connectionUniqueId) {
		Integer initialPing = initialPingByConnection.get(connectionUniqueId);
		if (initialPing == null) {
			scheduler.cancel(jobKey(connectionUniqueId));
			return;
		}

		int currentPing = player.getPing();
		if (currentPing == initialPing) return;

		Identity identity = identityByConnection.get(connectionUniqueId);
		String currentServer = currentServerByConnection.getOrDefault(connectionUniqueId, "");
		clear(connectionUniqueId);
		if (identity != null)
			EventUtil.callEvent(new DeliveryCheckpointReachedEvent(
					DeliveryCheckpoint.PLATFORM_READY_INITIAL,
					identity,
					currentServer
			));
	}

	private JobKey jobKey(@NotNull UUID connectionUniqueId) {
		return JobKey.of(ORIGIN, PURPOSE, connectionUniqueId.toString());
	}
}

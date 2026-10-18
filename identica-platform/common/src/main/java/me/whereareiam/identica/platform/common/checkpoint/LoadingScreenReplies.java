package me.whereareiam.identica.platform.common.checkpoint;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.delivery.DeliveryPayload;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.model.delivery.DeliveryTarget;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.messaging.DeliverySemantics;
import me.whereareiam.identica.type.messaging.DeliverySource;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The replies to players a platform holds on the loading screen, where the client shows no chat. While a player is
 * held, a reply waits as a delivery, which Identica sends once the player's client joined a server's world.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LoadingScreenReplies {
	private final DeliveryService deliveryService;
	private final Set<UUID> held = ConcurrentHashMap.newKeySet();

	/**
	 * Starts deferring the replies to a player the platform now holds on the loading screen.
	 *
	 * @param connectionUniqueId the held player's connection
	 */
	public void held(@NotNull UUID connectionUniqueId) {
		held.add(connectionUniqueId);
	}

	/**
	 * Stops deferring the replies to a player whose hold ended.
	 *
	 * @param connectionUniqueId the player's connection
	 */
	public void released(@NotNull UUID connectionUniqueId) {
		held.remove(connectionUniqueId);
	}

	/**
	 * Defers a reply to a held player until their client joined a server's world.
	 *
	 * @param connectionUniqueId the player's connection
	 * @param message            the rendered message text
	 * @return whether the player is held, so that the reply waits; a player who is not held is sent it directly
	 */
	public boolean defer(@NotNull UUID connectionUniqueId, @NotNull String message) {
		if (!held.contains(connectionUniqueId)) return false;

		long now = System.currentTimeMillis();
		deliveryService.queue(DeliveryRequest.builder()
				.id(UUID.randomUUID())
				.source(DeliverySource.NOTICE)
				.target(DeliveryTarget.builder()
						.connectionUniqueId(connectionUniqueId)
						.build())
				.payload(DeliveryPayload.builder()
						.chatMessage(message)
						.build())
				.semantics(DeliverySemantics.ONCE)
				.createdAt(now)
				.updatedAt(now)
				.build());

		return true;
	}
}

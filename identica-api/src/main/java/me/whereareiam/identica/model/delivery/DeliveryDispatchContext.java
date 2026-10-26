package me.whereareiam.identica.model.delivery;

import lombok.*;
import me.whereareiam.identica.identity.actor.Identity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The moment pending deliveries are dispatched for: a player's client joined a server's world, or Identica holds the
 * player on the loading screen.
 */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class DeliveryDispatchContext {
	/**
	 * Identity whose client joined the server's world, or that is held on the loading screen.
	 */
	private @NotNull Identity identity;
	/**
	 * Name of the server the client joined, or {@code null} on the loading screen, where deliveries that require a
	 * server keep waiting.
	 */
	private @Nullable String server;
}

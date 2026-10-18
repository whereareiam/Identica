package me.whereareiam.identica.model.delivery;

import lombok.*;
import me.whereareiam.identica.identity.actor.Identity;
import org.jetbrains.annotations.NotNull;

/**
 * The moment pending deliveries are dispatched for: a player's client joined a server's world.
 */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class DeliveryDispatchContext {
	/**
	 * Identity whose client joined the server's world.
	 */
	private @NotNull Identity identity;
	/**
	 * Name of the server the client joined.
	 */
	private @NotNull String server;
}

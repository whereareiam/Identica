package me.whereareiam.identica.platform.velocity.api.handshake;

import com.velocitypowered.api.event.connection.PreLoginEvent;
import me.whereareiam.identica.handshake.HandshakeContext;
import org.jetbrains.annotations.NotNull;

/**
 * Velocity-specific handshake context.
 */
public final class VelocityHandshakeContext implements HandshakeContext {
	private final @NotNull PreLoginEvent event;

	public VelocityHandshakeContext(@NotNull PreLoginEvent event) {
		this.event = event;
	}

	public @NotNull PreLoginEvent event() {
		return event;
	}
}

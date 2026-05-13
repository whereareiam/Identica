package me.whereareiam.identica.platform.bungeecord.api.handshake;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.handshake.HandshakeContext;
import net.md_5.bungee.api.event.PlayerHandshakeEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Handshake context exposed to BungeeCord-specific handshake appliers.
 *
 */
@Getter
@RequiredArgsConstructor
public final class BungeeCordHandshakeContext implements HandshakeContext {
	private final @NotNull PlayerHandshakeEvent event;
}

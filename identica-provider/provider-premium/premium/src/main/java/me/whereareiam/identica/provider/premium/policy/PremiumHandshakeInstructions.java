package me.whereareiam.identica.provider.premium.policy;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.handshake.HandshakeInstruction;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.provider.premium.handshake.PremiumHandshakeAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Queues the handshake instructions Premium gives a connection's next handshake.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PremiumHandshakeInstructions {
	private final @NotNull HandshakeStore handshakeStore;
	private final @NotNull Provider<Engine> engineProvider;

	/**
	 * Asks the next handshake of the username and IP for a premium login.
	 *
	 * @param username username of the connection
	 * @param ip IP of the connection
	 */
	public void forceOnline(@Nullable String username, @Nullable String ip) {
		if (username == null || username.isBlank() || ip == null || ip.isBlank()) return;

		long ttlMillis = engineProvider.get().getBehavior().handshakeInstructionTtlMillis();
		HandshakeInstruction instruction = HandshakeInstruction.create(new ConnectionIdentity(username, ip), ttlMillis);
		instruction.setAttribute(PremiumHandshakeAttributes.FORCE_ONLINE, true);
		handshakeStore.putInstruction(instruction);
		Logger.debug(
				"Premium handshake queued force-online instruction username=%s ip=%s ttl=%d",
				username,
				ip,
				ttlMillis
		);
	}
}

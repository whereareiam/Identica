package me.whereareiam.identica.provider.premium.platform.bungeecord.handshake;

import me.whereareiam.identica.handshake.HandshakeApplier;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.handshake.HandshakeInstruction;
import me.whereareiam.identica.platform.bungeecord.api.handshake.BungeeCordHandshakeContext;
import me.whereareiam.identica.provider.premium.handshake.PremiumHandshakeAttributes;
import org.jetbrains.annotations.NotNull;

public class PremiumHandshakeApplier implements HandshakeApplier<BungeeCordHandshakeContext> {
	@Override
	public void apply(@NotNull BungeeCordHandshakeContext context, @NotNull HandshakeInstruction instruction) {
		if (!instruction.getAttribute(PremiumHandshakeAttributes.FORCE_ONLINE).orElse(false))
			return;

		context.getEvent().getConnection().setOnlineMode(true);
		Logger.debug(
				"Premium Bungee handshake applied force-online username=%s",
				context.getEvent().getConnection().getName()
		);
	}
}

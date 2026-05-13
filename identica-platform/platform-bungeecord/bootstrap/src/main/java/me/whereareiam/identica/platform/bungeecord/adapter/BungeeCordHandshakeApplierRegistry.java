package me.whereareiam.identica.platform.bungeecord.adapter;

import com.google.inject.Singleton;
import me.whereareiam.identica.handshake.HandshakeApplier;
import me.whereareiam.identica.handshake.HandshakeApplierRegistry;
import me.whereareiam.identica.model.auth.handshake.HandshakeInstruction;
import me.whereareiam.identica.platform.bungeecord.api.handshake.BungeeCordHandshakeContext;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Singleton
public final class BungeeCordHandshakeApplierRegistry
		implements HandshakeApplierRegistry<BungeeCordHandshakeContext> {
	private final Set<HandshakeApplier<BungeeCordHandshakeContext>> appliers = new CopyOnWriteArraySet<>();

	@Override
	public void register(@NotNull HandshakeApplier<BungeeCordHandshakeContext> applier) {
		appliers.add(applier);
	}

	@Override
	public void unregister(@NotNull HandshakeApplier<BungeeCordHandshakeContext> applier) {
		appliers.remove(applier);
	}

	@Override
	public void applyAll(
			@NotNull BungeeCordHandshakeContext context,
			@NotNull HandshakeInstruction instruction
	) {
		for (HandshakeApplier<BungeeCordHandshakeContext> applier : appliers)
			applier.apply(context, instruction);
	}
}

package me.whereareiam.identica.platform.bungeecord.adapter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.handshake.HandshakeApplier;
import me.whereareiam.identica.model.auth.handshake.HandshakeInstruction;
import me.whereareiam.identica.platform.adapter.PlatformHandshakeApplierContributor;
import me.whereareiam.identica.platform.adapter.PlatformHandshakeApplierRegistry;
import me.whereareiam.identica.platform.bungeecord.api.handshake.BungeeCordHandshakeContext;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class BungeeCordPlatformHandshakeApplierRegistry implements PlatformHandshakeApplierRegistry<BungeeCordHandshakeContext> {
	private final Set<PlatformHandshakeApplierContributor<BungeeCordHandshakeContext>> contributors;
	private final Set<HandshakeApplier<BungeeCordHandshakeContext>> dynamicAppliers = new CopyOnWriteArraySet<>();

	@Override
	public void register(@NotNull HandshakeApplier<BungeeCordHandshakeContext> applier) {
		dynamicAppliers.add(applier);
	}

	@Override
	public void unregister(@NotNull HandshakeApplier<BungeeCordHandshakeContext> applier) {
		dynamicAppliers.remove(applier);
	}

	@Override
	public void applyAll(
			@NotNull BungeeCordHandshakeContext context,
			@NotNull HandshakeInstruction instruction
	) {
		for (PlatformHandshakeApplierContributor<BungeeCordHandshakeContext> contributor : contributors) {
			if (!contributor.supports(instruction)) continue;

			contributor.apply(context, instruction);
		}

		for (HandshakeApplier<BungeeCordHandshakeContext> applier : dynamicAppliers)
			applier.apply(context, instruction);
	}
}

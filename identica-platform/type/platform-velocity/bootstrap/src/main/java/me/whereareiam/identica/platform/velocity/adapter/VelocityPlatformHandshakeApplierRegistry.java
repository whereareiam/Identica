package me.whereareiam.identica.platform.velocity.adapter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.handshake.HandshakeApplier;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.handshake.HandshakeInstruction;
import me.whereareiam.identica.platform.adapter.PlatformHandshakeApplierContributor;
import me.whereareiam.identica.platform.adapter.PlatformHandshakeApplierRegistry;
import me.whereareiam.identica.platform.velocity.api.handshake.VelocityHandshakeContext;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class VelocityPlatformHandshakeApplierRegistry implements PlatformHandshakeApplierRegistry<VelocityHandshakeContext> {
	private final Set<PlatformHandshakeApplierContributor<VelocityHandshakeContext>> contributors;
	private final Set<HandshakeApplier<VelocityHandshakeContext>> dynamicAppliers = new CopyOnWriteArraySet<>();

	@Override
	public void register(@NotNull HandshakeApplier<VelocityHandshakeContext> applier) {
		dynamicAppliers.add(applier);
	}

	@Override
	public void unregister(@NotNull HandshakeApplier<VelocityHandshakeContext> applier) {
		dynamicAppliers.remove(applier);
	}

	@Override
	public void applyAll(@NotNull VelocityHandshakeContext context, @NotNull HandshakeInstruction instruction) {
		for (PlatformHandshakeApplierContributor<VelocityHandshakeContext> contributor : contributors) {
			if (!contributor.supports(instruction)) continue;

			try {
				contributor.apply(context, instruction);
			} catch (Exception e) {
				Logger.warn("Handshake instruction applier failed: %s", e.getMessage());
			}
		}

		for (HandshakeApplier<VelocityHandshakeContext> applier : dynamicAppliers) {
			try {
				applier.apply(context, instruction);
			} catch (Exception e) {
				Logger.warn("Handshake instruction applier failed: %s", e.getMessage());
			}
		}
	}
}

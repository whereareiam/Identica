package me.whereareiam.identica.provider.premium.platform.bungeecord.handshake;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.provider.state.ProviderDisabledEvent;
import me.whereareiam.identica.event.provider.state.ProviderEnabledEvent;
import me.whereareiam.identica.handshake.HandshakeApplierRegistry;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.platform.bungeecord.api.handshake.BungeeCordHandshakeContext;
import me.whereareiam.identica.provider.premium.PremiumConstants;
import org.jetbrains.annotations.NotNull;

@Singleton
public class PremiumBungeeCordHandshakeApplierLifecycle implements EventListener {
	private final HandshakeApplierRegistry<BungeeCordHandshakeContext> applierRegistry;
	private final PremiumHandshakeApplier applier;

	@Inject
	public PremiumBungeeCordHandshakeApplierLifecycle(
			EventManager eventManager,
			HandshakeApplierRegistry<BungeeCordHandshakeContext> applierRegistry,
			PremiumHandshakeApplier applier
	) {
		this.applierRegistry = applierRegistry;
		this.applier = applier;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onProviderEnabled(@NotNull ProviderEnabledEvent event) {
		if (isPremium(event.getProvider()))
			applierRegistry.register(applier);
	}

	@IdenticEvent
	public void onProviderDisabled(@NotNull ProviderDisabledEvent event) {
		if (isPremium(event.getProvider()))
			applierRegistry.unregister(applier);
	}

	private boolean isPremium(InternalProvider provider) {
		if (provider == null || provider.getDescriptor() == null)
			return false;

		return provider.getDescriptor().getId().equalsIgnoreCase(PremiumConstants.PROVIDER_ID);
	}
}

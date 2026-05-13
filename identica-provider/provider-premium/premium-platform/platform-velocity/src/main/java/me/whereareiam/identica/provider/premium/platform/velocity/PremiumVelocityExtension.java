package me.whereareiam.identica.provider.premium.platform.velocity;

import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.listener.DynamicListenerRegistry;
import me.whereareiam.identica.provider.ProviderPlatformExtension;
import me.whereareiam.identica.provider.premium.platform.velocity.listener.connection.PremiumGameProfileRequestListener;
import me.whereareiam.identica.type.PlatformType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Singleton
@NoArgsConstructor(force = true)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class PremiumVelocityExtension implements ProviderPlatformExtension {
	private final DynamicListenerRegistry listenerRegistry;
	private final PremiumGameProfileRequestListener gameProfileRequestListener;

	@Override
	public @NotNull PlatformType platform() {
		return PlatformType.VELOCITY;
	}

	@Override
	public @NotNull List<Module> modules() {
		return List.of(new PremiumVelocityModule());
	}

	@Override
	public void onEnable() {
		listenerRegistry.register(gameProfileRequestListener);
	}
}

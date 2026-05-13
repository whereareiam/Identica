package me.whereareiam.identica.provider.premium.platform.bungeecord;

import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.listener.DynamicListenerRegistry;
import me.whereareiam.identica.provider.ProviderPlatformExtension;
import me.whereareiam.identica.provider.premium.platform.bungeecord.listener.connection.PremiumPostLoginListener;
import me.whereareiam.identica.type.PlatformType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Singleton
@NoArgsConstructor(force = true)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class PremiumBungeeCordExtension implements ProviderPlatformExtension {
	private final DynamicListenerRegistry listenerRegistry;
	private final PremiumPostLoginListener postLoginListener;

	@Override
	public @NotNull PlatformType platform() {
		return PlatformType.BUNGEECORD;
	}

	@Override
	public @NotNull List<Module> modules() {
		return List.of(new PremiumBungeeCordModule());
	}

	@Override
	public void onEnable() {
		listenerRegistry.register(postLoginListener);
	}
}

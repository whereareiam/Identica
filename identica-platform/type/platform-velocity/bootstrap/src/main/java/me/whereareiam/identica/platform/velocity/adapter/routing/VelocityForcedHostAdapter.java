package me.whereareiam.identica.platform.velocity.adapter.routing;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.platform.adapter.PlatformForcedHostAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VelocityForcedHostAdapter implements PlatformForcedHostAdapter {
	private final ProxyServer proxyServer;

	@Override
	public @NotNull Optional<String> resolve(@NotNull ConnectionIdentity connection) {
		ConnectionIdentity.Origin origin = connection.getOrigin();
		if (origin == null) return Optional.empty();

		// Velocity keeps its forced hosts by lower-case hostname.
		List<String> servers = proxyServer.getConfiguration().getForcedHosts().get(origin.getHost().toLowerCase(Locale.ROOT));
		if (servers == null || servers.isEmpty()) return Optional.empty();

		return Optional.of(servers.get(0));
	}
}

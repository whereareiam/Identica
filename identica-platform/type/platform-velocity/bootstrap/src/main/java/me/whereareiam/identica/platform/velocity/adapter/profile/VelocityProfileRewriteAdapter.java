package me.whereareiam.identica.platform.velocity.adapter.profile;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.util.GameProfile;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.platform.common.decision.ProfileRewriteProcessor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.platform.common.adapter.PlatformProfileAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VelocityProfileRewriteAdapter implements PlatformProfileAdapter<GameProfileRequestEvent> {
	private final @NotNull ProfileRewriteProcessor processor;

	public void rewrite(@NotNull GameProfileRequestEvent event) {
		GameProfile current = event.getGameProfile();
		if (current == null) return;

		processor.process(request(event, current), target(event, current))
				.toCompletableFuture()
				.join();
	}

	private void applyOrigin(@NotNull ConnectionIdentity identity, @NotNull GameProfileRequestEvent event) {
		InetSocketAddress virtualHost = event.getConnection().getVirtualHost().orElse(null);
		if (virtualHost == null) return;

		identity.setOrigin(new ConnectionIdentity.Origin(
				virtualHost.getHostString(),
				virtualHost.getPort()
		));
	}

	private @NotNull ProfileRewriteProcessor.Request request(@NotNull GameProfileRequestEvent event, @NotNull GameProfile current) {
		ConnectionIdentity identity = new ConnectionIdentity(event.getUsername(), resolveIp(event));
		identity.setObservedUniqueId(current.getId());
		applyOrigin(identity, event);
		return new ProfileRewriteProcessor.Request(
				identity,
				current.getId(),
				current.getName()
		);
	}

	private @NotNull ProfileRewriteProcessor.Target target(@NotNull GameProfileRequestEvent event, @NotNull GameProfile current) {
		return rewrite -> {
			GameProfile rewritten = current;
			if (rewrite.uniqueId() != null && !rewrite.uniqueId().equals(rewritten.getId()))
				rewritten = rewritten.withId(rewrite.uniqueId());

			if (!rewrite.username().isBlank() && !rewrite.username().equals(rewritten.getName()))
				rewritten = rewritten.withName(rewrite.username());

			if (rewritten != current)
				event.setGameProfile(rewritten);
		};
	}

	private @Nullable String resolveIp(@NotNull GameProfileRequestEvent event) {
		if (event.getConnection().getRemoteAddress() == null) return null;
		if (event.getConnection().getRemoteAddress().getAddress() != null)
			return event.getConnection().getRemoteAddress().getAddress().getHostAddress();

		return event.getConnection().getRemoteAddress().getHostString();
	}

	@Override
	public void apply(@NotNull GameProfileRequestEvent event) {
		rewrite(event);
	}
}

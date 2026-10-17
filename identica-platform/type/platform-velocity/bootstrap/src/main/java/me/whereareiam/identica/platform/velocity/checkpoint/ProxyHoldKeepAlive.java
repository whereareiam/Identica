package me.whereareiam.identica.platform.velocity.checkpoint;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.player.CookieReceiveEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.platform.velocity.VelocityIdentica;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Keeps a player held on the proxy connected. With no server connected nobody sends the client keep-alives, so the
 * client sends nothing, and Velocity's read timeout closes the connection after 30 seconds. A cookie request makes the
 * client answer, which keeps the connection open; the proxy keeps the answers to itself.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProxyHoldKeepAlive {
	/**
	 * Key of the cookie a held player is asked for.
	 */
	static final Key COOKIE = Key.key("identica", "hold");
	/**
	 * How often a held player is asked, well within Velocity's read timeout.
	 */
	static final Duration INTERVAL = Duration.ofSeconds(10);

	private final ProxyServer proxy;
	private final VelocityIdentica plugin;

	/**
	 * Starts asking a held player for the cookie until the returned task is cancelled. A client before Minecraft
	 * 1.20.5 has no cookies and is not asked.
	 *
	 * @param player the held player
	 * @return the repeating task
	 */
	@NotNull ScheduledTask keepAlive(@NotNull Player player) {
		return proxy.getScheduler()
				.buildTask(plugin, () -> {
					if (player.isActive() && player.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5))
						player.requestCookie(COOKIE);
				})
				.delay(INTERVAL)
				.repeat(INTERVAL)
				.schedule();
	}

	/**
	 * Keeps the answers to the proxy's own requests from being forwarded to a server.
	 *
	 * @param event a cookie the client sent
	 */
	public void answered(@NotNull CookieReceiveEvent event) {
		if (COOKIE.equals(event.getOriginalKey())) event.setResult(CookieReceiveEvent.ForwardResult.handled());
	}
}

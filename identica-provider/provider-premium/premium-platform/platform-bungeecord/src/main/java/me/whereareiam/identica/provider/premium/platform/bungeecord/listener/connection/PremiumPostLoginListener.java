package me.whereareiam.identica.provider.premium.platform.bungeecord.listener.connection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.provider.ProviderAttemptStore;
import me.whereareiam.identica.provider.premium.PremiumConstants;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileStore;
import me.whereareiam.identica.util.UniqueIdGenerator;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PremiumPostLoginListener implements DynamicListener<PostLoginEvent> {
	private final PremiumProfileStore profileStore;
	private final ProviderAttemptStore attemptStore;

	@Override
	public void onEvent(PostLoginEvent event) {
		ProxiedPlayer player = event.getPlayer();
		if (player == null) return;

		UUID profileId = player.getUniqueId();
		if (profileId == null) return;

		String username = player.getName();
		if (username == null || username.isBlank()) return;
		String ip = resolveIp(player);

		UUID offlineUuid = UniqueIdGenerator.offlinePlayerUniqueId(username);
		if (offlineUuid != null && !profileId.equals(offlineUuid)) {
			attemptStore.clearAttempt(PremiumConstants.PROVIDER_ID, PremiumConstants.ATTEMPT_SCOPE_VERIFY, username, ip);
			Logger.debug(
					"Premium Bungee login cleared offline verify attempt username=%s ip=%s profile=%s",
					username,
					ip,
					profileId
			);
		}

		profileStore.save(username, profileId.toString());
		Logger.debug(
				"Premium Bungee login stored snapshot username=%s ip=%s profile=%s offline=%s",
				username,
				ip,
				profileId,
				offlineUuid
		);
	}

	private String resolveIp(ProxiedPlayer player) {
		SocketAddress address = player.getSocketAddress();
		if (!(address instanceof InetSocketAddress inetSocketAddress) || inetSocketAddress.getAddress() == null)
			return null;

		return inetSocketAddress.getAddress().getHostAddress();
	}
}

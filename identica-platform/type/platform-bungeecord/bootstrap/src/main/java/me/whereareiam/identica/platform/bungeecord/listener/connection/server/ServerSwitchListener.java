package me.whereareiam.identica.platform.bungeecord.listener.connection.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.connection.ServerSwitchingEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.platform.bungeecord.join.BungeeCordJoinReporter;
import me.whereareiam.identica.platform.common.adapter.PlatformResumeDecisionAdapter;
import me.whereareiam.identica.util.EventUtil;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerSwitchEvent;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ServerSwitchListener implements DynamicListener<ServerSwitchEvent> {
	private final PlatformResumeDecisionAdapter<ServerSwitchEvent> resumeDecisionAdapter;
	private final BungeeCordJoinReporter joinReporter;
	private final IdentityService identityService;

	@Override
	public void onEvent(ServerSwitchEvent event) {
		resumeDecisionAdapter.resume(event);
		ProxiedPlayer player = event.getPlayer();
		if (player == null) return;

		// A client from Minecraft 1.20.2 on starts configuring for the new server now, and leaves its world until the
		// join reporter fires ServerJoinedEvent.
		identityService.findByConnectionUniqueId(player.getUniqueId())
				.ifPresent(identity -> EventUtil.callEvent(new ServerSwitchingEvent(identity)));
		joinReporter.switched(player);
	}
}

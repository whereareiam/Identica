package me.whereareiam.identica.platform.bungeecord.listener.connection.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.platform.common.adapter.PlatformResumeDecisionAdapter;
import me.whereareiam.identica.service.PlatformDeliveryAdapter;
import net.md_5.bungee.api.event.ServerSwitchEvent;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ServerSwitchListener implements DynamicListener<ServerSwitchEvent> {
	private final PlatformResumeDecisionAdapter<ServerSwitchEvent> resumeDecisionAdapter;
	private final IdentityService identityService;
	private final PlatformDeliveryAdapter deliveryAdapter;

	@Override
	public void onEvent(ServerSwitchEvent event) {
		resumeDecisionAdapter.resume(event);
		if (event.getFrom() == null && event.getPlayer() != null && event.getPlayer().getServer() != null)
			identityService.findByConnectionUniqueId(event.getPlayer().getUniqueId())
					.ifPresent(identity -> deliveryAdapter.armInitialReady(
							identity,
							event.getPlayer().getServer().getInfo().getName()
					));
	}
}

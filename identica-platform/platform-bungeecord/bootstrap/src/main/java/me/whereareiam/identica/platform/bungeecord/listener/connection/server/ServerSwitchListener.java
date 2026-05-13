package me.whereareiam.identica.platform.bungeecord.listener.connection.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.platform.bungeecord.adapter.auth.BungeeCordResumeDecisionAdapter;
import net.md_5.bungee.api.event.ServerSwitchEvent;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ServerSwitchListener implements DynamicListener<ServerSwitchEvent> {
	private final BungeeCordResumeDecisionAdapter resumeDecisionAdapter;

	@Override
	public void onEvent(ServerSwitchEvent event) {
		resumeDecisionAdapter.resume(event);
	}
}

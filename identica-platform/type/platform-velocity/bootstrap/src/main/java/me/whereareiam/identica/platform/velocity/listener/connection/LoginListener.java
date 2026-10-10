package me.whereareiam.identica.platform.velocity.listener.connection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.connection.LoginEvent;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.platform.common.adapter.PlatformLoginDecisionAdapter;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LoginListener implements DynamicListener<LoginEvent> {
	private final PlatformLoginDecisionAdapter<LoginEvent> loginDecisionAdapter;

	@Override
	public void onEvent(LoginEvent event) {
		loginDecisionAdapter.process(event);
	}
}

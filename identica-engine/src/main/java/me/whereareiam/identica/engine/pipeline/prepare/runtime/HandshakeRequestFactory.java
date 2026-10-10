package me.whereareiam.identica.engine.pipeline.prepare.runtime;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.handshake.HandshakeRequest;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the request the handshake policies evaluate from what the prepare pipeline knows about a connection.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HandshakeRequestFactory {
	private final Provider<Engine> engineProvider;

	public @NotNull HandshakeRequest create(@NotNull ConnectionIdentity identity, @NotNull PrepareContextItem context) {
		return new HandshakeRequest(
				identity,
				context.getProvider(),
				context.getPreferredLink(),
				journeyMode(context)
		);
	}

	private @Nullable JourneyMode journeyMode(@NotNull PrepareContextItem context) {
		Engine engine = engineProvider.get();
		if (engine == null) return null;

		Engine.Scenarios scenarios = engine.getScenarios();
		Engine.Scenario scenario = context.getPreferredLink() != null
				? scenarios.getAuthentication()
				: scenarios.getRegistration();

		return scenario != null ? scenario.getJourneyMode() : null;
	}
}

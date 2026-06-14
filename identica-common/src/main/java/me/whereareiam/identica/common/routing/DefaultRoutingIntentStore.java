package me.whereareiam.identica.common.routing;

import com.google.inject.Singleton;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptReport;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptState;
import me.whereareiam.identica.routing.RoutingIntentStore;
import me.whereareiam.identica.type.routing.RoutingIntentStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class DefaultRoutingIntentStore implements RoutingIntentStore {
	private final Map<UUID, RoutingIntent> intents = new ConcurrentHashMap<>();

	@Override
	public void put(@NotNull RoutingIntent intent) {
		intent.setUpdatedAt(System.currentTimeMillis());
		intents.put(intent.getConnectionUniqueId(), intent);
	}

	@Override
	public @NotNull Optional<RoutingIntent> peek(@NotNull UUID connectionUniqueId) {
		return Optional.ofNullable(intents.get(connectionUniqueId));
	}

	@Override
	public @NotNull Optional<RoutingIntent> consume(@NotNull UUID connectionUniqueId) {
		return Optional.ofNullable(intents.remove(connectionUniqueId));
	}

	@Override
	public @NotNull Optional<RoutingIntent> markReached(@NotNull UUID connectionUniqueId, @NotNull String serverName) {
		RoutingIntent intent = intents.get(connectionUniqueId);
		if (intent == null) return Optional.empty();
		if (!intent.getEndpoint().getServer().equalsIgnoreCase(serverName)) return Optional.empty();

		intent.setStatus(RoutingIntentStatus.REACHED);
		intent.setUpdatedAt(System.currentTimeMillis());
		return Optional.of(intent);
	}

	@Override
	public @NotNull Optional<RoutingIntent> markExhausted(@NotNull UUID connectionUniqueId) {
		RoutingIntent intent = intents.get(connectionUniqueId);
		if (intent == null) return Optional.empty();

		intent.setStatus(RoutingIntentStatus.EXHAUSTED);
		intent.setUpdatedAt(System.currentTimeMillis());
		return Optional.of(intent);
	}

	@Override
	public @NotNull Optional<RoutingIntent> recordAttempt(@NotNull RoutingAttemptReport report) {
		RoutingIntent intent = intents.get(report.getConnectionUniqueId());
		if (intent == null) return Optional.empty();

		RoutingAttemptState state = intent.getAttemptState();
		state.setAttempts(state.getAttempts() + 1);
		state.setLastAttemptAt(System.currentTimeMillis());
		state.setLastAccepted(report.isAccepted());
		state.setLastServer(report.getServer());
		state.setLastFailure(report.getFailure());
		intent.setUpdatedAt(System.currentTimeMillis());
		return Optional.of(intent);
	}

	@Override
	public boolean clear(@NotNull UUID connectionUniqueId) {
		return intents.remove(connectionUniqueId) != null;
	}
}

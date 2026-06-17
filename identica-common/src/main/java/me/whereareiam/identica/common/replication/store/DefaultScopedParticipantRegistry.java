package me.whereareiam.identica.common.replication.store;

import com.google.inject.Singleton;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.replication.store.participant.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Singleton
public final class DefaultScopedParticipantRegistry {
	private final Map<Class<?>, Set<? extends BoundaryParticipant>> participantsByType = new ConcurrentHashMap<>();
	private final Map<Class<?>, Registry<?>> registriesByType = new ConcurrentHashMap<>();

	public @NotNull Registry<ConnectionDisconnectedParticipant> disconnected() {
		return registry(ConnectionDisconnectedParticipant.class);
	}

	public @NotNull Registry<ConnectionCompletedParticipant> completed() {
		return registry(ConnectionCompletedParticipant.class);
	}

	public @NotNull Registry<ConnectionTerminatedParticipant> terminated() {
		return registry(ConnectionTerminatedParticipant.class);
	}

	public @NotNull Registry<AccountLifecycleParticipant> accounts() {
		return registry(AccountLifecycleParticipant.class);
	}

	@SuppressWarnings("unchecked")
	private <T extends BoundaryParticipant> @NotNull Registry<T> registry(@NotNull Class<T> type) {
		return (Registry<T>) registriesByType.computeIfAbsent(type, ignored -> new TypedRegistry<>(type));
	}

	@SuppressWarnings("unchecked")
	private <T extends BoundaryParticipant> @NotNull Set<T> participants(@NotNull Class<T> type) {
		return (Set<T>) participantsByType.computeIfAbsent(type, ignored -> new CopyOnWriteArraySet<T>());
	}

	private <T extends BoundaryParticipant> @NotNull Set<T> ordered(@NotNull Class<T> type) {
		List<T> ordered = new ArrayList<>(participants(type));
		ordered.sort(Comparator.comparing(BoundaryParticipant::order));
		return Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
	}

	private final class TypedRegistry<T extends BoundaryParticipant> implements Registry<T> {
		private final Class<T> type;

		private TypedRegistry(@NotNull Class<T> type) {
			this.type = type;
		}

		@Override
		public void register(T value) {
			if (value == null) return;
			participants(type).add(value);
		}

		@Override
		public void unregister(T value) {
			if (value == null) return;
			participants(type).remove(value);
		}

		@Override
		public @NotNull Set<T> values() {
			return ordered(type);
		}
	}
}

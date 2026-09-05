package me.whereareiam.identica.feature.recognition.store;

import me.whereareiam.identica.feature.recognition.model.SessionRecognitionSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Stores ephemeral reconnect-recognition snapshots in the replication-backed
 * cache layer.
 */
public interface SessionRecognitionStore {
	/**
	 * Loads the latest stored recognition snapshot for a provider subject.
	 *
	 * @param providerId provider id
	 * @param providerSubject provider subject
	 * @return stored recognition snapshot or empty when none is available
	 */
	@NotNull Optional<SessionRecognitionSnapshot> find(
			@Nullable String providerId,
			@Nullable String providerSubject
	);

	/**
	 * Saves a recognition snapshot for later reconnect checks.
	 *
	 * @param snapshot recognition snapshot to store
	 */
	void save(@Nullable SessionRecognitionSnapshot snapshot);

	/**
	 * Clears a recognition snapshot for a provider subject.
	 *
	 * @param providerId provider id
	 * @param providerSubject provider subject
	 */
	void clear(
			@Nullable String providerId,
			@Nullable String providerSubject
	);
}

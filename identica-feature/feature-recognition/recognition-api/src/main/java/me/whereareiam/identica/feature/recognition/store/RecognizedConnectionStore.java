package me.whereareiam.identica.feature.recognition.store;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Stores whether a live authentication connection completed via recognition.
 *
 * <p>The state is scoped to a single connection attempt. A reconnect receives a
 * new connection UUID and must not inherit an earlier recognized outcome.</p>
 */
public interface RecognizedConnectionStore {
	/**
	 * Marks the supplied connection as recognized.
	 *
	 * @param connectionUniqueId recognized connection id
	 */
	void markRecognized(@NotNull UUID connectionUniqueId);

	/**
	 * Returns whether the supplied connection is currently marked recognized.
	 *
	 * @param connectionUniqueId connection id to check
	 * @return {@code true} when the connection is marked recognized
	 */
	boolean isRecognized(@NotNull UUID connectionUniqueId);

	/**
	 * Consumes and clears the recognized state for a connection.
	 *
	 * @param connectionUniqueId connection id to consume
	 * @return {@code true} when a recognized state was present
	 */
	boolean consumeRecognized(@NotNull UUID connectionUniqueId);
}

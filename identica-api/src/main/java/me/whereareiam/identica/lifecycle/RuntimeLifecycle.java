package me.whereareiam.identica.lifecycle;

import org.jetbrains.annotations.NotNull;

/**
 * A part of Identica that starts and stops with it. Each lifecycle belongs to a {@link StartupPhase}; Identica
 * starts the phases in order and, within a phase, the lifecycles in the order they were bound. It stops them in
 * the reverse order.
 * <p>
 * Building a lifecycle, like building any object Identica injects, does no I/O: documents, the database and other
 * stores are read when the lifecycle starts or later.
 */
public interface RuntimeLifecycle {
	/**
	 * Returns the phase this lifecycle starts in.
	 *
	 * @return the lifecycle's phase
	 */
	@NotNull StartupPhase phase();

	/**
	 * Starts this lifecycle once every lifecycle of the earlier phases has started. A failure stops Identica's
	 * startup: the lifecycles started before are stopped in reverse and the failure is passed on.
	 */
	void initialize();

	/**
	 * Releases registrations and resources, including those from partial initialization.
	 */
	void shutdown();
}

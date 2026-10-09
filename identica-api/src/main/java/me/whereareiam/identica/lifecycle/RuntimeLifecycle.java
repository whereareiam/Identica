package me.whereareiam.identica.lifecycle;

/** Lifecycle for a compiled domain subsystem that is not an operator-toggleable feature. */
public interface RuntimeLifecycle {
	/** Initializes services after core configuration and persistence are ready. */
	void initialize();

	/** Releases registrations and resources, including those from partial initialization. */
	void shutdown();
}

package me.whereareiam.identica.event.lifecycle;

import me.whereareiam.identica.event.base.SynchronousEvent;

/**
 * Event called during startup once the compiled features have started, the last lifecycle of
 * {@link me.whereareiam.identica.lifecycle.StartupPhase#FEATURES}. The configuration, the log, the database, the
 * core subsystems and the features are ready; the providers are not loaded yet.
 */
public class IdenticaBootstrappedEvent implements SynchronousEvent {
}

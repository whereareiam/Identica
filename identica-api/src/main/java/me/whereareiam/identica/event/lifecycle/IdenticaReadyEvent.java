package me.whereareiam.identica.event.lifecycle;

import me.whereareiam.identica.event.base.SynchronousEvent;

/**
 * Event called at the end of startup, once every {@link me.whereareiam.identica.lifecycle.StartupPhase} has
 * started: the providers are loaded with their commands and documents, the integrations run, and Identica handles
 * player connections.
 */
public class IdenticaReadyEvent implements SynchronousEvent {
}

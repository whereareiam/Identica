package me.whereareiam.identica.event.lifecycle;

import me.whereareiam.identica.event.base.SynchronousEvent;

/**
 * Event called when Identica is shutting down.
 * Listeners finish on the shutdown thread before feature classloaders are closed.
 * Providers and features stop before infrastructure such as the database.
 */
public class IdenticaShutdownEvent implements SynchronousEvent {
}

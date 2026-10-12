package me.whereareiam.identica.addon.dialogs.trigger;

import org.jetbrains.annotations.NotNull;

/**
 * Reports the moments a rule may show a dialog at, such as an Identica step waiting for the player's input. A rule
 * names its trigger by {@link #id()}; the addon starts a trigger when it is registered and stops it when it is
 * removed or the addon stops.
 *
 * <pre>{@code
 * public final class ServerRulesTrigger implements DialogTrigger {
 *     private TriggerSink sink;
 *     public String id() { return "server-rules"; }
 *     public void start(TriggerSink sink) { this.sink = sink; }
 *     public void stop() { sink = null; }
 *     void joined(UUID connectionUniqueId, String server) {
 *         sink.fire(TriggerContext.builder()
 *                 .connectionUniqueId(connectionUniqueId)
 *                 .trigger(id())
 *                 .attribute("server", server)
 *                 .build());
 *     }
 * }
 * }</pre>
 */
public interface DialogTrigger {
	/**
	 * Returns the ID rules name the trigger by, in their {@code on} field.
	 *
	 * @return the trigger's ID, such as {@code step-waiting}
	 */
	@NotNull String id();

	/**
	 * Starts reporting moments to the sink.
	 *
	 * @param sink where the trigger reports
	 */
	void start(@NotNull TriggerSink sink);

	/**
	 * Stops reporting moments.
	 */
	void stop();
}

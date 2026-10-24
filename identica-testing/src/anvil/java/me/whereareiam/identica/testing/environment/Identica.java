package me.whereareiam.identica.testing.environment;

import me.whereareiam.anvil.integration.junit.AnvilEnvironment;
import me.whereareiam.identica.type.pipeline.journey.JourneyChoice;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Consumer;

/**
 * Starts a fresh Identica network around a test: a proxy with the packaged plugin and providers, an
 * {@code auth} server for authentication and registration steps, and a {@code lobby} that completed players reach.
 * The proxy is new in every test; the two servers keep running and serve one test after another.
 * {@link #step()} and {@link #complete()} change where players are routed.
 *
 * <pre>{@code
 * @Test
 * @Identica(choice = JourneyChoice.PROMPTED, providers = Provider.CREDENTIAL)
 * void registers(ScenarioContext anvil) {
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@AnvilEnvironment(IdenticaScenarios.class)
public @interface Identica {
	/**
	 * Platform the proxy runs on. A BungeeCord proxy verifies premium logins against Mojang's own session server,
	 * so only offline players join it, and it forces no hostname to {@code lobby}.
	 */
	Platform platform() default Platform.VELOCITY;

	/**
	 * How registration picks the provider: with {@link JourneyChoice#PROMPTED} the player chooses whenever more than one
	 * provider can register them.
	 */
	JourneyChoice choice() default JourneyChoice.AUTOMATIC;

	/**
	 * Enabled providers; every other provider is installed but disabled.
	 */
	Provider[] providers() default {Provider.PREMIUM, Provider.CREDENTIAL};

	/**
	 * Whether enabled providers answer on their entrypoint hosts.
	 */
	boolean entrypoints() default false;

	/**
	 * Server players are sent to while a step waits for them, or blank to leave them on the server they are on.
	 * Players join {@code lobby}.
	 */
	String step() default IdenticaNetwork.AUTH;

	/**
	 * Server players are sent to once they are done, or blank to leave them on the server they are on.
	 */
	String complete() default IdenticaNetwork.LOBBY;

	/**
	 * Database the proxy stores its accounts in. A database server runs in a container that stops with the network,
	 * so such a test is skipped without Docker.
	 */
	Database database() default Database.SQLITE;

	/**
	 * Whether the proxy also runs the example addon, which holds back chat and commands until a player has
	 * authenticated and holds the players of {@link IdenticaNetwork#HOLD_HOST} and {@link IdenticaNetwork#ASK_HOST} at
	 * its checkpoints. On BungeeCord, the addon runs only its checkpoints.
	 */
	boolean addon() default false;

	/**
	 * Changes to the proxy's configuration files that the other attributes do not cover, applied in order. Each
	 * class needs a constructor without parameters.
	 *
	 * <pre>{@code
	 * static final class ShortRegistration implements Consumer<ProxyConfiguration> {
	 *     public void accept(ProxyConfiguration proxy) {
	 *         proxy.engine().getScenarios().getRegistration().setPipelineTtl(Duration.ofSeconds(5));
	 *     }
	 * }
	 * }</pre>
	 */
	Class<? extends Consumer<ProxyConfiguration>>[] configure() default {};
}

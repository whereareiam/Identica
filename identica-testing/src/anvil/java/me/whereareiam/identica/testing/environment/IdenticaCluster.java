package me.whereareiam.identica.testing.environment;

import me.whereareiam.anvil.integration.junit.AnvilEnvironment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Consumer;

/**
 * Starts a fresh network of two Identica proxies around a test. Both proxies share one database, the
 * {@code auth} and {@code lobby} servers, and, when replicating, one Redis. Credential is the only provider and
 * journeys are interactive. The proxies, the database and Redis are new in every test; the two servers keep
 * running and serve one test after another. The database and Redis run in containers, so the test is skipped
 * without Docker.
 *
 * <pre>{@code
 * @Test
 * @IdenticaCluster
 * void recognisesTheAccountOnTheOtherProxy(ScenarioContext anvil) {
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@AnvilEnvironment(IdenticaClusterScenarios.class)
public @interface IdenticaCluster {
	/**
	 * Whether the proxies replicate through Redis. Without it they only share the database.
	 */
	boolean replication() default true;

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
	 * Changes to both proxies' configuration files, applied in order to each proxy, as
	 * {@link Identica#configure()} does for a single proxy. Each class needs a constructor without parameters.
	 */
	Class<? extends Consumer<ProxyConfiguration>>[] configure() default {};
}

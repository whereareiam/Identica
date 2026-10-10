package me.whereareiam.identica.testing.environment;

import me.whereareiam.anvil.integration.junit.AnvilEnvironment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Starts a fresh network of two Identica proxies around a test. Both proxies share one database, the
 * {@code auth} and {@code lobby} servers, and, when replicating, one Redis. Credential is the only provider and
 * journeys are interactive. The database and Redis run in containers, so the test is skipped without Docker.
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
}

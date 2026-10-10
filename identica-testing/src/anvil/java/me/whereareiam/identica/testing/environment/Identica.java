package me.whereareiam.identica.testing.environment;

import me.whereareiam.anvil.integration.junit.AnvilEnvironment;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.JourneyPolicy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Starts a fresh Identica network around a test: a Velocity proxy with the packaged plugin and providers, an
 * {@code auth} server for authentication and registration steps, and a {@code lobby} that completed players reach.
 *
 * <pre>{@code
 * @Test
 * @Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
 * void registers(ScenarioContext anvil) {
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@AnvilEnvironment(IdenticaScenarios.class)
public @interface Identica {
	/**
	 * Journey mode of the authentication and registration scenarios.
	 */
	JourneyMode mode() default JourneyMode.SEAMLESS;

	/**
	 * Whether the mode may fall back to the other one.
	 */
	JourneyPolicy policy() default JourneyPolicy.PREFER;

	/**
	 * Enabled providers; every other official provider is installed but disabled.
	 */
	Provider[] providers() default {Provider.PREMIUM, Provider.CREDENTIAL};

	/**
	 * Whether enabled providers answer on their entrypoint hosts.
	 */
	boolean entrypoints() default false;

	/**
	 * Whether registration skips the provider choice when exactly one provider is eligible.
	 */
	boolean autoSelectSingleProvider() default false;
}

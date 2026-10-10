package me.whereareiam.identica.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.annotation.merge.Merge;
import me.whereareiam.configura.type.merge.MissingEntries;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Routing configuration model.
 */
@Getter
@Setter
@ToString
public class Routing {
	private @NotNull Defaults defaults = new Defaults();
	/**
	 * Scenario-specific routing targets keyed by scenario id.
	 * Supported ids: authentication, registration, migration.
	 */
	@Merge(missingEntries = MissingEntries.OMIT)
	private @NotNull Map<String, Targets> scenarios = new HashMap<>();

	/**
	 * Default routing targets.
	 */
	@Getter
	@Setter
	@ToString
	public static class Defaults {
		private @NotNull Target step = Target.step();
		private @NotNull Target complete = Target.complete();
	}

	/**
	 * Routing target settings.
	 */
	@Getter
	@Setter
	@ToString
	public static class Target {
		private @NotNull String target = "";
		private @Nullable RoutingAttemptPolicy attempts;

		/**
		 * Creates a step target with default step retry behavior.
		 *
		 * @return step target
		 */
		public static @NotNull Target step() {
			Target target = new Target();
			target.setAttempts(RoutingAttemptPolicy.defaultStep());
			return target;
		}

		/**
		 * Creates a completion target with default completion retry behavior.
		 *
		 * @return completion target
		 */
		public static @NotNull Target complete() {
			Target target = new Target();
			target.setAttempts(RoutingAttemptPolicy.defaultCompletion());
			return target;
		}
	}

	/**
	 * Routing targets by scenario phase.
	 */
	@Getter
	@Setter
	@ToString
	public static class Targets {
		private @NotNull Target step = new Target();
		/**
		 * Routing target used when a scenario journey fully completes.
		 */
		private @NotNull Target complete = new Target();
		private @NotNull Overrides overrides = new Overrides();

		/**
		 * Routing overrides.
		 */
		@Getter
		@Setter
		@ToString
		public static class Overrides {
			private @NotNull Map<String, Target> stages = new HashMap<>();
			private @NotNull Map<String, Target> steps = new HashMap<>();
		}
	}
}

package me.whereareiam.identica.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.ConfigDocument;
import me.whereareiam.identica.type.pipeline.PipelineConcurrencyPolicy;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.JourneyPolicy;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Engine configuration model.
 */
@Getter
@Setter
@ToString
public class Engine extends ConfigDocument {
	private @NotNull Behavior behavior = new Behavior();
	private @NotNull Scenarios scenarios = new Scenarios();

	/**
	 * Engine-wide behavior settings.
	 */
	@Getter
	@Setter
	@ToString
	public static class Behavior {
		/**
		 * Time-to-live for transient prepare/completion bridge entries.
		 */
		private @NotNull Duration bridgeTtl;
		/**
		 * Time-to-live for handshake instructions.
		 */
		private @NotNull Duration handshakeInstructionTtl;

		/**
		 * Returns bridge TTL in milliseconds with validation.
		 *
		 * @return bridge TTL in milliseconds
		 */
		public long bridgeTtlMillis() {
			if (bridgeTtl.isZero() || bridgeTtl.isNegative()) {
				throw new IllegalStateException("engine.behavior.bridgeTtl must be positive");
			}

			return bridgeTtl.toMillis();
		}

		/**
		 * Returns handshake instruction TTL in milliseconds with validation.
		 *
		 * @return handshake instruction TTL in milliseconds
		 */
		public long handshakeInstructionTtlMillis() {
			if (handshakeInstructionTtl.isZero() || handshakeInstructionTtl.isNegative()) {
				throw new IllegalStateException("engine.behavior.handshakeInstructionTtl must be positive");
			}

			return handshakeInstructionTtl.toMillis();
		}
	}

	/**
	 * Scenario-specific engine behavior settings.
	 */
	@Getter
	@Setter
	@ToString
	public static class Scenarios {
		private @NotNull Authentication authentication = new Authentication();
		private @NotNull Registration registration = new Registration();
		private @NotNull Migration migration = new Migration();
	}

	/**
	 * Shared scenario behavior settings.
	 */
	@Getter
	@Setter
	@ToString
	public static class Scenario {
		/**
		 * Time-to-live for pending pipeline state.
		 */
		private @NotNull Duration pipelineTtl;
		/**
		 * Time-to-live for advance locks.
		 */
		private @NotNull Duration advanceLockTtl;
		/**
		 * Whether resume requests are allowed for this scenario.
		 */
		private boolean allowResume;
		/**
		 * Preferred journey mode for this scenario.
		 */
		private @NotNull JourneyMode journeyMode;
		/**
		 * Policy for applying the preferred journey mode.
		 */
		private @NotNull JourneyPolicy journeyPolicy;

		/**
		 * Returns pipeline TTL in milliseconds with validation.
		 *
		 * @return pipeline TTL in milliseconds
		 */
		public long pipelineTtlMillis() {
			if (pipelineTtl.isZero() || pipelineTtl.isNegative()) {
				throw new IllegalStateException(pathPrefix() + ".pipelineTtl must be positive");
			}

			return pipelineTtl.toMillis();
		}

		/**
		 * Returns advance-lock TTL in milliseconds with validation.
		 *
		 * @return advance-lock TTL in milliseconds
		 */
		public long advanceLockTtlMillis() {
			if (advanceLockTtl.isZero() || advanceLockTtl.isNegative()) {
				throw new IllegalStateException(pathPrefix() + ".advanceLockTtl must be positive");
			}

			return advanceLockTtl.toMillis();
		}

		/**
		 * Returns the config path prefix used in validation messages.
		 *
		 * @return config path prefix
		 */
		protected @NotNull String pathPrefix() {
			return "engine.scenarios";
		}
	}

	/**
	 * Authentication engine settings.
	 */
	@Getter
	@Setter
	@ToString
	public static class Authentication extends Scenario {
		private @NotNull PipelineConcurrencyPolicy pipelineConcurrencyPolicy;

		@Override
		protected @NotNull String pathPrefix() {
			return "engine.scenarios.authentication";
		}
	}

	/**
	 * Registration engine settings.
	 */
	@Getter
	@Setter
	@ToString
	public static class Registration extends Scenario {
		/**
		 * Policy for concurrent in-flight pipelines for the same identity.
		 */
		private @NotNull PipelineConcurrencyPolicy pipelineConcurrencyPolicy;
		/**
		 * Whether interactive registration should auto-select the only available provider.
		 */
		private boolean autoSelectSingleProvider;

		@Override
		protected @NotNull String pathPrefix() {
			return "engine.scenarios.registration";
		}
	}

	/**
	 * Migration engine settings.
	 */
	@Getter
	@Setter
	@ToString
	public static class Migration extends Scenario {
		@Override
		protected @NotNull String pathPrefix() {
			return "engine.scenarios.migration";
		}
	}
}

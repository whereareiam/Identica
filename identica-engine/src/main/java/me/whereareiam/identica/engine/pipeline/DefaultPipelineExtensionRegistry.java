package me.whereareiam.identica.engine.pipeline;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.pipeline.journey.ProviderMethod;
import me.whereareiam.identica.model.pipeline.journey.stage.JourneyStage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.model.pipeline.phase.PhasePlacement;
import me.whereareiam.identica.pipeline.PipelineGroup;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.PipelineRegistry;
import me.whereareiam.identica.pipeline.extension.PipelineExtension;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionBuilder;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.pipeline.journey.registry.JourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.AuthenticationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.RegistrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class DefaultPipelineExtensionRegistry implements PipelineExtensionRegistry {
	private final PipelineRegistry authenticationRegistry;
	private final PipelineRegistry prepareRegistry;
	private final PipelineRegistry registrationRegistry;
	private final PipelineRegistry migrationRegistry;
	private final AuthenticationJourneyRegistry authenticationStageRegistry;
	private final RegistrationJourneyRegistry registrationStageRegistry;
	private final MigrationJourneyRegistry migrationStageRegistry;
	private final Map<String, RegisteredExtension> extensions = new ConcurrentHashMap<>();

	@Inject
	public DefaultPipelineExtensionRegistry(
			@Named("authenticationPipelineRegistry") PipelineRegistry authenticationRegistry,
			@Named("preparePipelineRegistry") PipelineRegistry prepareRegistry,
			@Named("registrationPipelineRegistry") PipelineRegistry registrationRegistry,
			@Named("migrationPipelineRegistry") PipelineRegistry migrationRegistry,
			AuthenticationJourneyRegistry authenticationStageRegistry,
			RegistrationJourneyRegistry registrationStageRegistry,
			MigrationJourneyRegistry migrationStageRegistry
	) {
		this.authenticationRegistry = authenticationRegistry;
		this.prepareRegistry = prepareRegistry;
		this.registrationRegistry = registrationRegistry;
		this.migrationRegistry = migrationRegistry;
		this.authenticationStageRegistry = authenticationStageRegistry;
		this.registrationStageRegistry = registrationStageRegistry;
		this.migrationStageRegistry = migrationStageRegistry;
	}

	@Override
	public synchronized void register(@NotNull PipelineExtension extension) {
		String extensionId = extension.id();
		if (extensionId.isBlank()) return;

		String key = extensionId.toLowerCase();
		RegisteredExtension existing = extensions.get(key);
		if (existing != null) {
			Logger.warn("Pipeline extension %s already registered, replacing with latest registration", extensionId);
			existing.rollback();
		}

		RegistrationJournal journal = new RegistrationJournal();
		extension.apply(journal);
		extensions.put(key, new RegisteredExtension(extension, journal.rollbackActions()));
	}

	@Override
	public synchronized boolean unregister(@NotNull String extensionId) {
		if (extensionId.isBlank()) return false;

		RegisteredExtension removed = extensions.remove(extensionId.toLowerCase());
		if (removed == null) return false;

		removed.rollback();
		return true;
	}

	@Override
	public @NotNull List<PipelineExtension> getAll() {
		List<PipelineExtension> snapshot = new ArrayList<>();
		for (RegisteredExtension extension : extensions.values()) {
			if (extension != null)
				snapshot.add(extension.extension());
		}

		snapshot.sort(Comparator.comparingInt(PipelineExtension::order)
				.thenComparing(PipelineExtension::id, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(snapshot);
	}

	private @NotNull PipelineRegistry resolveScenarioRegistry(@NotNull PipelineType pipelineType) {
		return switch (pipelineType) {
			case AUTHENTICATION -> authenticationRegistry;
			case REGISTRATION -> registrationRegistry;
			case MIGRATION -> migrationRegistry;
		};
	}

	private @NotNull JourneyRegistry resolveJourneyRegistry(@NotNull PipelineType pipelineType) {
		return switch (pipelineType) {
			case AUTHENTICATION -> authenticationStageRegistry;
			case REGISTRATION -> registrationStageRegistry;
			case MIGRATION -> migrationStageRegistry;
		};
	}

	private final class RegistrationJournal implements PipelineExtensionBuilder {
		private final List<Runnable> rollbackActions = new ArrayList<>();

		@Override
		public void registerPrepareGroup(@NotNull PipelineGroup<?> group) {
			prepareRegistry.register(group);
			rollbackActions.add(() -> prepareRegistry.unregister(group.id()));
		}

		@Override
		public void registerPreparePhase(
				@NotNull String groupId,
				@NotNull PipelinePhase<?> phase,
				@NotNull PhasePlacement placement
		) {
			prepareRegistry.registerPhase(groupId, phase, placement);
			rollbackActions.add(() -> prepareRegistry.unregisterPhase(groupId, phase.id()));
		}

		@Override
		public void registerScenarioGroup(@NotNull PipelineType pipelineType, @NotNull PipelineGroup<?> group) {
			PipelineRegistry registry = resolveScenarioRegistry(pipelineType);
			registry.register(group);
			rollbackActions.add(() -> registry.unregister(group.id()));
		}

		@Override
		public void registerScenarioPhase(
				@NotNull PipelineType pipelineType,
				@NotNull String groupId,
				@NotNull PipelinePhase<?> phase,
				@NotNull PhasePlacement placement
		) {
			PipelineRegistry registry = resolveScenarioRegistry(pipelineType);
			registry.registerPhase(groupId, phase, placement);
			rollbackActions.add(() -> registry.unregisterPhase(groupId, phase.id()));
		}

		@Override
		public void registerStage(@NotNull PipelineType pipelineType, @NotNull JourneyStage stage) {
			JourneyRegistry registry = resolveJourneyRegistry(pipelineType);
			registry.registerStage(stage);
			rollbackActions.add(() -> registry.unregisterStage(stage.getId()));
		}

		@Override
		public void registerMethod(@NotNull ProviderMethod method) {
			JourneyRegistry registry = resolveJourneyRegistry(method.getScenario());
			registry.registerMethod(method);
			rollbackActions.add(() -> registry.removeMethod(method.getProviderId(), method.getName()));
		}

		@Override
		public void registerStep(@NotNull JourneyStep step) {
			Set<PipelineType> targetTypes = step.getScenarios();
			if (targetTypes.isEmpty()) {
				Logger.warn("Journey step %s does not specify scenarios, skipping registration", step.getStep().getName());
				return;
			}

			for (PipelineType pipelineType : targetTypes) {
				JourneyRegistry registry = resolveJourneyRegistry(pipelineType);
				registry.registerStep(step.toBuilder()
						.scenarios(EnumSet.of(pipelineType))
						.build());

				recordStepRollback(registry, step.getStageId(), step.getProviderId(), pipelineType, step.getStep());
			}
		}


		@Override
		public void registerStep(
				@NotNull PipelineType pipelineType,
				@Nullable String providerId,
				@NotNull StageType stageType,
				@NotNull Step step
		) {
			JourneyRegistry registry = resolveJourneyRegistry(pipelineType);
			registry.registerStep(providerId, stageType, step.order(), pipelineType, step);
			recordStepRollback(registry, stageType.id(), providerId, pipelineType, step);
		}

		@Override
		public void registerStep(
				@Nullable String providerId,
				@NotNull StageType stageType,
				@NotNull Step step
		) {
			PipelineExtensionBuilder.super.registerStep(providerId, stageType, step);
		}

		private void recordStepRollback(
				@NotNull JourneyRegistry registry,
				@NotNull String stageId,
				@Nullable String providerId,
				@NotNull PipelineType pipelineType,
				@NotNull Step step
		) {
			String stepName = step.getName();
			if (stepName.isBlank())
				return;

			rollbackActions.add(() -> registry.removeStep(stageId, providerId, pipelineType, stepName));
		}

		private @NotNull List<Runnable> rollbackActions() {
			return List.copyOf(rollbackActions);
		}
	}

	private record RegisteredExtension(@NotNull PipelineExtension extension, @NotNull List<Runnable> rollbackActions) {
		private void rollback() {
			for (int index = rollbackActions.size() - 1; index >= 0; index--) {
				Runnable action = rollbackActions.get(index);
				if (action != null)
					action.run();
			}
		}
	}
}

package me.whereareiam.identica.engine.pipeline.scenario.type.migration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.identica.engine.pipeline.PipelineExecutor;
import me.whereareiam.identica.engine.pipeline.scenario.AbstractScenarioPipeline;
import me.whereareiam.identica.event.scenario.migration.MigrationRequiredEvent;
import me.whereareiam.identica.event.scenario.migration.MigrationResolvedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.pipeline.PipelineRegistry;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.pipeline.state.scenario.shared.item.IdentityMetaItem;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.util.EventUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
public class MigrationPipeline extends AbstractScenarioPipeline {
	@Inject
	public MigrationPipeline(
			@Named("migrationPipelineRegistry") PipelineRegistry registry,
			Provider<Messages> messagesProvider,
			Provider<Engine> engineProvider,
			PipelineStateStore pipelineStateStore,
			PipelineExecutor executor
	) {
		super(
				registry,
				messagesProvider,
				engineProvider,
				pipelineStateStore,
				PipelineType.MIGRATION,
				executor
		);
	}

	@Override
	protected @Nullable ScenarioContext buildContext(@NotNull ConnectionRequest request) {
		if (request.getIdentity().getAccountUniqueId() == null) {
			Logger.severe("%s request missing Identica UUID", type());
			return null;
		}

		MigrationContext context = MigrationContext.builder()
				.connectionUniqueId(request.getConnectionUniqueId())
				.identity(request.getIdentity())
				.intendedServer(request.getIntendedServer())
				.provider(request.getProvider())
				.selection(request.getSelection())
				.transition(request.getTransition())
				.build();

		emitScenarioBuilt(context);

		return context;
	}

	@Override
	protected @NotNull ScenarioContext mergeContext(@NotNull ScenarioContext base, @NotNull ResumeRequest request) {
		ConnectionIdentity identity = mergeIdentity(base, request);
		if (identity == null) return base;

		String intendedServer = request.getIntendedServer() != null
				? request.getIntendedServer()
				: base.getIntendedServer();

		if (base instanceof MigrationContext migration) {
			MigrationContext merged = MigrationContext.builder()
					.connectionUniqueId(resolveConnectionUniqueId(base, request))
					.identity(identity)
					.intendedServer(intendedServer)
					.targetProviderId(migration.getTargetProviderId())
					.build();

			merged.setProvider(migration.getProvider());
			merged.setSelection(migration.getSelection());
			merged.setTransition(migration.getTransition());
			Logger.debug(
					"Migration resume merged connection=%s identica=%s username=%s ip=%s target=%s provider=%s subject=%s",
					merged.getConnectionUniqueId(),
					merged.getAccountUniqueId(),
					merged.getUsername(),
					merged.getIp(),
					merged.getTargetProviderId(),
					merged.getProvider() != null ? merged.getProvider().getProviderId() : null,
					merged.getProvider() != null ? merged.getProvider().getProviderSubject() : null
			);
			return merged;
		}

		return base;
	}

	@Override
	public boolean isPending(@NotNull PipelineState state) {
		return state.has(JourneyStateItem.KEY);
	}

	@Override
	protected void onStart(@NotNull PipelineState pipelineState, boolean resumed) {
		pipelineState.put(IdentityMetaItem.KEY, new IdentityMetaItem(resumed));
	}

	@Override
	protected void emitRequired(@NotNull ScenarioContext context, long expiresAt, @Nullable JourneyMode journeyMode) {
		if (!(context instanceof MigrationContext migrationContext)) return;
		EventUtil.callEvent(new MigrationRequiredEvent(migrationContext, false, expiresAt, journeyMode));
	}

	@Override
	protected void emitResolved(@NotNull ScenarioContext context, @NotNull ScenarioResolution resolution, boolean sessionOpened) {
		if (!(context instanceof MigrationContext migrationContext)) return;
		EventUtil.callEvent(new MigrationResolvedEvent(migrationContext, resolution, sessionOpened));
	}
}

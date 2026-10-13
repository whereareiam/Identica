package me.whereareiam.identica.engine.pipeline.scenario.type.authentication;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.engine.pipeline.PipelineExecutor;
import me.whereareiam.identica.engine.pipeline.scenario.AbstractScenarioPipeline;
import me.whereareiam.identica.event.pipeline.scenario.authentication.AuthenticationContextBuiltEvent;
import me.whereareiam.identica.event.scenario.authentication.AuthenticationRequiredEvent;
import me.whereareiam.identica.event.scenario.authentication.AuthenticationResolvedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
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

import java.util.UUID;

@Singleton
public class AuthenticationPipeline extends AbstractScenarioPipeline {
	private final ProviderLinkPersistenceService providerLinkPersistenceService;

	@Inject
	public AuthenticationPipeline(
			@Named("authenticationPipelineRegistry") PipelineRegistry registry,
			Provider<Messages> messagesProvider,
			Provider<Engine> engineProvider,
			PipelineStateStore pipelineStateStore,
			PipelineExecutor executor,
			ProviderLinkPersistenceService providerLinkPersistenceService
	) {
		super(
				registry,
				messagesProvider,
				engineProvider,
				pipelineStateStore,
				PipelineType.AUTHENTICATION,
				executor
		);
		this.providerLinkPersistenceService = providerLinkPersistenceService;
	}

	@Override
	protected @Nullable ScenarioContext buildContext(@NotNull ConnectionRequest request) {
		if (request.getIdentity().getAccountUniqueId() == null) {
			Logger.severe("%s request missing Identica UUID", type());
			return null;
		}

		AuthContext context = AuthContext.builder()
				.connectionUniqueId(request.getConnectionUniqueId())
				.identity(request.getIdentity())
				.intendedServer(request.getIntendedServer())
				.build();
		context.setProvider(request.getProvider());
		context.setSelection(request.getSelection());
		context.setTransition(request.getTransition());

		EventUtil.callEvent(new AuthenticationContextBuiltEvent(context));
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

		if (base instanceof AuthContext authContext) {
			return AuthContext.builder()
					.connectionUniqueId(resolveConnectionUniqueId(base, request))
					.identity(identity)
					.intendedServer(intendedServer)
					.provider(authContext.getProvider())
					.selection(authContext.getSelection())
					.transition(authContext.getTransition())
					.build();
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
		if (!(context instanceof AuthContext authContext)) return;
		EventUtil.callEvent(new AuthenticationRequiredEvent(authContext, false, expiresAt, journeyMode));
	}

	@Override
	protected void emitResolved(@NotNull ScenarioContext context, @NotNull ScenarioResolution resolution, boolean sessionOpened) {
		if (!(context instanceof AuthContext authContext)) return;
		EventUtil.callEvent(new AuthenticationResolvedEvent(authContext, resolution, sessionOpened));
	}

	@Override
	public boolean matchesNewScenario(@Nullable ConnectionRequest request) {
		if (request == null) return false;
		UUID uniqueId = request.getIdentity().getAccountUniqueId();
		if (uniqueId == null) return false;

		return !providerLinkPersistenceService.findByUniqueId(uniqueId).isEmpty();
	}
}

package me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.authentication.group.policy.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.authentication.PolicyState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameHistoryPersistenceService;
import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameStatePersistenceService;
import me.whereareiam.identica.trait.authoritative.username.pipeline.phase.base.AbstractPersistUsernamePhase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
public class PersistAuthenticationUsernamePhase extends AbstractPersistUsernamePhase<PolicyState> {
	@Inject
	public PersistAuthenticationUsernamePhase(
			ProviderManager providerManager,
			AccountUsernameStatePersistenceService statePersistenceService,
			AccountPersistenceService accountPersistenceService,
			AccountUsernameHistoryPersistenceService historyPersistenceService,
			EventManager eventManager
	) {
		super(providerManager, statePersistenceService, accountPersistenceService, historyPersistenceService, eventManager);
	}

	@Override
	public @NotNull String id() {
		return "persist-authoritative-username";
	}

	@Override
	public int order() {
		return 200;
	}

	@Override
	public @NotNull Class<PolicyState> stateType() {
		return PolicyState.class;
	}

	@Override
	protected @Nullable ScenarioContext scenarioContext(@NotNull PipelineState pipelineState, @NotNull PolicyState state) {
		return pipelineState.getScenario(pipelineState.getPipelineType()) instanceof AuthContext authContext
				? authContext
				: null;
	}
}

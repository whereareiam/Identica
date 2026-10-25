package me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.session.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.session.phase.base.AbstractOpenSessionPhase;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.message.ScenarioMessages;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.pipeline.state.scenario.type.migration.SessionState;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

@Singleton
public class OpenSessionPhase extends AbstractOpenSessionPhase<MigrationContext, SessionState> {
	@Inject
	public OpenSessionPhase(
			SessionService sessionService,
			Provider<Messages> messagesProvider,
			EventManager eventManager
	) {
		super(sessionService, messagesProvider, eventManager);
	}

	@Override
	public @NotNull Class<SessionState> stateType() {
		return SessionState.class;
	}

	@Override
	protected @NotNull PipelineType pipelineType() {
		return PipelineType.MIGRATION;
	}

	@Override
	protected @NotNull PlayerMessage failedMessage(@NotNull Messages messages) {
		return ScenarioMessages.of(PipelineType.MIGRATION).getFailed().create(messages);
	}

	@Override
	protected MigrationContext resolveContext(@NotNull SessionState state) {
		return state.getContext();
	}

	@Override
	protected Session resolveSession(@NotNull SessionState state) {
		return state.getSession();
	}

}

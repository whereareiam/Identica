package me.whereareiam.identica.addon.dialogs.trigger;

import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.step.StepFinishedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.type.pipeline.journey.step.StepWaitReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code step-waiting} trigger: an Identica step waits for the player's input, such as a password. Its
 * attributes are the {@code scenario} (registration, authentication or migration), the {@code provider}, the
 * {@code stage}, the {@code step}, the {@code host} the player connected through and the {@code server} they
 * intend to reach. When the same step waits again, after a wrong answer, the {@code message} placeholder holds
 * Identica's answer, such as "Invalid password"; on the first prompt it is empty. Once the step stops waiting, the
 * moment settles.
 */
public final class StepWaitingTrigger implements DialogTrigger, EventListener {
	/**
	 * ID rules name this trigger by.
	 */
	public static final String ID = "step-waiting";

	private final EventManager events;
	private final Map<UUID, String> waiting = new ConcurrentHashMap<>();
	private volatile @Nullable TriggerSink sink;

	/**
	 * Creates the trigger over Identica's events.
	 *
	 * @param events Identica's event manager
	 */
	public StepWaitingTrigger(@NotNull EventManager events) {
		this.events = events;
	}

	@Override
	public @NotNull String id() {
		return ID;
	}

	@Override
	public void start(@NotNull TriggerSink sink) {
		this.sink = sink;
		events.register(this);
	}

	@Override
	public void stop() {
		events.unregister(this);
		sink = null;
		waiting.clear();
	}

	/**
	 * Reports a step that waits for input, or settles the moment of a step that stopped waiting.
	 *
	 * @param event a finished step
	 */
	@IdenticEvent
	public void onStepFinished(StepFinishedEvent event) {
		TriggerSink current = sink;
		UUID connectionUniqueId = event.getContext().getConnectionUniqueId();
		if (current == null || connectionUniqueId == null) return;

		String step = event.getStep().getName();
		StepResult result = event.getResult();
		if (result.getStatus() != StepResult.StepStatus.WAITING || result.getWaitReason() == StepWaitReason.ONLINE) {
			if (waiting.remove(connectionUniqueId, step)) current.settle(connectionUniqueId, ID);
			return;
		}

		boolean again = step.equals(waiting.put(connectionUniqueId, step));
		String message = again && result.getMessage() != null ? result.getMessage() : "";
		current.fire(context(event, connectionUniqueId, step, message));
	}

	private static TriggerContext context(StepFinishedEvent event, UUID connectionUniqueId, String step, String message) {
		ScenarioContext scenario = event.getContext();
		TriggerContext.TriggerContextBuilder context = TriggerContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.trigger(ID)
				.attribute("scenario", event.getPipelineType().name())
				.attribute("stage", event.getPhase().id())
				.attribute("step", step)
				.placeholder("message", message);

		ProviderContext provider = scenario.getProvider();
		if (provider != null && provider.getProviderId() != null) context.attribute("provider", provider.getProviderId());

		ConnectionIdentity.Origin origin = scenario.getIdentity().getOrigin();
		if (origin != null) context.attribute("host", origin.getHost());

		if (scenario.getIntendedServer() != null) context.attribute("server", scenario.getIntendedServer());

		return context.build();
	}
}

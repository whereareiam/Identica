package me.whereareiam.identica.engine.step;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.option.JourneyOption;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.option.JourneyOptions;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.pipeline.scenario.registration.EnrollmentOptionsEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.message.ProviderChoiceMessages;
import me.whereareiam.identica.model.auth.EnrollmentEntry;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.message.Prompt;
import me.whereareiam.identica.model.pipeline.journey.JourneyOverrideItem;
import me.whereareiam.identica.model.pipeline.journey.ProviderMethod;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.AbstractStep;
import me.whereareiam.identica.pipeline.journey.step.StatefulStep;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyChoice;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.keystone.model.SerializerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Lets the player pick how a scenario gets done when its {@code choice} is {@link JourneyChoice#PROMPTED}: one option per
 * provider, or per method of a provider that offers several. The player is not asked when the selection already
 * settled the provider, such as by hostname or a linked account, and that provider has one option left, or when
 * there is only one option. With {@link JourneyChoice#AUTOMATIC} the step does nothing: the journey tries the options
 * in their order.
 */
@Singleton
public class EnrollmentStep extends AbstractStep implements StatefulStep {
	private final JourneyOptions journeyOptions;
	private final ProviderOperations providerOperations;
	private final Provider<Engine> engineProvider;
	private final Provider<Messages> messagesProvider;
	private final EventManager eventManager;
	private final ProviderSelector providerSelector;

	@Inject
	public EnrollmentStep(
			JourneyOptions journeyOptions,
			ProviderOperations providerOperations,
			Provider<Engine> engineProvider,
			Provider<Messages> messagesProvider,
			EventManager eventManager,
			ProviderSelector providerSelector
	) {
		super("enrollment");
		this.journeyOptions = journeyOptions;
		this.providerOperations = providerOperations;
		this.engineProvider = engineProvider;
		this.messagesProvider = messagesProvider;
		this.eventManager = eventManager;
		this.providerSelector = providerSelector;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context, @NotNull PipelineState state) {
		PipelineType pipelineType = state.getPipelineType();
		if (pipelineType == null || choice(pipelineType) != JourneyChoice.PROMPTED)
			return CompletableFuture.completedFuture(StepResult.proceed(context));

		Messages messages = messagesProvider.get();
		Messages.Engine.Journey.Step.Enrollment enrollment = messages
				.getEngine()
				.getJourney()
				.getStep()
				.getEnrollment();
		List<JourneyOption> options = journeyOptions.resolve(
				context,
				pipelineType,
				state.get(JourneyOverrideItem.KEY).orElse(null)
		);
		if (options.isEmpty())
			return CompletableFuture.completedFuture(StepResult.failed(ProviderChoiceMessages.EMPTY.create(messages)));

		List<EnrollmentEntry> entries = settledEntries(context, buildEntries(enrollment, options));
		EnrollmentOptionsEvent optionsEvent = new EnrollmentOptionsEvent(context, entries);
		eventManager.call(optionsEvent);
		entries = optionsEvent.getEntries();

		if (entries.isEmpty())
			return CompletableFuture.completedFuture(StepResult.failed(ProviderChoiceMessages.EMPTY.create(messages)));

		if (entries.size() == 1) {
			EnrollmentEntry only = entries.getFirst();
			Logger.debug("Enrollment skipped the choice username=%s option=%s", context.getUsername(), only.getOptionId());
			runOption(context, only.getProviderId(), only.getMethod());
			return CompletableFuture.completedFuture(StepResult.complete(context));
		}

		return CompletableFuture.completedFuture(StepResult.waiting(buildMessage(messages, enrollment, entries)));
	}

	private @NotNull JourneyChoice choice(@NotNull PipelineType pipelineType) {
		Engine.Scenarios scenarios = engineProvider.get().getScenarios();
		Engine.Scenario scenario = switch (pipelineType) {
			case REGISTRATION -> scenarios.getRegistration();
			case MIGRATION -> scenarios.getMigration();
			case AUTHENTICATION -> scenarios.getAuthentication();
		};

		return scenario.getChoice();
	}

	/**
	 * Keeps the options of the provider the selection already settled with its hostname or a stronger claim, such as
	 * a verified subject already linked to an account. The player is not asked to choose the provider again, only
	 * among its methods.
	 */
	private @NotNull List<EnrollmentEntry> settledEntries(
			@NotNull ScenarioContext context,
			@NotNull List<EnrollmentEntry> entries
	) {
		List<String> offered = entries.stream().map(EnrollmentEntry::getProviderId).distinct().toList();
		ProviderClaim winner = providerSelector.winner(context.getSelection(), offered);
		if (winner == null || !providerSelector.isAtLeast(winner.getOrigin(), ProviderClaimOrigin.HOSTNAME)) return entries;

		Logger.debug("Enrollment kept the options of the settled provider username=%s provider=%s origin=%s",
				context.getUsername(),
				winner.getProviderId(),
				winner.getOrigin());
		return entries.stream()
				.filter(entry -> entry.getProviderId().equalsIgnoreCase(winner.getProviderId()))
				.toList();
	}

	private void runOption(@NotNull ScenarioContext context, @NotNull String providerId, @Nullable String method) {
		ProviderContext provider = context.getProvider();
		String username = context.getUsername() != null ? context.getUsername() : "";
		if (provider == null) {
			provider = ProviderContext.of(providerId, null, username);
			context.setProvider(provider);
		} else {
			provider.setProviderId(providerId);
			if (provider.getProviderUsername().isBlank())
				provider.setProviderUsername(username);
		}

		if (provider != null)
			provider.setMethod(method);
	}

	private List<EnrollmentEntry> buildEntries(
			Messages.Engine.Journey.Step.Enrollment enrollment,
			List<JourneyOption> options
	) {
		Map<String, Long> optionsPerProvider = options.stream()
				.collect(Collectors.groupingBy(option -> option.getProviderId().toLowerCase(Locale.ROOT), Collectors.counting()));

		List<EnrollmentEntry> entries = new ArrayList<>();
		for (JourneyOption option : options) {
			String providerId = option.getProviderId();
			// A provider with one option is answered by its ID alone.
			boolean namesMethod = optionsPerProvider.get(providerId.toLowerCase(Locale.ROOT)) > 1;
			String name = providerOperations.displayProviderName(providerId);
			if (name == null || name.isBlank())
				name = providerId;

			ProviderMethod method = option.getMethod();
			String methodName = method != null && method.getLabel() != null ? method.getLabel().get() : null;
			List<String> descriptionLines = resolveDescription(enrollment, option.id());
			if (descriptionLines.isEmpty() && method != null)
				descriptionLines = resolveDescription(enrollment, providerId);

			entries.add(EnrollmentEntry.builder()
					.providerId(providerId)
					.providerName(name)
					.description(descriptionLines)
					.method(namesMethod ? option.methodName() : null)
					.methodName(namesMethod ? methodName : null)
					.build());
		}
		return entries;
	}

	private List<String> resolveDescription(
			Messages.Engine.Journey.Step.Enrollment enrollment,
			String optionId
	) {
		Map<String, String> descriptions = enrollment.getDescriptions();
		if (descriptions.isEmpty())
			return List.of();

		for (Map.Entry<String, String> entry : descriptions.entrySet()) {
			if (entry.getKey() == null)
				continue;
			if (!entry.getKey().equalsIgnoreCase(optionId))
				continue;

			String description = entry.getValue();
			if (description == null || description.isBlank())
				return List.of();
			return List.of(description);
		}

		return List.of();
	}

	private @NotNull PlayerMessage buildMessage(
			Messages messages,
			Messages.Engine.Journey.Step.Enrollment enrollment,
			List<EnrollmentEntry> entries
	) {
		SerializerOptions.PlaceholderFormat format = Serializer.getEngine().getPlaceholderFormat();
		List<String> renderedEntries = renderEntries(enrollment.getEntryFormat(), entries, format);
		List<Prompt.Option> options = entries.stream()
				.map(entry -> new Prompt.Option(entry.getOptionId(), optionName(entry)))
				.toList();

		PlayerMessage message = ProviderChoiceMessages.CHOICE.create(messages, Map.of("entries", String.join("\n", renderedEntries)));
		return message.withPrompt(ProviderChoiceMessages.PROMPT.toBuilder()
				.clearFields()
				.field(Prompt.Field.choice("option", options))
				.build());
	}

	private static @NotNull String optionName(@NotNull EnrollmentEntry entry) {
		String methodName = entry.getMethodName();

		return methodName == null || methodName.isBlank() ? entry.getProviderName() : methodName;
	}

	private List<String> renderEntries(
			Messages.Engine.Journey.Step.Enrollment.EntryFormat entryFormat,
			List<EnrollmentEntry> entries,
			SerializerOptions.PlaceholderFormat format
	) {
		List<String> rendered = new ArrayList<>();
		if (entryFormat == null) return rendered;

		String templateFormat = entryFormat.getFormat();
		if (templateFormat.isBlank()) return rendered;

		for (EnrollmentEntry entry : entries) {
			boolean hasDescription = !entry.getDescription().isEmpty();
			String template = hasDescription ? entryFormat.getFormat() : entryFormat.getEmptyFormat();
			if (template.isBlank()) template = entryFormat.getFormat();

			String description = hasDescription ? String.join(" ", entry.getDescription()).trim() : "";
			String methodName = entry.getMethodName() != null ? entry.getMethodName() : "";
			Map<String, String> placeholders = Map.of(
					"optionId", entry.getOptionId(),
					"optionName", optionName(entry),
					"providerId", entry.getProviderId(),
					"providerName", entry.getProviderName(),
					"methodName", methodName,
					"description", description
			);

			String renderedLine = formatLine(template, placeholders, format);
			if (renderedLine != null && !renderedLine.isBlank())
				rendered.add(renderedLine);
		}

		return rendered;
	}

	private String formatLine(String line, Map<String, String> placeholders, SerializerOptions.PlaceholderFormat format) {
		if (line == null || line.isBlank())
			return "";
		String result = line;
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			String token = format.format(entry.getKey());
			String value = entry.getValue() == null ? "" : entry.getValue();
			result = result.replace(token, value);
		}
		return result;
	}
}

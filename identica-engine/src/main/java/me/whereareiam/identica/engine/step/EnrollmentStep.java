package me.whereareiam.identica.engine.step;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.pipeline.scenario.registration.EnrollmentOptionsEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.EnrollmentEntry;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.type.InteractiveStep;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.keystone.model.SerializerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Singleton
public class EnrollmentStep extends InteractiveStep {
	private final ProviderOperations providerOperations;
	private final Provider<Engine> engineProvider;
	private final Provider<Messages> messagesProvider;
	private final EventManager eventManager;
	private final ProviderSelector providerSelector;

	@Inject
	public EnrollmentStep(
			ProviderOperations providerOperations,
			Provider<Engine> engineProvider,
			Provider<Messages> messagesProvider,
			EventManager eventManager,
			ProviderSelector providerSelector
	) {
		super("enrollment");
		this.providerOperations = providerOperations;
		this.engineProvider = engineProvider;
		this.messagesProvider = messagesProvider;
		this.eventManager = eventManager;
		this.providerSelector = providerSelector;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		List<InternalProvider> providers = providerOperations.eligibleProviders(
				context,
				PipelineType.REGISTRATION,
				JourneyMode.INTERACTIVE
		);
		Messages.Engine.Journey.Step.Enrollment enrollment = messagesProvider.get()
				.getEngine()
				.getJourney()
				.getStep()
				.getEnrollment();

		if (providers.isEmpty())
			return CompletableFuture.completedFuture(StepResult.failed(joinLines(enrollment.getEmpty())));

		List<EnrollmentEntry> entries = buildEntries(enrollment, providers);
		EnrollmentOptionsEvent optionsEvent = new EnrollmentOptionsEvent(context, entries);
		eventManager.call(optionsEvent);
		entries = optionsEvent.getEntries();

		if (entries.isEmpty())
			return CompletableFuture.completedFuture(StepResult.failed(joinLines(enrollment.getEmpty())));

		ProviderClaim selected = selectedThroughEntrypoint(context, entries);
		if (selected != null) {
			Logger.debug("Enrollment skipped the provider choice username=%s provider=%s origin=%s",
					context.getUsername(),
					selected.getProviderId(),
					selected.getOrigin());
			runProvider(context, selected.getProviderId());
			return CompletableFuture.completedFuture(StepResult.complete(context));
		}

		if (shouldAutoSelectSingleProvider() && entries.size() == 1) {
			runProvider(context, entries.getFirst().getProviderId());
			return CompletableFuture.completedFuture(StepResult.complete(context));
		}

		String message = buildMessage(enrollment, entries);
		return CompletableFuture.completedFuture(StepResult.waiting(message));
	}

	private boolean shouldAutoSelectSingleProvider() {
		return engineProvider.get()
				.getScenarios().getRegistration()
				.isAutoSelectSingleProvider();
	}

	/**
	 * The offered provider the selection already settled with its hostname or a stronger claim, such as a verified
	 * subject already linked to an account. The player is not asked to choose again.
	 */
	private @Nullable ProviderClaim selectedThroughEntrypoint(
			@NotNull ScenarioContext context,
			@NotNull List<EnrollmentEntry> entries
	) {
		List<String> offered = entries.stream().map(EnrollmentEntry::getProviderId).toList();
		ProviderClaim winner = providerSelector.winner(context.getSelection(), offered);
		if (winner == null || !providerSelector.isAtLeast(winner.getOrigin(), ProviderClaimOrigin.HOSTNAME)) return null;

		return winner;
	}

	private void runProvider(@NotNull ScenarioContext context, @NotNull String providerId) {
		ProviderContext provider = context.getProvider();
		String username = context.getUsername() != null ? context.getUsername() : "";
		if (provider == null) {
			context.setProvider(ProviderContext.of(providerId, null, username));
			return;
		}

		provider.setProviderId(providerId);
		if (provider.getProviderUsername().isBlank())
			provider.setProviderUsername(username);
	}

	private List<EnrollmentEntry> buildEntries(
			Messages.Engine.Journey.Step.Enrollment enrollment,
			List<InternalProvider> providers
	) {
		List<EnrollmentEntry> entries = new ArrayList<>();
		for (InternalProvider provider : providers) {
			if (provider == null || provider.getDescriptor() == null)
				continue;

			ProviderDescriptor descriptor = provider.getDescriptor();
			String providerId = descriptor.getId();
			if (providerId.isBlank())
				continue;

			String name = descriptor.getName();
			if (name.isBlank())
				name = providerId;

			List<String> descriptionLines = resolveDescription(enrollment, providerId);
			entries.add(EnrollmentEntry.builder()
					.providerId(providerId)
					.providerName(name)
					.description(descriptionLines)
					.build());
		}
		return entries;
	}

	private List<String> resolveDescription(
			Messages.Engine.Journey.Step.Enrollment enrollment,
			String providerId
	) {
		Map<String, String> descriptions = enrollment.getDescriptions();
		if (descriptions.isEmpty())
			return List.of();

		for (Map.Entry<String, String> entry : descriptions.entrySet()) {
			if (entry.getKey() == null)
				continue;
			if (!entry.getKey().equalsIgnoreCase(providerId))
				continue;

			String description = entry.getValue();
			if (description == null || description.isBlank())
				return List.of();
			return List.of(description);
		}

		return List.of();
	}

	private String buildMessage(
			Messages.Engine.Journey.Step.Enrollment enrollment,
			List<EnrollmentEntry> entries
	) {
		SerializerOptions.PlaceholderFormat format = Serializer.getEngine().getPlaceholderFormat();
		List<String> renderedEntries = renderEntries(enrollment.getEntryFormat(), entries, format);

		List<String> lines = new ArrayList<>();
		List<String> body = enrollment.getBody();
		String entriesToken = format.format("entries");
		boolean inserted = false;

		if (!body.isEmpty()) {
			for (String line : body) {
				if (line != null && line.contains(entriesToken)) {
					lines.addAll(renderedEntries);
					inserted = true;
					continue;
				}
				if (line != null)
					lines.add(line);
			}
		}

		if (!inserted)
			lines.addAll(renderedEntries);

		return String.join("\n", lines);
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
			Map<String, String> placeholders = Map.of(
					"providerId", entry.getProviderId(),
					"providerName", entry.getProviderName(),
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

	private String joinLines(List<String> lines) {
		return String.join("\n", lines);
	}
}

package me.whereareiam.identica.pipeline.completion.step;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.completion.CompletionContext;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Base completion step that shows the player a configured title and its completion message.
 */
public abstract class AbstractMessageCompletionStep extends AbstractCompletionStep {
	protected AbstractMessageCompletionStep(@NotNull String name) {
		super(name);
	}

	protected @Nullable AbstractMessageCompletionStep.TitleContent title(@NotNull CompletionContext context) {
		return null;
	}

	protected final @NotNull AbstractMessageCompletionStep.TitleContent title(
			@Nullable String title,
			@Nullable String subtitle
	) {
		return new TitleContent(title, subtitle);
	}

	/**
	 * Returns the message that tells the player their journey completed, or {@code null} to tell nothing. The step
	 * sends it through {@link MessageService} with {@link #placeholders(CompletionContext)} as defaults for the
	 * values it does not set itself.
	 *
	 * @param context completion context containing the resolved identity and provider details
	 * @return the message, or {@code null}
	 */
	protected @Nullable PlayerMessage message(@NotNull CompletionContext context) {
		return null;
	}

	/**
	 * Returns the service that shows the completion message.
	 *
	 * @return the message service
	 */
	protected abstract @NotNull MessageService messageService();

	protected @NotNull Map<String, String> placeholders(@NotNull CompletionContext context) {
		Map<String, String> placeholders = new HashMap<>();

		String username = context.getIdentity().getUsername();
		if (!username.isBlank()) placeholders.put("player", username);

		String providerId = context.getProviderId();
		if (providerId != null && !providerId.isBlank()) placeholders.put("provider", providerId);

		return placeholders;
	}

	/**
	 * Executes this completion step by showing its optional title and message to the active identity.
	 *
	 * @param context completion context containing the resolved identity and provider details
	 */
	@Override
	public final void execute(@NotNull CompletionContext context) {
		Identity identity = context.getIdentity();
		Map<String, String> placeholders = placeholders(context);

		sendTitle(identity, title(context), placeholders);
		sendMessage(identity, message(context), placeholders);
	}

	private void sendTitle(
			@NotNull Identity identity,
			@Nullable AbstractMessageCompletionStep.TitleContent titleContent,
			@NotNull Map<String, String> placeholders
	) {
		if (titleContent == null) return;

		String resolvedTitle = renderTitle(titleContent.title(), placeholders);
		String resolvedSubtitle = renderTitle(titleContent.subtitle(), placeholders);
		if (resolvedTitle == null && resolvedSubtitle == null) return;

		Component titleComponent = resolvedTitle == null
				? Component.empty()
				: Serializer.serialize(identity, resolvedTitle);
		Component subtitleComponent = resolvedSubtitle == null
				? Component.empty()
				: Serializer.serialize(identity, resolvedSubtitle);
		identity.sendTitle(net.kyori.adventure.title.Title.title(titleComponent, subtitleComponent));
	}

	private void sendMessage(
			@NotNull Identity identity,
			@Nullable PlayerMessage message,
			@NotNull Map<String, String> placeholders
	) {
		if (message == null || message.isEmpty()) return;

		Map<String, String> merged = new HashMap<>(placeholders);
		merged.putAll(message.getPlaceholders());
		messageService().send(identity, message.toBuilder().placeholders(merged).build());
	}

	private static @Nullable String renderTitle(
			@Nullable String value,
			@NotNull Map<String, String> placeholders
	) {
		if (value == null) return null;

		String resolved = Serializer.render(value, placeholders);

		String trimmed = resolved.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	protected record TitleContent(
			@Nullable String title,
			@Nullable String subtitle
	) { }
}

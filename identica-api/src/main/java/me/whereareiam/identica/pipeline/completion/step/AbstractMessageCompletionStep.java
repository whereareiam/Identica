package me.whereareiam.identica.pipeline.completion.step;

import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.completion.CompletionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base completion step that tells the player its completion message, with a configured title.
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
	 * Executes this completion step by telling the active identity its completion message, with its title when the
	 * step has one, through {@link MessageService}.
	 *
	 * @param context completion context containing the resolved identity and provider details
	 */
	@Override
	public final void execute(@NotNull CompletionContext context) {
		PlayerMessage message = Objects.requireNonNullElseGet(message(context), () -> PlayerMessage.builder().build());
		TitleContent title = title(context);
		if (title != null) message = message.withTitle(title.title(), title.subtitle());
		if (message.isEmpty()) return;

		Map<String, String> placeholders = new HashMap<>(placeholders(context));
		placeholders.putAll(message.getPlaceholders());
		messageService().send(context.getIdentity(), message.toBuilder().placeholders(placeholders).build());
	}

	protected record TitleContent(
			@Nullable String title,
			@Nullable String subtitle
	) { }
}

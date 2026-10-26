package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.DialogPart;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.message.ChatPart;
import me.whereareiam.identica.model.message.Presentation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Presents the prompts and messages the rules name through Identica's message service: their dialog part replaces
 * chat, and the rules pick the dialog when Identica tells a player the message. Applying the rules again, such as
 * after a reload, replaces the presentations.
 */
public final class RulePresentations {
	private static final Presentation DIALOG = Presentation.builder()
			.part(DialogPart.byRules())
			.removed(ChatPart.TYPE)
			.build();

	private final MessageService messages;
	private final List<MessageService.Registration> registrations = new ArrayList<>();

	/**
	 * Creates the presentations, presenting nothing yet.
	 *
	 * @param messages Identica's message service
	 */
	public RulePresentations(@NotNull MessageService messages) {
		this.messages = messages;
	}

	/**
	 * Presents what the rules name, in place of what earlier rules named. A rule that names neither a prompt nor a
	 * message is left out, since it would match no message.
	 *
	 * @param rules the current rules
	 */
	public synchronized void apply(@NotNull List<DialogRule> rules) {
		remove();

		Set<String> prompts = new LinkedHashSet<>();
		Set<String> ids = new LinkedHashSet<>();
		for (DialogRule rule : rules) {
			String message = rule.getWhen().get(RuleMatcher.MESSAGE);
			String prompt = rule.getWhen().get(RuleMatcher.PROMPT);
			if (message != null) ids.add(message.toLowerCase(Locale.ROOT));
			else if (prompt != null) prompts.add(prompt.toLowerCase(Locale.ROOT));
			else Logger.warn("A dialog rule names neither a prompt nor a message and matches no message: %s", rule.getWhen());
		}

		prompts.forEach(prompt -> registrations.add(messages.presentPrompt(prompt, DIALOG)));
		ids.forEach(id -> registrations.add(messages.present(id, DIALOG)));
	}

	/**
	 * Removes every presentation.
	 */
	public synchronized void remove() {
		registrations.forEach(MessageService.Registration::remove);
		registrations.clear();
	}
}

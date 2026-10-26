package me.whereareiam.identica.message;

import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.message.MessageDefinition;
import me.whereareiam.identica.model.message.PartType;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.message.Presentation;
import me.whereareiam.identica.model.message.Prompt;
import me.whereareiam.identica.type.message.ClientScreen;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * How Identica tells players things. Every message a player gets from Identica or its providers, such as a prompt,
 * the answer to a wrong password or the reason a login is refused, is a {@link PlayerMessage} of a registered
 * {@link MessageDefinition}, shown through this service.
 *
 * <p>A message is shown by its parts: a chat line by default, a title when it has one, and whatever
 * {@link Presentation}s give it, in the ways registered {@link PartType}s render. An addon that shows prompts as
 * dialogs registers a dialog part type and presents the messages it wants as dialogs; everything else stays in
 * chat.</p>
 *
 * <p>A message with a prompt is the player's open prompt until another prompt replaces it, its owner ends it with
 * {@link #endPrompt(UUID, Prompt)}, the player's journey resolves or the player leaves; what it showed, such as a
 * dialog, stays until then. A message without a prompt, a notice, never ends the open prompt. A part the player
 * cannot see yet, such as chat on the loading screen, is shown once they joined a server's world, if its message is
 * still current.</p>
 *
 * <pre>{@code
 * IdenticaAPI.whenReady(identica -> identica.getMessageService().present("credential:password",
 *         Presentation.builder()
 *                 .part(new DialogPart("login"))
 *                 .removed(ChatPart.TYPE)
 *                 .build()));
 * }</pre>
 */
public interface MessageService {
	/**
	 * Registers message definitions, so they can be listed and presented before they are first sent. A definition
	 * with the ID of a registered one replaces it.
	 *
	 * @param definitions definitions to register
	 * @return removes the definitions again
	 */
	@NotNull Registration register(@NotNull Collection<? extends MessageDefinition<?>> definitions);

	/**
	 * Returns every registered message definition.
	 *
	 * @return definitions, in registration order
	 */
	@NotNull Collection<MessageDefinition<?>> definitions();

	/**
	 * Finds a registered message definition.
	 *
	 * @param id message ID
	 * @return the definition, or empty when no definition has the ID
	 */
	@NotNull Optional<MessageDefinition<?>> definition(@NotNull String id);

	/**
	 * Registers a kind of part messages can be shown with.
	 *
	 * @param type the part type
	 * @return removes the part type again
	 */
	@NotNull Registration registerPartType(@NotNull PartType<?> type);

	/**
	 * Gives a message parts on top of how it is shown otherwise, see {@link Presentation}.
	 *
	 * @param messageId ID of the message
	 * @param presentation the parts
	 * @return removes the presentation again
	 */
	@NotNull Registration present(@NotNull String messageId, @NotNull Presentation presentation);

	/**
	 * Gives every message that asks for the same thing parts on top of how it is shown otherwise, such as a dialog for
	 * a password asked for the first time and again after a wrong one, including messages defined later. When a
	 * presentation of the message's own ID has the same priority, it decides a part type the prompt's presentation
	 * gives too.
	 *
	 * <pre>{@code
	 * messages.presentPrompt("credential:password", Presentation.builder()
	 *         .part(new DialogPart("login"))
	 *         .removed(ChatPart.TYPE)
	 *         .build());
	 * }</pre>
	 *
	 * @param promptId ID of the prompt, see {@link me.whereareiam.identica.model.message.Prompt#getId()}
	 * @param presentation the parts
	 * @return removes the presentation again
	 */
	@NotNull Registration presentPrompt(@NotNull String promptId, @NotNull Presentation presentation);

	/**
	 * Shows a message to a player in all of its parts. A message with a prompt becomes the player's open prompt and
	 * takes down what the previous one showed, once its own parts are shown; a notice takes down only what the
	 * previous notice showed. A part the player cannot see yet waits until they joined a server's world.
	 *
	 * @param recipient the player
	 * @param message the message; an empty message shows nothing
	 */
	void send(@NotNull Identity recipient, @NotNull PlayerMessage message);

	/**
	 * Ends a player's open prompt, taking down what it showed, such as a dialog, when it is the given prompt. Identica
	 * ends the prompts of a journey itself when the journey resolves; an owner that asks outside a journey, such as a
	 * command that waits for a code, ends its prompt once it has its answer or gives up.
	 *
	 * @param connectionUniqueId the connection
	 * @param prompt the prompt to end; a different open prompt stays
	 */
	void endPrompt(@NotNull UUID connectionUniqueId, @NotNull Prompt prompt);

	/**
	 * Tells what a connection's client shows now: the loading screen, nothing while it joins a server, or a server's
	 * world. A part renderer returns {@code PartRendering.notYet()} while the client cannot show its part, such as chat
	 * anywhere but in a world, and Identica renders it again once the player joined a server.
	 *
	 * @param connectionUniqueId the connection
	 * @return what the client shows
	 */
	@NotNull ClientScreen screen(@NotNull UUID connectionUniqueId);

	/**
	 * Renders a message as the reason a player is disconnected or refused, which can only be a text: the message's
	 * chat text and the text of the messages appended to it, rendered for the player.
	 *
	 * @param actor the player, or {@code null} for a connection refused before it became a player
	 * @param message the message
	 * @return the reason
	 */
	@NotNull Component disconnectReason(@Nullable Actor actor, @NotNull PlayerMessage message);

	/**
	 * Takes down everything still shown to a connection, such as a dialog, and drops what waits to be shown, because
	 * the player left. Identica calls this itself; to end one prompt, see {@link #endPrompt(UUID, Prompt)}.
	 *
	 * @param connectionUniqueId the connection
	 */
	void close(@NotNull UUID connectionUniqueId);

	/**
	 * Builds the command line that answers a prompt: its command as a player types it, see
	 * {@link me.whereareiam.identica.command.CommandService#commandLine(String)}, followed by the values of its fields
	 * in order, without a leading slash.
	 *
	 * @param prompt the prompt
	 * @param values field values by field name
	 * @return the command line, or empty when the prompt's command is not registered
	 */
	@NotNull Optional<String> answer(@NotNull Prompt prompt, @NotNull Map<String, String> values);

	/**
	 * Undoes a registration.
	 */
	@FunctionalInterface
	interface Registration {
		/**
		 * Removes what was registered. Removing twice does nothing.
		 */
		void remove();
	}
}

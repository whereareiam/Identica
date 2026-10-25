package me.whereareiam.identica.common.messaging;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.message.IdenticaMessages;
import me.whereareiam.identica.message.MessageGate;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.message.PartRenderer;
import me.whereareiam.identica.model.CommandDefinition;
import me.whereareiam.identica.model.message.*;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shows messages by their parts: chat unless presentations say otherwise, each part by the renderer of its type, a
 * type's fallbacks when its renderer cannot show it. What stays visible is taken down when the next message reaches
 * the player, or when the player leaves.
 */
@Singleton
public class DefaultMessageService implements MessageService, EventListener {
	private final Provider<MessageGate> gate;
	private final Provider<CommandService> commands;

	private final Map<String, MessageDefinition<?>> definitions = Collections.synchronizedMap(new LinkedHashMap<>());
	private final Map<String, PartType<?>> partTypes = new ConcurrentHashMap<>();
	private final Map<String, List<PresentationEntry>> presentations = new ConcurrentHashMap<>();
	private final Map<String, List<PresentationEntry>> promptPresentations = new ConcurrentHashMap<>();
	private final Map<UUID, List<PartRendering.Close>> shown = new ConcurrentHashMap<>();
	private final Set<String> unknownPartTypes = ConcurrentHashMap.newKeySet();
	private final AtomicLong presentationOrder = new AtomicLong();

	@Inject
	public DefaultMessageService(
			@NotNull Provider<MessageGate> gate,
			@NotNull Provider<CommandService> commands,
			@NotNull EventManager eventManager
	) {
		this.gate = gate;
		this.commands = commands;
		registerPartType(PartType.<ChatPart>builder()
				.id(ChatPart.TYPE)
				.partClass(ChatPart.class)
				.renderer(new ChatPartRenderer())
				.defaultPart(message -> ChatPart.ofMessage())
				.build());
		register(IdenticaMessages.all());
		eventManager.register(this);
	}

	@Override
	public @NotNull Registration register(@NotNull Collection<? extends MessageDefinition<?>> registered) {
		List<MessageDefinition<?>> added = List.copyOf(registered);
		for (MessageDefinition<?> definition : added) {
			MessageDefinition<?> replaced = definitions.put(normalize(definition.getId()), definition);
			if (replaced != null && replaced != definition)
				Logger.debug("Message definition %s replaced", definition.getId());
		}

		return () -> {
			for (MessageDefinition<?> definition : added)
				definitions.remove(normalize(definition.getId()), definition);
		};
	}

	@Override
	public @NotNull Collection<MessageDefinition<?>> definitions() {
		synchronized (definitions) {
			return List.copyOf(definitions.values());
		}
	}

	@Override
	public @NotNull Optional<MessageDefinition<?>> definition(@NotNull String id) {
		return Optional.ofNullable(definitions.get(normalize(id)));
	}

	@Override
	public @NotNull Registration registerPartType(@NotNull PartType<?> type) {
		String id = normalize(type.getId());
		PartType<?> replaced = partTypes.put(id, type);
		if (replaced != null && replaced != type)
			Logger.warn("Message part type %s registered again, replacing the earlier registration", type.getId());

		return () -> partTypes.remove(id, type);
	}

	@Override
	public @NotNull Registration present(@NotNull String messageId, @NotNull Presentation presentation) {
		return present(presentations, messageId, presentation, false);
	}

	@Override
	public @NotNull Registration presentPrompt(@NotNull String promptId, @NotNull Presentation presentation) {
		return present(promptPresentations, promptId, presentation, true);
	}

	private @NotNull Registration present(
			@NotNull Map<String, List<PresentationEntry>> byId,
			@NotNull String rawId,
			@NotNull Presentation presentation,
			boolean prompt
	) {
		String id = normalize(rawId);
		PresentationEntry entry = new PresentationEntry(presentation, prompt, presentationOrder.incrementAndGet());
		byId.computeIfAbsent(id, ignored -> new CopyOnWriteArrayList<>()).add(entry);
		warnAboutCollisions(byId, id, entry);

		return () -> {
			List<PresentationEntry> entries = byId.get(id);
			if (entries != null) entries.remove(entry);
		};
	}

	@Override
	public void send(@NotNull Identity recipient, @NotNull PlayerMessage message) {
		if (message.isEmpty()) return;
		if (gate.get().hold(recipient, message)) return;

		show(recipient, message);
	}

	/**
	 * Shows a message now, without asking the gate, such as one a player held on the loading screen can see at last.
	 *
	 * @param recipient the player
	 * @param message the message
	 */
	public void show(@NotNull Identity recipient, @NotNull PlayerMessage message) {
		if (message.isEmpty()) return;

		UUID connectionUniqueId = recipient.getConnectionUniqueId();
		if (connectionUniqueId != null) close(connectionUniqueId);

		Map<String, MessagePart> parts = parts(message);
		List<PartRendering.Close> closes = new ArrayList<>();
		for (MessagePart part : parts.values())
			render(part, parts.keySet(), message, recipient, closes);

		if (connectionUniqueId != null && !closes.isEmpty())
			shown.put(connectionUniqueId, closes);
	}

	@Override
	public @NotNull Component disconnectReason(@Nullable Actor actor, @NotNull PlayerMessage message) {
		MessagePart chat = parts(message).get(ChatPart.TYPE);
		String text = chat instanceof ChatPart part && part.getText() != null ? part.getText() : message.getText();
		if (actor != null) return Serializer.serialize(actor, text, message.getPlaceholders());

		return Serializer.serialize(SerializerContent.builder()
				.message(text)
				.placeholders(message.getPlaceholders())
				.build());
	}

	@Override
	public void close(@NotNull UUID connectionUniqueId) {
		List<PartRendering.Close> closes = shown.remove(connectionUniqueId);
		if (closes == null) return;

		for (PartRendering.Close close : closes) {
			try {
				close.close();
			} catch (RuntimeException failure) {
				Logger.severe(failure, "Could not take down a message part for connection %s", connectionUniqueId);
			}
		}
	}

	@Override
	public @NotNull Optional<String> answer(@NotNull Prompt prompt, @NotNull Map<String, String> values) {
		CommandDefinition command = commands.get().getRegisteredDefinitions().get(prompt.getCommand());
		if (command == null || command.getAliases() == null || command.getAliases().isEmpty()) return Optional.empty();

		StringJoiner line = new StringJoiner(" ");
		line.add(command.getAliases().getFirst());
		for (Prompt.Field field : prompt.getFields())
			line.add(values.getOrDefault(field.getName(), ""));

		return Optional.of(line.toString().strip());
	}

	@IdenticEvent
	public void onIdentityDetached(@NotNull IdentityDetachedEvent event) {
		close(event.getUniqueId());
	}

	/**
	 * Resolves a message's parts: chat, then every presentation of its prompt and of its ID from the lowest priority
	 * up, so a higher priority wins per part type, and among equals one of the message's own ID.
	 */
	private @NotNull Map<String, MessagePart> parts(@NotNull PlayerMessage message) {
		Map<String, MessagePart> parts = new LinkedHashMap<>();
		parts.put(ChatPart.TYPE, ChatPart.ofMessage());

		List<PresentationEntry> entries = new ArrayList<>(presentations.getOrDefault(normalize(message.getId()), List.of()));
		Prompt prompt = message.getPrompt();
		if (prompt != null) entries.addAll(promptPresentations.getOrDefault(normalize(prompt.getId()), List.of()));
		entries.stream()
				.sorted(Comparator.comparingInt((PresentationEntry entry) -> entry.presentation().getPriority())
						.thenComparing(PresentationEntry::prompt, Comparator.reverseOrder())
						.thenComparingLong(PresentationEntry::order))
				.forEach(entry -> {
					entry.presentation().getRemoved().forEach(type -> parts.remove(normalize(type)));
					for (MessagePart part : entry.presentation().getParts())
						parts.put(normalize(part.type()), part);
				});

		return parts;
	}

	private void render(
			@NotNull MessagePart part,
			@NotNull Set<String> presented,
			@NotNull PlayerMessage message,
			@NotNull Identity recipient,
			@NotNull List<PartRendering.Close> closes
	) {
		PartType<?> type = partTypes.get(normalize(part.type()));
		if (type == null) {
			if (unknownPartTypes.add(part.type()))
				Logger.warn("Message %s names the part type %s, which is not registered", message.getId(), part.type());
			return;
		}

		PartRendering rendering = renderWith(type, part, message, recipient);
		if (rendering.isShown()) {
			if (rendering.getClose() != null) closes.add(rendering.getClose());
			return;
		}

		for (String fallback : type.getFallbacks()) {
			// A fallback the message shows anyway is shown in its own turn.
			if (presented.contains(normalize(fallback))) return;

			PartType<?> fallbackType = partTypes.get(normalize(fallback));
			if (fallbackType == null || fallbackType.getDefaultPart() == null) continue;

			PartRendering fallbackRendering = renderDefault(fallbackType, message, recipient);
			if (!fallbackRendering.isShown()) continue;

			if (fallbackRendering.getClose() != null) closes.add(fallbackRendering.getClose());
			return;
		}
	}

	private <P extends MessagePart> @NotNull PartRendering renderWith(
			@NotNull PartType<P> type,
			@NotNull MessagePart part,
			@NotNull PlayerMessage message,
			@NotNull Identity recipient
	) {
		if (!type.getPartClass().isInstance(part)) {
			Logger.warn("Message %s has a %s part of class %s, expected %s",
					message.getId(), type.getId(), part.getClass().getName(), type.getPartClass().getName());
			return PartRendering.unsupported();
		}

		return safely(type, type.getRenderer(), type.getPartClass().cast(part), message, recipient);
	}

	private <P extends MessagePart> @NotNull PartRendering renderDefault(
			@NotNull PartType<P> type,
			@NotNull PlayerMessage message,
			@NotNull Identity recipient
	) {
		P part = Objects.requireNonNull(type.getDefaultPart()).apply(message);

		return safely(type, type.getRenderer(), part, message, recipient);
	}

	private <P extends MessagePart> @NotNull PartRendering safely(
			@NotNull PartType<P> type,
			@NotNull PartRenderer<P> renderer,
			@NotNull P part,
			@NotNull PlayerMessage message,
			@NotNull Identity recipient
	) {
		try {
			return renderer.render(part, message, recipient);
		} catch (RuntimeException failure) {
			Logger.severe(failure, "Could not show the %s part of message %s", type.getId(), message.getId());
			return PartRendering.unsupported();
		}
	}

	private void warnAboutCollisions(
			@NotNull Map<String, List<PresentationEntry>> byId,
			@NotNull String messageId,
			@NotNull PresentationEntry added
	) {
		for (PresentationEntry entry : byId.getOrDefault(messageId, List.of())) {
			if (entry == added || entry.presentation().getPriority() != added.presentation().getPriority()) continue;

			for (MessagePart part : added.presentation().getParts()) {
				boolean collides = entry.presentation().getParts().stream()
						.anyMatch(other -> normalize(other.type()).equals(normalize(part.type())));
				if (collides)
					Logger.warn("Two presentations of %s give a %s part at priority %d; the later one wins",
							messageId, part.type(), added.presentation().getPriority());
			}
		}
	}

	private static @NotNull String normalize(@NotNull String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private record PresentationEntry(@NotNull Presentation presentation, boolean prompt, long order) {
	}
}

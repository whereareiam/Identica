package me.whereareiam.identica.common.messaging;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import me.whereareiam.identica.event.connection.ServerSwitchingEvent;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.event.scenario.ScenarioResolvedEvent;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.message.IdenticaMessages;
import me.whereareiam.identica.message.LoadingScreen;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.message.PartRenderer;
import me.whereareiam.identica.model.message.*;
import me.whereareiam.identica.type.message.ClientScreen;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shows messages by their parts: chat unless presentations say otherwise, a title when the message has one, each part
 * by the renderer of its type, a type's fallbacks when its renderer cannot show it. Each connection has one open prompt
 * and one last notice; what a prompt showed stays until another prompt replaces it, its owner ends it, the player's
 * journey resolves or the player leaves, and what a notice showed stays until the next notice. Parts the player cannot
 * see yet wait until they joined a server's world.
 */
@Singleton
public class DefaultMessageService implements MessageService, EventListener {
	private final Provider<CommandService> commands;
	private final Provider<LoadingScreen> loadingScreen;

	private final Map<String, MessageDefinition<?>> definitions = Collections.synchronizedMap(new LinkedHashMap<>());
	private final Map<String, PartType<?>> partTypes = new ConcurrentHashMap<>();
	private final Map<String, List<PresentationEntry>> presentations = new ConcurrentHashMap<>();
	private final Map<String, List<PresentationEntry>> promptPresentations = new ConcurrentHashMap<>();
	private final Map<UUID, ShownMessages> shown = new ConcurrentHashMap<>();
	private final Set<UUID> inWorld = ConcurrentHashMap.newKeySet();
	private final Set<String> unknownPartTypes = ConcurrentHashMap.newKeySet();
	private final AtomicLong presentationOrder = new AtomicLong();

	@Inject
	public DefaultMessageService(
			@NotNull Provider<LoadingScreen> loadingScreen,
			@NotNull Provider<CommandService> commands,
			@NotNull EventManager eventManager
	) {
		this.loadingScreen = loadingScreen;
		this.commands = commands;
		registerPartType(PartType.<ChatPart>builder()
				.id(ChatPart.TYPE)
				.partClass(ChatPart.class)
				.renderer(new ChatPartRenderer(this))
				.defaultPart(message -> ChatPart.ofMessage())
				.build());
		registerPartType(PartType.<TitlePart>builder()
				.id(TitlePart.TYPE)
				.partClass(TitlePart.class)
				.renderer(new TitlePartRenderer(this))
				.defaultPart(message -> TitlePart.ofMessage())
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

		Map<String, MessagePart> parts = parts(message);
		ShownMessages.Shown shownMessage = new ShownMessages.Shown(message);
		List<MessagePart> waits = new ArrayList<>();
		for (MessagePart part : parts.values())
			render(part, parts.keySet(), shownMessage, recipient, waits);

		UUID connectionUniqueId = recipient.getConnectionUniqueId();
		if (connectionUniqueId == null) return;

		ShownMessages state = shown.computeIfAbsent(connectionUniqueId, ShownMessages::new);
		synchronized (state) {
			// The previous message's parts go only now, so a part shown again, such as a dialog, replaces itself.
			state.current(shownMessage, waits);
		}
	}

	@Override
	public void endPrompt(@NotNull UUID connectionUniqueId, @NotNull Prompt prompt) {
		ShownMessages state = shown.get(connectionUniqueId);
		if (state == null) return;

		synchronized (state) {
			ShownMessages.Shown open = state.prompt();
			Prompt openPrompt = open != null ? open.message().getPrompt() : null;
			if (openPrompt != null && normalize(openPrompt.getId()).equals(normalize(prompt.getId())))
				state.endPrompt();
		}
	}

	@Override
	public @NotNull ClientScreen screen(@NotNull UUID connectionUniqueId) {
		if (loadingScreen.get().holds(connectionUniqueId)) return ClientScreen.LOADING_SCREEN;
		if (inWorld.contains(connectionUniqueId)) return ClientScreen.WORLD;

		return ClientScreen.JOINING;
	}

	@Override
	public @NotNull Component disconnectReason(@Nullable Actor actor, @NotNull PlayerMessage message) {
		MessagePart chat = parts(message).get(ChatPart.TYPE);
		PlayerMessage reason = chat instanceof ChatPart part && part.getText() != null
				? message.toBuilder().text(part.getText()).build()
				: message;

		return Serializer.serialize(actor, reason);
	}

	@Override
	public void close(@NotNull UUID connectionUniqueId) {
		inWorld.remove(connectionUniqueId);
		ShownMessages state = shown.remove(connectionUniqueId);
		if (state == null) return;

		synchronized (state) {
			state.close();
		}
	}

	@Override
	public @NotNull Optional<String> answer(@NotNull Prompt prompt, @NotNull Map<String, String> values) {
		String command = commands.get().commandLine(prompt.getCommand()).orElse(null);
		if (command == null) return Optional.empty();

		StringJoiner line = new StringJoiner(" ");
		line.add(command);
		for (Prompt.Field field : prompt.getFields())
			line.add(values.getOrDefault(field.getName(), ""));

		return Optional.of(line.toString().strip());
	}

	@IdenticEvent
	public void onIdentityDetached(@NotNull IdentityDetachedEvent event) {
		close(event.getUniqueId());
	}

	/**
	 * Ends the open prompt of a player whose journey resolved, since nothing of the journey waits for an answer any
	 * more.
	 *
	 * @param event the resolved scenario
	 */
	@IdenticEvent
	public void onScenarioResolved(@NotNull ScenarioResolvedEvent event) {
		ShownMessages state = shown.get(event.getConnectionUniqueId());
		if (state == null) return;

		synchronized (state) {
			state.endPrompt();
		}
	}

	/**
	 * Records that a player's client left its server's world to join another one, so parts that need a world wait.
	 *
	 * @param event the player switches servers
	 */
	@IdenticEvent
	public void onServerSwitching(@NotNull ServerSwitchingEvent event) {
		UUID connectionUniqueId = event.getIdentity().getConnectionUniqueId();
		if (connectionUniqueId != null) inWorld.remove(connectionUniqueId);
	}

	/**
	 * Shows what waited once the player joined a server's world, and shows again the parts of the open prompt the
	 * client dropped when it switched servers, such as a dialog.
	 *
	 * @param event the player joined a server
	 */
	@IdenticEvent
	public void onServerJoined(@NotNull ServerJoinedEvent event) {
		Identity recipient = event.getIdentity();
		UUID connectionUniqueId = recipient.getConnectionUniqueId();
		if (connectionUniqueId == null) return;

		inWorld.add(connectionUniqueId);
		ShownMessages state = shown.get(connectionUniqueId);
		if (state == null) return;

		List<ShownMessages.Waiting> waiting;
		List<ShownMessages.Waiting> dropped = new ArrayList<>();
		synchronized (state) {
			waiting = state.takeWaiting();
			ShownMessages.Shown open = state.prompt();
			if (open != null)
				open.parts().forEach((type, part) -> {
					PartType<?> partType = partTypes.get(type);
					if (partType != null && partType.isDroppedOnServerSwitch()) dropped.add(new ShownMessages.Waiting(open, part));
				});
		}

		for (ShownMessages.Waiting entry : waiting)
			showAgain(entry, recipient, state);
		for (ShownMessages.Waiting entry : dropped)
			showAgain(entry, recipient, state);
	}

	/**
	 * Resolves a message's parts: chat, a title when the message has one, then every presentation of its prompt and
	 * of its ID from the lowest priority up, so a higher priority wins per part type, and among equals one of the
	 * message's own ID.
	 */
	private @NotNull Map<String, MessagePart> parts(@NotNull PlayerMessage message) {
		Map<String, MessagePart> parts = new LinkedHashMap<>();
		parts.put(ChatPart.TYPE, ChatPart.ofMessage());
		if (message.hasTitle()) parts.put(TitlePart.TYPE, TitlePart.ofMessage());

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

	/**
	 * Renders one part of a message, or a fallback of its type when the player's client cannot show it. A part the
	 * player cannot see yet waits.
	 */
	private void render(
			@NotNull MessagePart part,
			@NotNull Set<String> presented,
			@NotNull ShownMessages.Shown shownMessage,
			@NotNull Identity recipient,
			@NotNull List<MessagePart> waits
	) {
		PartType<?> type = partTypes.get(normalize(part.type()));
		if (type == null) {
			if (unknownPartTypes.add(part.type()))
				Logger.warn("Message %s names the part type %s, which is not registered",
						shownMessage.message().getId(), part.type());
			return;
		}

		PartRendering rendering = renderWith(type, part, shownMessage.message(), recipient);
		if (record(rendering, normalize(type.getId()), part, shownMessage, waits)) return;

		for (String fallback : type.getFallbacks()) {
			// A fallback the message shows anyway is shown in its own turn.
			if (presented.contains(normalize(fallback))) return;

			PartType<?> fallbackType = partTypes.get(normalize(fallback));
			if (fallbackType == null || fallbackType.getDefaultPart() == null) continue;

			MessagePart fallbackPart = fallbackType.getDefaultPart().apply(shownMessage.message());
			PartRendering fallbackRendering = renderWith(fallbackType, fallbackPart, shownMessage.message(), recipient);
			if (record(fallbackRendering, normalize(fallbackType.getId()), fallbackPart, shownMessage, waits)) return;
		}
	}

	/**
	 * Records what became of a rendered part: shown, or waiting until the player can see it.
	 *
	 * @return {@code false} when the part cannot be shown to the player, so a fallback is tried
	 */
	private static boolean record(
			@NotNull PartRendering rendering,
			@NotNull String type,
			@NotNull MessagePart part,
			@NotNull ShownMessages.Shown shownMessage,
			@NotNull List<MessagePart> waits
	) {
		if (rendering.isShown()) {
			shownMessage.shown(type, part, rendering.getClose());
			return true;
		}
		if (!rendering.isNotYet()) return false;

		waits.add(part);
		return true;
	}

	/**
	 * Renders a part again once the player may see it: it stays when its message is still current, is taken down at
	 * once when its message was replaced meanwhile, and waits again when the player still cannot see it.
	 */
	private void showAgain(@NotNull ShownMessages.Waiting entry, @NotNull Identity recipient, @NotNull ShownMessages state) {
		PartType<?> type = partTypes.get(normalize(entry.part().type()));
		if (type == null) return;

		PartRendering rendering = renderWith(type, entry.part(), entry.shown().message(), recipient);
		synchronized (state) {
			if (rendering.isNotYet()) {
				state.waitAgain(entry);
				return;
			}
			if (!rendering.isShown()) return;

			if (state.isCurrent(entry.shown())) {
				entry.shown().shown(normalize(type.getId()), entry.part(), rendering.getClose());
				return;
			}
		}

		if (rendering.getClose() != null) rendering.getClose().close();
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

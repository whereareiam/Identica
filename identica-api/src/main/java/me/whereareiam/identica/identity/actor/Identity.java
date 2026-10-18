package me.whereareiam.identica.identity.actor;

import me.whereareiam.identica.Serializer;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract runner class for Identica identity implementations.
 * Platform-specific modules extend this with concrete implementations.
 */
@SuppressWarnings("unused")
public abstract class Identity extends ConnectionIdentity implements Actor {
	@NotNull
	protected final UUID uniqueId;

	protected Identity(@NotNull UUID uniqueId, @NotNull String username) {
		this(uniqueId, uniqueId, username, null, null);
	}

	protected Identity(@NotNull UUID uniqueId, @NotNull String username, @Nullable String ip) {
		this(uniqueId, uniqueId, username, ip, null);
	}

	protected Identity(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@NotNull String username,
			@Nullable String ip
	) {
		this(connectionUniqueId, accountUniqueId, username, ip, null);
	}

	protected Identity(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@NotNull String username,
			@Nullable String ip,
			@Nullable Origin origin
	) {
		super(username, ip);
		this.uniqueId = connectionUniqueId;
		setConnectionUniqueId(connectionUniqueId);
		setAccountUniqueId(accountUniqueId);
		setOrigin(origin);
	}

	/**
	 * Returns the Identica unique id for this identity.
	 *
	 * @return unique identity id
	 */
	@Override
	public @NotNull UUID getUniqueId() {
		return uniqueId;
	}

	/**
	 * Sends a message to this identity.
	 *
	 * @param message message component to send
	 */
	@Override
	public abstract void sendMessage(@NotNull Component message);

	/**
	 * Sends a message text to this identity, rendered for it with Identica's serializer. Use it rather than
	 * {@link #sendMessage(Component)} for text Identica or a provider answers the player with, so that a platform can
	 * defer it to the moment the player's client can show it: a player held on the loading screen sees it once
	 * their client joined a server's world, through the same queue as Identica's prompts.
	 *
	 * <pre>{@code
	 * identity.send("<red>Wrong password, {attempts} attempts left", Map.of("attempts", "2"));
	 * }</pre>
	 *
	 * @param message message text; a blank text sends nothing
	 * @param placeholders placeholder values keyed by placeholder name without delimiters
	 */
	public void send(@NotNull String message, @NotNull Map<String, String> placeholders) {
		if (message.isBlank()) return;

		sendMessage(Serializer.serialize(this, message, placeholders));
	}

	/**
	 * Sends a message text without placeholders to this identity, as {@link #send(String, Map)} does.
	 *
	 * @param message message text; a blank text sends nothing
	 */
	public void send(@NotNull String message) {
		send(message, Map.of());
	}

	/**
	 * Sends a title to this identity.
	 *
	 * @param title title payload
	 */
	public abstract void sendTitle(@NotNull Title title);

	/**
	 * Checks whether this identity has the specified permission.
	 *
	 * @param permission permission node
	 * @return {@code true} when the permission is granted
	 */
	@Override
	public abstract boolean hasPermission(@NotNull String permission);

	/**
	 * Returns the locale for this identity.
	 *
	 * @return locale associated with this identity
	 */
	@Override
	public abstract @NotNull Locale getLocale();

	/**
	 * Returns the Adventure audience for this identity.
	 *
	 * @return audience instance
	 */
	@Override
	public abstract @NotNull Audience getAudience();

	/**
	 * Disconnects this identity from the server.
	 *
	 * @param reason disconnect message
	 */
	public abstract void disconnect(@NotNull Component reason);
}

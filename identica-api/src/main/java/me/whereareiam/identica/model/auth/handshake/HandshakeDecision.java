package me.whereareiam.identica.model.auth.handshake;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.model.HandshakeAttributeKey;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Outcome of the handshake policies for one connection.
 *
 * <p>An allowed handshake can carry attributes for the platform, such as a request for an online-mode login. They
 * apply to this handshake only; a policy that wants to influence a later handshake queues a
 * {@link HandshakeInstruction} instead.</p>
 *
 * <pre>{@code
 * HandshakeDecision decision = HandshakeDecision.allow()
 *         .withAttribute(PremiumHandshakeAttributes.FORCE_ONLINE, true);
 * }</pre>
 */
@Getter
@ToString
@RequiredArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
public class HandshakeDecision {
	private final Status status;
	private final String message;
	private final String reason;
	private final Map<String, String> attributes;

	/**
	 * Creates a decision that allows the connection.
	 *
	 * @return allow decision without attributes
	 */
	public static HandshakeDecision allow() {
		return new HandshakeDecision(Status.ALLOW, null, null, Map.of());
	}

	/**
	 * Creates a decision that refuses the connection.
	 *
	 * @param message message shown to the player
	 * @return deny decision without attributes
	 */
	public static HandshakeDecision deny(String message) {
		return new HandshakeDecision(Status.DENY, message, null, Map.of());
	}

	/**
	 * Returns the encoded attributes for the platform, by attribute id.
	 *
	 * @return attributes, empty when the decision carries none
	 */
	public @NotNull Map<String, String> getAttributes() {
		return attributes != null ? attributes : Map.of();
	}

	/**
	 * Returns a copy of this decision with the attribute set.
	 *
	 * @param key attribute key
	 * @param value attribute value
	 * @param <T> attribute type
	 * @return decision with the attribute
	 */
	public <T> @NotNull HandshakeDecision withAttribute(@NotNull HandshakeAttributeKey<T> key, @NotNull T value) {
		return withAttributes(Map.of(key.id(), key.encoder().apply(value)));
	}

	/**
	 * Returns a copy of this decision with the encoded attributes added, replacing attributes with the same id.
	 *
	 * @param attributes encoded attributes by attribute id
	 * @return decision with the attributes
	 */
	public @NotNull HandshakeDecision withAttributes(@NotNull Map<String, String> attributes) {
		if (attributes.isEmpty()) return this;

		Map<String, String> merged = new HashMap<>(getAttributes());
		merged.putAll(attributes);

		return new HandshakeDecision(status, message, reason, Map.copyOf(merged));
	}

	/**
	 * Returns the decoded value of an attribute.
	 *
	 * @param key attribute key
	 * @param <T> attribute type
	 * @return attribute value, or empty when the decision does not carry it
	 */
	public <T> @NotNull Optional<T> getAttribute(@NotNull HandshakeAttributeKey<T> key) {
		String payload = getAttributes().get(key.id());
		if (payload == null) return Optional.empty();

		return Optional.ofNullable(key.decoder().apply(payload));
	}

	public enum Status {
		ALLOW,
		DENY
	}
}

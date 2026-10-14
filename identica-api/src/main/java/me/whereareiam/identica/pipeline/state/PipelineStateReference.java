package me.whereareiam.identica.pipeline.state;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.pipeline.ScenarioContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The connection a pipeline state belongs to: the username the connection was identified by and the address it
 * came from. The hostname the client connected through is not part of it.
 *
 * <p>Login progress belongs to the connection that made it. A player who returns with the same username from the
 * same address continues where she left, on any proxy that shares the states; a client with the same username from
 * another address has a state of its own. Unique ids are not part of the reference: two clients with one username
 * get the same offline unique id, so an id cannot tell them apart.</p>
 *
 * <pre>{@code
 * PipelineState state = pipelineStateStore.find(PipelineStateReference.from(identity)).orElse(null);
 * }</pre>
 */
@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PipelineStateReference {
	private static final PipelineStateReference EMPTY = new PipelineStateReference("", "");

	/**
	 * Username of the connection, in lower case.
	 */
	private final @NotNull String username;
	/**
	 * Address of the connection, empty when unknown.
	 */
	private final @NotNull String ip;

	/**
	 * Creates the reference of a connection.
	 *
	 * @param username username the connection was identified by
	 * @param ip address of the connection, or {@code null} when unknown
	 * @return reference, empty when the username is blank
	 */
	public static @NotNull PipelineStateReference of(@Nullable String username, @Nullable String ip) {
		if (username == null || username.isBlank()) return EMPTY;

		return new PipelineStateReference(username.trim().toLowerCase(Locale.ROOT), ip == null ? "" : ip.trim());
	}

	/**
	 * Creates the reference of the connection an identity describes, under the username it was identified by.
	 *
	 * @param identity connection identity, or {@code null}
	 * @return reference, empty without an identity
	 */
	public static @NotNull PipelineStateReference from(@Nullable ConnectionIdentity identity) {
		if (identity == null) return EMPTY;

		return of(identity.getConnectionUsername(), identity.getIp());
	}

	/**
	 * Creates the reference of the connection a scenario runs for.
	 *
	 * @param context scenario context
	 * @return reference
	 */
	public static @NotNull PipelineStateReference from(@NotNull ScenarioContext context) {
		return from(context.getIdentity());
	}

	/**
	 * Creates the reference of the connection a resume request comes from.
	 *
	 * @param request resume request
	 * @return reference, empty when the request has no identity
	 */
	public static @NotNull PipelineStateReference from(@NotNull ResumeRequest request) {
		return from(request.getIdentity());
	}

	/**
	 * Creates the reference of the connection an advance request comes from.
	 *
	 * @param request advance request
	 * @return reference, empty when the request has no identity
	 */
	public static @NotNull PipelineStateReference from(@NotNull AdvanceRequest request) {
		return from(request.getIdentity());
	}

	/**
	 * Returns whether the reference names no connection.
	 *
	 * @return {@code true} without a username
	 */
	public boolean isEmpty() {
		return username.isBlank();
	}

	/**
	 * Returns the key a store files the state under.
	 *
	 * @return username and address joined by {@code |}
	 */
	public @NotNull String key() {
		return username + "|" + ip;
	}
}

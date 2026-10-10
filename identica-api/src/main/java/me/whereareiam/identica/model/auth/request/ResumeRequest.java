package me.whereareiam.identica.model.auth.request;

import lombok.*;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.IdentityReference;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.state.PipelineInput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Resume request details for pending authentication journeys.
 *
 * <p>The resume request should include the latest connection identity so pending
 * journeyModes can re-evaluate IP-sensitive steps. When {@code identity}
 * is omitted, the stored context data is reused.</p>
 */
@Getter
@ToString
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("unused")
public class ResumeRequest {
	@Builder.Default
	private final @NotNull IdentityReference identityReference = new IdentityReference();
	private final @Nullable ConnectionIdentity identity;
	private final @Nullable ProviderContext provider;
	private final @Nullable String intendedServer;
	/**
	 * Input from the command that sent this request, applied to the player's state at the start of the run.
	 */
	private final @Nullable PipelineInput input;

	/**
	 * Returns the connection identity for this resume request.
	 *
	 * @return connection identity or {@code null}
	 */
	public @Nullable ConnectionIdentity getIdentity() {
		return identity;
	}

	/**
	 * Returns the stable identity unique id for this resume request.
	 *
	 * @return identity unique id or {@code null}
	 */
	public @Nullable UUID getConnectionUniqueId() {
		if (identityReference.getConnectionUniqueId() != null)
			return identityReference.getConnectionUniqueId();

		return identity != null ? identity.getConnectionUniqueId() : null;
	}

	public @Nullable java.util.UUID getAccountUniqueId() {
		if (identityReference.getAccountUniqueId() != null)
			return identityReference.getAccountUniqueId();

		return identity != null ? identity.getAccountUniqueId() : null;
	}

	public @NotNull IdentityReference getIdentityReference() {
		if (identity == null)
			return identityReference;

		if (identityReference.getConnectionUniqueId() == null)
			identityReference.setConnectionUniqueId(identity.getConnectionUniqueId());
		if (identityReference.getObservedUniqueId() == null)
			identityReference.setObservedUniqueId(identity.getObservedUniqueId());
		if (identityReference.getAccountUniqueId() == null)
			identityReference.setAccountUniqueId(identity.getAccountUniqueId());

		return identityReference;
	}

	/**
	 * Returns the username for this resume request.
	 *
	 * @return username or {@code null}
	 */
	public @Nullable String getUsername() {
		return identity != null ? identity.getUsername() : null;
	}

	/**
	 * Returns the IP address for this resume request.
	 *
	 * @return IP address or {@code null}
	 */
	public @Nullable String getIp() {
		return identity != null ? identity.getIp() : null;
	}

	/**
	 * Returns whether this request supplies connection identity.
	 *
	 * @return {@code true} if connection identity is present
	 */
	public boolean hasIdentity() {
		return identity != null;
	}

	/**
	 * Returns the connection identity for this request.
	 *
	 * @return connection identity or {@code null}
	 */
	public @Nullable ConnectionIdentity getIdentityInfo() {
		return identity;
	}

	public @Nullable ProviderContext getProvider() {
		return provider;
	}

	/**
	 * Returns the intended server name for this request.
	 *
	 * @return server name or {@code null}
	 */
	public @Nullable String getIntendedServer() {
		return intendedServer;
	}

	/**
	 * Converts this resume request into a connection request when identity is present.
	 *
	 * @return converted connection request or {@code null}
	 */
	public @Nullable ConnectionRequest toConnectionRequest() {
		if (identity == null) return null;

		return ConnectionRequest.builder()
				.identityReference(identityReference)
				.identity(identity)
				.provider(provider)
				.intendedServer(intendedServer)
				.build();
	}

	public static class ResumeRequestBuilder {
		private final IdentityReference identityReference = new IdentityReference();

		public @NotNull ResumeRequestBuilder connectionUniqueId(@Nullable java.util.UUID connectionUniqueId) {
			identityReference.setConnectionUniqueId(connectionUniqueId);
			return this;
		}

		public @NotNull ResumeRequestBuilder accountUniqueId(@Nullable java.util.UUID accountUniqueId) {
			identityReference.setAccountUniqueId(accountUniqueId);
			return this;
		}

		public @NotNull ResumeRequest build() {
			return new ResumeRequest(identityReference, identity, provider, intendedServer, input);
		}
	}
}

package me.whereareiam.identica.model.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provider that handles a connection right now, with the subject and username it uses.
 *
 * <p>Steps of the running provider fill in its subject. Which provider runs, and why, is decided by the
 * connection's {@link ProviderSelection}.</p>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ProviderContext {
	private @Nullable String providerId;
	private @Nullable String providerSubject;
	private @NotNull String providerUsername;

	/**
	 * Creates the context of a running provider.
	 *
	 * @param providerId provider id
	 * @param providerSubject subject the provider uses for the connection, if known
	 * @param providerUsername username the provider uses for the connection
	 * @return context, or {@code null} when the provider id is blank
	 */
	public static @Nullable ProviderContext of(
			@Nullable String providerId,
			@Nullable String providerSubject,
			@Nullable String providerUsername
	) {
		if (providerId == null || providerId.isBlank()) return null;

		return ProviderContext.builder()
				.providerId(providerId)
				.providerSubject(providerSubject)
				.providerUsername(providerUsername == null ? "" : providerUsername)
				.build();
	}
}

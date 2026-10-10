package me.whereareiam.identica.model.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
	 * Source of provider selection for this context.
	 */
	private @Nullable ProviderOrigin source;
	/**
	 * Whether the provider verified, for this connection, that the client owns {@link #providerSubject}.
	 * It is carried over from {@code SubjectResolution#isVerified()} and is {@code false} for a provider
	 * that was only selected.
	 */
	private boolean subjectVerified;

	public static @Nullable ProviderContext of(
			@Nullable String providerId,
			@Nullable String providerSubject,
			@Nullable String providerUsername,
			@Nullable ProviderOrigin source
	) {
		if (providerId == null || providerId.isBlank()) return null;

		return ProviderContext.builder()
				.providerId(providerId)
				.providerSubject(providerSubject)
				.providerUsername(providerUsername == null ? "" : providerUsername)
				.source(source)
				.build();
	}
}

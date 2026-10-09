package me.whereareiam.identica.trait.authoritative.username.conflict;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.conflict.ConflictGuard;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.conflict.ConflictContext;
import me.whereareiam.identica.model.conflict.ConflictResolution;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.trait.authoritative.username.UsernameConflictSchema;
import me.whereareiam.identica.trait.authoritative.username.model.AuthoritativeUsernameMessages;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UsernameEntrypointGuard implements ConflictGuard {
	private final Provider<AuthoritativeUsernameMessages> messagesProvider;
	private final ProviderOperations providerOperations;

	@Override
	public @Nullable ConflictResolution guard(@NotNull ConflictContext context) {
		if (!"username".equalsIgnoreCase(context.getKey())) return null;

		String incomingProvider = context.getParticipantAttribute(
				UsernameConflictSchema.ROLE_INCOMING,
				UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_ID
		);
		String existingProvider = context.getParticipantAttribute(
				UsernameConflictSchema.ROLE_EXISTING,
				UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_ID
		);
		if (incomingProvider == null || existingProvider == null) return null;
		if (incomingProvider.equalsIgnoreCase(existingProvider)) return null;

		ProviderOrigin source = context.getAttribute(
				UsernameConflictSchema.ATTRIBUTE_ENTRYPOINT_SOURCE
		);
		if (source == ProviderOrigin.ENTRYPOINT) {
			Logger.debug("Username conflict accepted by entrypoint incoming=%s existing=%s",
					incomingProvider,
					existingProvider);
			return null;
		}

		if (!providerOperations.hasEntrypoints(incomingProvider)
				|| !providerOperations.hasEntrypoints(existingProvider))
			return null;

		Logger.debug("Username conflict requires entrypoint incoming=%s existing=%s",
				incomingProvider,
				existingProvider);
		return ConflictResolution.deny(resolveEntrypointMessage(incomingProvider, existingProvider));
	}

	private @Nullable String resolveEntrypointMessage(
			@Nullable String incomingProvider,
			@Nullable String existingProvider
	) {
		List<String> lines = messagesProvider.get().getPipeline().getPolicy().getEntrypointRequired();
		if (lines.isEmpty()) return null;

		return Serializer.render(String.join("\n", lines), Map.of(
				"incomingProvider", Objects.toString(providerOperations.displayProviderName(incomingProvider), ""),
				"existingProvider", Objects.toString(providerOperations.displayProviderName(existingProvider), ""),
				"incomingProviderId", Objects.toString(incomingProvider, ""),
				"existingProviderId", Objects.toString(existingProvider, ""),
				"incomingHost", Objects.toString(providerOperations.displayEntrypoint(incomingProvider), ""),
				"existingHost", Objects.toString(providerOperations.displayEntrypoint(existingProvider), "")
		));
	}
}

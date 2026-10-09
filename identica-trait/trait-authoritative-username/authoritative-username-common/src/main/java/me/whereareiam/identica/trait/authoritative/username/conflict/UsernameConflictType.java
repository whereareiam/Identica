package me.whereareiam.identica.trait.authoritative.username.conflict;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.conflict.ConflictGuard;
import me.whereareiam.identica.conflict.ConflictType;
import me.whereareiam.identica.conflict.resolver.ConflictResolver;
import me.whereareiam.identica.model.conflict.ConflictContext;
import me.whereareiam.identica.model.config.provider.Conflicts;
import me.whereareiam.identica.trait.authoritative.username.config.provider.UsernameConflictsProvider;
import me.whereareiam.identica.trait.authoritative.username.UsernameConflictSchema;
import me.whereareiam.identica.trait.authoritative.username.conflict.factory.UsernameConflictContextFactory;
import me.whereareiam.identica.trait.authoritative.username.conflict.resolver.UsernameFormatResolver;
import me.whereareiam.identica.trait.authoritative.username.model.conflict.UsernameConflictSubject;
import me.whereareiam.identica.type.ConflictHook;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UsernameConflictType implements ConflictType<UsernameConflictSubject> {
	private final UsernameConflictContextFactory contextFactory;
	private final UsernameFormatResolver formatResolver;
	private final UsernameEntrypointGuard entrypointGuard;
	private final UsernameConflictsProvider conflictsProvider;

	@Override
	public @NotNull String getKey() {
		return "username";
	}

	@Override
	public @Nullable Conflicts.ConflictRules getDefaultRules() {
		return conflictsProvider.get().getRules().get(getKey());
	}

	@Override
	public @NotNull ConflictHook getDefaultHook() {
		return ConflictHook.PREPARE;
	}

	@Override
	public @NotNull List<ConflictResolver> getResolvers() {
		return List.of(formatResolver);
	}

	@Override
	public @NotNull List<ConflictGuard> getGuards() {
		return List.of(entrypointGuard);
	}

	@Override
	public @Nullable ConflictContext createContext(@NotNull UsernameConflictSubject subject) {
		return contextFactory.create(subject);
	}

	@Override
	public boolean matchesRule(@NotNull ConflictContext context, @NotNull JsonNode when) {
		JsonNode providers = when.path("providers");
		if (!providers.isArray() || providers.isEmpty()) return false;

		String incomingProvider = context.getParticipantAttribute(
				UsernameConflictSchema.ROLE_INCOMING,
				UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_ID
		);
		String existingProvider = context.getParticipantAttribute(
				UsernameConflictSchema.ROLE_EXISTING,
				UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_ID
		);
		if (incomingProvider == null || existingProvider == null) return false;

		boolean hasIncoming = false;
		boolean hasExisting = false;
		for (JsonNode providerNode : providers) {
			String provider = providerNode != null ? providerNode.asText() : "";
			if (provider.isBlank()) continue;
			if (provider.equalsIgnoreCase(incomingProvider)) hasIncoming = true;
			if (provider.equalsIgnoreCase(existingProvider)) hasExisting = true;
		}

		return hasIncoming && hasExisting;
	}
}

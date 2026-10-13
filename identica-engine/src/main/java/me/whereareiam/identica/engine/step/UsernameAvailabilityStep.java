package me.whereareiam.identica.engine.step;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.model.config.UsernameMessages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.type.SeamlessStep;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.provider.ProviderTrait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Refuses to register a username that another account holds without regard to case, before the provider's own
 * registration steps run. Usernames are unique like Minecraft's: {@code alice} cannot register while {@code Alice}
 * exists. A provider that vouches for its usernames, with {@link ProviderTrait#AUTHORITATIVE_USERNAME}, is never
 * refused: its player owns the name, and gets an account that shares it.
 */
@Singleton
public class UsernameAvailabilityStep extends SeamlessStep {
	/** Runs before the steps of every provider, which order theirs from 0 on. */
	private static final int ORDER = -100;

	private final AccountPersistenceService accountPersistenceService;
	private final ProviderManager providerManager;
	private final Provider<UsernameMessages> messagesProvider;

	@Inject
	public UsernameAvailabilityStep(
			AccountPersistenceService accountPersistenceService,
			ProviderManager providerManager,
			Provider<UsernameMessages> messagesProvider
	) {
		super("username-availability");
		this.accountPersistenceService = accountPersistenceService;
		this.providerManager = providerManager;
		this.messagesProvider = messagesProvider;
	}

	@Override
	public int order() {
		return ORDER;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		String username = context.getUsername();
		if (username == null || username.isBlank() || vouchesForUsernames(context.getProvider()))
			return CompletableFuture.completedFuture(StepResult.proceed(context));

		UUID uniqueId = context.getAccountUniqueId();
		boolean taken = accountPersistenceService.findByUsername(username).stream()
				.map(Account::getUniqueId)
				.anyMatch(holder -> !holder.equals(uniqueId));
		if (!taken) return CompletableFuture.completedFuture(StepResult.proceed(context));

		return CompletableFuture.completedFuture(StepResult.denied(takenMessage(username)));
	}

	private boolean vouchesForUsernames(@Nullable ProviderContext provider) {
		if (provider == null || provider.getProviderId() == null) return false;

		return providerManager.findProvidersByTraits(ProviderTrait.AUTHORITATIVE_USERNAME).stream()
				.map(InternalProvider::getDescriptor)
				.filter(Objects::nonNull)
				.anyMatch(descriptor -> descriptor.getId().equalsIgnoreCase(provider.getProviderId()));
	}

	private @NotNull String takenMessage(@NotNull String username) {
		String lines = String.join("\n", messagesProvider.get().getPipeline().getPolicy().getTaken());

		return Serializer.render(lines, Map.of("username", username));
	}
}

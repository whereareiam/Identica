package me.whereareiam.identica.common.identity.username;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.username.AccountUsernameDuplicate;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.username.AccountUsernameDuplicatePersistenceService;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Warns in the console about each username that several accounts share without regard to case. Such accounts keep
 * logging in through their own provider links, so the warning never stops the startup.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UsernameDuplicateWarning {
	private final AccountUsernameDuplicatePersistenceService duplicatePersistenceService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;

	/**
	 * Logs one warning per shared username, naming its accounts with their unique ids, providers and last logins.
	 */
	public void warn() {
		for (AccountUsernameDuplicate duplicate : duplicatePersistenceService.findAll())
			Logger.warn("Duplicate usernames: %s is shared by %s. Each account logs in through its own provider; "
							+ "/identica admin username duplicates lists them.",
					duplicate.getUsername(),
					duplicate.getAccounts().stream().map(this::describe).collect(Collectors.joining(", ")));
	}

	private @NotNull String describe(@NotNull Account account) {
		String providers = providerLinkPersistenceService.findByUniqueId(account.getUniqueId()).stream()
				.map(AccountProviderLink::getProviderId)
				.collect(Collectors.joining("/"));
		String lastSeen = account.getLastSeenAt() > 0 ? Instant.ofEpochMilli(account.getLastSeenAt()).toString() : "never";

		return "%s (%s, providers %s, last seen %s)".formatted(
				account.getUsername(),
				account.getUniqueId(),
				providers.isEmpty() ? "none" : providers,
				lastSeen
		);
	}
}

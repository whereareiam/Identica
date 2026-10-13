package me.whereareiam.identica.adapter.database.username;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameEntity;
import me.whereareiam.identica.adapter.database.mapper.account.AccountMapper;
import me.whereareiam.identica.adapter.database.mapper.username.UsernameMapper;
import me.whereareiam.identica.adapter.database.repository.username.UsernameRepository;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.username.AccountUsernameDuplicate;
import me.whereareiam.identica.persistence.username.AccountUsernameDuplicatePersistenceService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Finds shared usernames from the usernames without a key: every account that shares its username with an older one
 * holds none, so each shared username has at least one of them.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultAccountUsernameDuplicatePersistenceService implements AccountUsernameDuplicatePersistenceService {
	private static final Comparator<Account> OLDEST_FIRST = Comparator
			.comparingLong((Account account) -> account.getCreatedAt() > 0 ? account.getCreatedAt() : Long.MAX_VALUE)
			.thenComparing(account -> account.getUniqueId().toString());

	private final Provider<EntityAccess<AccountEntity>> accounts;
	private final Provider<UsernameRepository> usernameRepository;

	@Override
	public @NotNull List<AccountUsernameDuplicate> findAll() {
		try {
			Map<String, String> usernames = new TreeMap<>();
			for (UsernameEntity keyless : usernameRepository.get().findWithoutUsernameKey())
				usernames.putIfAbsent(UsernameMapper.usernameKey(keyless.getUsername()), keyless.getUsername());

			List<AccountUsernameDuplicate> duplicates = new ArrayList<>();
			usernames.forEach((key, username) -> {
				List<Account> sharing = accountsOf(username);
				if (sharing.size() < 2) return;

				duplicates.add(AccountUsernameDuplicate.builder()
						.username(key)
						.accounts(sharing)
						.build());
			});

			return duplicates;
		} catch (Exception e) {
			Logger.warn(e, "Failed to find usernames shared by several accounts");
			return List.of();
		}
	}

	private List<Account> accountsOf(String username) {
		return usernameRepository.get().findByUsername(username).stream()
				.flatMap(row -> accounts.get().find(row.getUniqueId()).map(entity -> AccountMapper.toModel(entity, row)).stream())
				.sorted(OLDEST_FIRST)
				.toList();
	}
}

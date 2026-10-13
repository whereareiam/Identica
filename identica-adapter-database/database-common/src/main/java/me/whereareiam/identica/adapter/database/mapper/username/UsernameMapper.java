package me.whereareiam.identica.adapter.database.mapper.username;

import me.whereareiam.identica.adapter.database.entity.username.UsernameEntity;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.username.AccountUsername;
import me.whereareiam.identica.type.identity.AccountUsernameSource;

import java.util.Locale;

public final class UsernameMapper {
	/**
	 * Returns the key of a username that no two accounts share: the username in lower case, as Java lowers it, because
	 * SQLite lowers only ASCII letters.
	 */
	public static String usernameKey(String username) {
		return username.toLowerCase(Locale.ROOT);
	}

	public static AccountUsername toModel(UsernameEntity entity) {
		return AccountUsername.builder()
				.uniqueId(entity.getUniqueId())
				.username(entity.getUsername())
				.source(AccountUsernameSource.fromId(entity.getSource()))
				.providerId(entity.getProviderId())
				.changedAt(entity.getChangedAt())
				.build();
	}

	/**
	 * Returns the username row of a new account: it holds the key of its username.
	 */
	public static UsernameEntity toEntity(Account account, AccountUsernameSource source, String providerId) {
		return UsernameEntity.builder()
				.uniqueId(account.getUniqueId())
				.username(account.getUsername())
				.usernameKey(usernameKey(account.getUsername()))
				.source(source.getId())
				.providerId(providerId)
				.changedAt(account.getCreatedAt())
				.build();
	}
}

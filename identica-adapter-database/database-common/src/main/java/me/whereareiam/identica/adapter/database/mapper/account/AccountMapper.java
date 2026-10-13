package me.whereareiam.identica.adapter.database.mapper.account;

import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameEntity;
import me.whereareiam.identica.model.identity.Account;

import java.util.Objects;

public final class AccountMapper {
	public static Account toModel(AccountEntity entity, UsernameEntity username) {
		return Account.builder()
				.uniqueId(entity.getUniqueId())
				.username(username.getUsername())
				.createdAt(Objects.requireNonNullElse(entity.getCreatedAt(), 0L))
				.lastSeenAt(Objects.requireNonNullElse(entity.getLastSeenAt(), 0L))
				.build();
	}

	public static AccountEntity toEntity(Account account) {
		return AccountEntity.builder()
				.uniqueId(account.getUniqueId())
				.createdAt(account.getCreatedAt())
				.lastSeenAt(account.getLastSeenAt())
				.build();
	}
}

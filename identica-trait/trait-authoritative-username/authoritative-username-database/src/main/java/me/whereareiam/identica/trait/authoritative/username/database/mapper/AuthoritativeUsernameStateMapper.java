package me.whereareiam.identica.trait.authoritative.username.database.mapper;

import me.whereareiam.identica.trait.authoritative.username.database.entity.AuthoritativeUsernameStateEntity;
import me.whereareiam.identica.trait.authoritative.username.model.account.AccountUsernameState;
import me.whereareiam.identica.trait.authoritative.username.type.AccountUsernameSource;

public final class AuthoritativeUsernameStateMapper {
	public static AccountUsernameState toModel(AuthoritativeUsernameStateEntity entity) {
		if (entity == null) return null;
		return AccountUsernameState.builder()
				.uniqueId(entity.getUniqueId())
				.source(AccountUsernameSource.fromId(entity.getSource()))
				.updatedAt(entity.getUpdatedAt())
				.build();
	}
}

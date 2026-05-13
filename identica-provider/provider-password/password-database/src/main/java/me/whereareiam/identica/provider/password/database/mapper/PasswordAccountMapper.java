package me.whereareiam.identica.provider.password.database.mapper;

import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.database.entity.PasswordAccountEntity;

public final class PasswordAccountMapper {
	public static PasswordAccount toModel(PasswordAccountEntity entity) {
		if (entity == null) return null;
		return PasswordAccount.builder()
				.providerId(entity.getProviderId())
				.providerSubject(entity.getProviderSubject())
				.passwordHash(entity.getPasswordHash())
				.hashingMethod(entity.getHashingMethod())
				.createdAt(entity.getCreatedAt())
				.updatedAt(entity.getUpdatedAt())
				.build();
	}

	public static PasswordAccountEntity toEntity(PasswordAccount account) {
		if (account == null) return null;
		return PasswordAccountEntity.builder()
				.providerId(account.getProviderId())
				.providerSubject(account.getProviderSubject())
				.passwordHash(account.getPasswordHash())
				.hashingMethod(account.getHashingMethod())
				.createdAt(account.getCreatedAt())
				.updatedAt(account.getUpdatedAt())
				.build();
	}
}

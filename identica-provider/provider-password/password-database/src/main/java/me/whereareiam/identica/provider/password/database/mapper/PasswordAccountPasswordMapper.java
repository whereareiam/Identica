package me.whereareiam.identica.provider.password.database.mapper;

import me.whereareiam.identica.provider.password.model.PasswordAccountPassword;
import me.whereareiam.identica.provider.password.database.entity.PasswordAccountPasswordEntity;

public final class PasswordAccountPasswordMapper {
	public static PasswordAccountPasswordEntity toEntity(PasswordAccountPassword change) {
		if (change == null) return null;
		return PasswordAccountPasswordEntity.builder()
				.providerId(change.getProviderId())
				.providerSubject(change.getProviderSubject())
				.hashingMethod(change.getHashingMethod())
				.changeReason(change.getChangeReason().name())
				.changedAt(change.getChangedAt())
				.build();
	}
}

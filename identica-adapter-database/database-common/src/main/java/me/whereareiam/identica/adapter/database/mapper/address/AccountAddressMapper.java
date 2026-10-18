package me.whereareiam.identica.adapter.database.mapper.address;

import me.whereareiam.identica.adapter.database.entity.address.AccountAddressEntity;
import me.whereareiam.identica.model.identity.address.AccountAddress;

public final class AccountAddressMapper {
	public static AccountAddress toModel(AccountAddressEntity entity) {
		return AccountAddress.builder()
				.uniqueId(entity.getUniqueId())
				.address(entity.getAddress())
				.network(entity.getNetwork())
				.registration(entity.isRegistration())
				.firstSeenAt(entity.getFirstSeenAt())
				.lastSeenAt(entity.getLastSeenAt())
				.build();
	}

	public static AccountAddressEntity toEntity(AccountAddress address) {
		return AccountAddressEntity.builder()
				.uniqueId(address.getUniqueId())
				.address(address.getAddress())
				.network(address.getNetwork())
				.registration(address.isRegistration())
				.firstSeenAt(address.getFirstSeenAt())
				.lastSeenAt(address.getLastSeenAt())
				.build();
	}
}

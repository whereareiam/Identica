package me.whereareiam.identica.adapter.database.entity.address;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.whereareiam.dialectica.entity.annotation.Column;
import me.whereareiam.dialectica.entity.annotation.Id;
import me.whereareiam.dialectica.entity.annotation.Index;
import me.whereareiam.dialectica.entity.annotation.Length;
import me.whereareiam.dialectica.entity.annotation.References;
import me.whereareiam.dialectica.entity.annotation.Table;
import me.whereareiam.dialectica.schema.type.ReferentialAction;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;

import java.util.UUID;

/**
 * An address an account registered or logged in from, one row per account and address, in its stored form: written
 * out or hashed. The group of addresses that count as one is kept beside it and indexed, because the limit on
 * accounts per address counts by it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Table(AccountAddressEntity.TABLE)
@Index(name = "identica_account_addresses_network_idx", columns = "network")
public class AccountAddressEntity {
	/** Name of the table. */
	public static final String TABLE = "identica_account_addresses";

	@Id
	@References(entity = AccountEntity.class, onDelete = ReferentialAction.CASCADE)
	private UUID uniqueId;
	@Id
	@Length(64)
	private String address;
	@Length(64)
	private String network;
	@Column("is_registration")
	private boolean registration;
	private long firstSeenAt;
	private long lastSeenAt;
}

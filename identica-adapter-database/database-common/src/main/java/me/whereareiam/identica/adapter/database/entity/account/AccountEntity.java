package me.whereareiam.identica.adapter.database.entity.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.whereareiam.dialectica.entity.annotation.Id;
import me.whereareiam.dialectica.entity.annotation.Nullable;
import me.whereareiam.dialectica.entity.annotation.Table;
import me.whereareiam.identica.database.schema.AccountTable;

import java.util.UUID;

/**
 * An account; its username is a {@link me.whereareiam.identica.adapter.database.entity.username.UsernameEntity}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Table(AccountTable.NAME)
public class AccountEntity {
	@Id
	private UUID uniqueId;
	@Nullable
	private Long createdAt;
	@Nullable
	private Long lastSeenAt;
}

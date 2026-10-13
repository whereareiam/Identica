package me.whereareiam.identica.adapter.database.entity.username;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.whereareiam.dialectica.entity.annotation.Id;
import me.whereareiam.dialectica.entity.annotation.Index;
import me.whereareiam.dialectica.entity.annotation.Length;
import me.whereareiam.dialectica.entity.annotation.Nullable;
import me.whereareiam.dialectica.entity.annotation.References;
import me.whereareiam.dialectica.entity.annotation.Table;
import me.whereareiam.dialectica.schema.type.ReferentialAction;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;

import java.util.UUID;

/**
 * The current username of an account, one row per account. Its key, the username in lower case, is unique, so no
 * two accounts hold one username in different cases; an account from before usernames were unique that shares its
 * username with an older one has no key.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Table(UsernameEntity.TABLE)
@Index(name = "identica_account_usernames_username_key_idx", columns = "usernameKey", unique = true)
public class UsernameEntity {
	/** Name of the table. */
	public static final String TABLE = "identica_account_usernames";
	/** Column of the username in lower case. */
	public static final String USERNAME_KEY = "username_key";

	@Id
	@References(entity = AccountEntity.class, onDelete = ReferentialAction.CASCADE)
	private UUID uniqueId;
	@Length(64)
	private String username;
	@Nullable
	@Length(64)
	private String usernameKey;
	@Length(32)
	private String source;
	@Nullable
	@Length(64)
	private String providerId;
	private long changedAt;
}

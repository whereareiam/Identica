package me.whereareiam.identica.model.identity.username;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.type.identity.username.UsernameRenameTaken;
import me.whereareiam.identica.type.identity.username.UsernameRenameTooLong;
import org.jetbrains.annotations.NotNull;

/**
 * How a username is renamed when a conflict takes it away from an account, the settings of
 * {@code rename-arriving}.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class UsernameRename {
	/**
	 * Pattern of the renamed name: {@code {username}} is the name being renamed and {@code {provider}} the id of the
	 * renamed account's provider. Characters Minecraft does not allow in a username are left out.
	 */
	private @NotNull String pattern = "{username}_{provider}";

	/**
	 * What happens when the renamed name is longer than 16 characters.
	 */
	private @NotNull UsernameRenameTooLong tooLong = UsernameRenameTooLong.SHORTEN;

	/**
	 * What happens when another account holds the renamed name.
	 */
	private @NotNull UsernameRenameTaken taken = UsernameRenameTaken.NUMBER;
}

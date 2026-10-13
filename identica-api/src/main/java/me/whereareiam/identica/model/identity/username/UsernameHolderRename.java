package me.whereareiam.identica.model.identity.username;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.type.identity.username.UsernameHolderOnline;
import org.jetbrains.annotations.NotNull;

/**
 * The settings of {@code rename-holder}: how the holder is renamed, and what happens to a holder who is online. The
 * arriving player's name for the session while the holder keeps playing is made the same way.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class UsernameHolderRename extends UsernameRename {
	/**
	 * What happens when the holder is online.
	 */
	private @NotNull UsernameHolderOnline holderOnline = UsernameHolderOnline.KEEP_PLAYING;
}

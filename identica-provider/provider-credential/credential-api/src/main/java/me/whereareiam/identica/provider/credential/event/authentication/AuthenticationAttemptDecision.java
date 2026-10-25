package me.whereareiam.identica.provider.credential.event.authentication;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class AuthenticationAttemptDecision {
	private boolean deny;
	private @Nullable PlayerMessage denyMessage;
	private @Nullable PlayerMessage warningMessage;

	public static AuthenticationAttemptDecision allow() {
		return new AuthenticationAttemptDecision(false, null, null);
	}
}

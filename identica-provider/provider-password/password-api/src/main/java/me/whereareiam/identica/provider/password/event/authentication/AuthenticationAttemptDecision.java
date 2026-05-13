package me.whereareiam.identica.provider.password.event.authentication;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class AuthenticationAttemptDecision {
	private boolean deny;
	private @Nullable String denyMessage;
	private @Nullable String warningMessage;

	public static AuthenticationAttemptDecision allow() {
		return new AuthenticationAttemptDecision(false, null, null);
	}
}

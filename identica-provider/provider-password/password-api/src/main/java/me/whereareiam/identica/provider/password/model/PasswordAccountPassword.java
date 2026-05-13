package me.whereareiam.identica.provider.password.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.provider.password.type.PasswordChangeReason;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PasswordAccountPassword {
	private @NotNull String providerId;
	private @NotNull String providerSubject;
	private @NotNull String hashingMethod;
	private @NotNull PasswordChangeReason changeReason;
	private long changedAt;
}

package me.whereareiam.identica.provider.credential.util;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.provider.credential.pipeline.CredentialAnswers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PasswordRules {
	private final Provider<CredentialSettings> settingsProvider;
	private final Provider<CredentialMessages> messagesProvider;

	/**
	 * Checks a new password against the settings.
	 *
	 * @param password the new password
	 * @return the first rule it breaks, or {@code null} when it keeps them all
	 */
	public @Nullable Violation check(@Nullable String password) {
		CredentialSettings settings = settingsProvider.get();
		if (settings == null || settings.getScenario() == null || settings.getScenario().getRegistration() == null)
			return null;

		CredentialSettings.Scenario.Registration.Password passwordSettings = settings.getScenario().getRegistration().getPassword();
		String value = password == null ? "" : password;
		int length = value.length();

		CredentialMessages.Password messages = messagesProvider.get().getPassword();

		int minLength = passwordSettings.getMinLength();
		if (minLength > 0 && length < minLength)
			return new Violation(CredentialAnswers.PASSWORD_TOO_SHORT, messages.getTooShort());

		int maxLength = passwordSettings.getMaxLength();
		if (maxLength > 0 && length > maxLength)
			return new Violation(CredentialAnswers.PASSWORD_TOO_LONG, messages.getTooLong());

		if (containsWhitespace(value))
			return new Violation(CredentialAnswers.PASSWORD_HAS_SPACES, messages.getNoSpaces());

		int minUpper = passwordSettings.getMinUpper();
		if (minUpper > 0 && countUpper(value) < minUpper)
			return new Violation(CredentialAnswers.PASSWORD_MISSING_UPPER, messages.getMissingUpper());

		int minLower = passwordSettings.getMinLower();
		if (minLower > 0 && countLower(value) < minLower)
			return new Violation(CredentialAnswers.PASSWORD_MISSING_LOWER, messages.getMissingLower());

		int minNumber = passwordSettings.getMinNumber();
		if (minNumber > 0 && countDigits(value) < minNumber)
			return new Violation(CredentialAnswers.PASSWORD_MISSING_NUMBER, messages.getMissingNumber());

		int minSpecial = passwordSettings.getMinSpecial();
		if (minSpecial > 0 && countSpecial(value) < minSpecial)
			return new Violation(CredentialAnswers.PASSWORD_MISSING_SPECIAL, messages.getMissingSpecial());

		return null;
	}

	private boolean containsWhitespace(String value) {
		for (int i = 0; i < value.length(); i++) {
			if (Character.isWhitespace(value.charAt(i)))
				return true;
		}
		return false;
	}

	private int countUpper(String value) {
		int count = 0;
		for (int i = 0; i < value.length(); i++) {
			if (Character.isUpperCase(value.charAt(i)))
				count++;
		}

		return count;
	}

	private int countLower(String value) {
		int count = 0;
		for (int i = 0; i < value.length(); i++) {
			if (Character.isLowerCase(value.charAt(i)))
				count++;
		}

		return count;
	}

	private int countDigits(String value) {
		int count = 0;
		for (int i = 0; i < value.length(); i++) {
			if (Character.isDigit(value.charAt(i)))
				count++;
		}

		return count;
	}

	private int countSpecial(String value) {
		int count = 0;
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (!Character.isLetterOrDigit(ch))
				count++;
		}

		return count;
	}

	/**
	 * A rule a new password breaks.
	 *
	 * @param answer the ID of the answer, one of {@link CredentialAnswers}
	 * @param message the message to the player
	 */
	public record Violation(@NotNull String answer, @NotNull String message) {
	}
}

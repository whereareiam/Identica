package me.whereareiam.identica.provider.credential.util;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.message.MessageDefinition;
import me.whereareiam.identica.provider.credential.CredentialMessageDefinitions;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import org.jetbrains.annotations.Nullable;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PasswordRules {
	private final Provider<CredentialSettings> settingsProvider;

	/**
	 * Checks a new password against the settings.
	 *
	 * @param password the new password
	 * @return the message of the first rule it breaks, or {@code null} when it keeps them all
	 */
	public @Nullable MessageDefinition<CredentialMessages> check(@Nullable String password) {
		CredentialSettings settings = settingsProvider.get();
		if (settings == null || settings.getScenario() == null || settings.getScenario().getRegistration() == null)
			return null;

		CredentialSettings.Scenario.Registration.Password passwordSettings = settings.getScenario().getRegistration().getPassword();
		String value = password == null ? "" : password;
		int length = value.length();

		int minLength = passwordSettings.getMinLength();
		if (minLength > 0 && length < minLength)
			return CredentialMessageDefinitions.PASSWORD_TOO_SHORT;

		int maxLength = passwordSettings.getMaxLength();
		if (maxLength > 0 && length > maxLength)
			return CredentialMessageDefinitions.PASSWORD_TOO_LONG;

		if (containsWhitespace(value))
			return CredentialMessageDefinitions.PASSWORD_HAS_SPACES;

		int minUpper = passwordSettings.getMinUpper();
		if (minUpper > 0 && countUpper(value) < minUpper)
			return CredentialMessageDefinitions.PASSWORD_MISSING_UPPER;

		int minLower = passwordSettings.getMinLower();
		if (minLower > 0 && countLower(value) < minLower)
			return CredentialMessageDefinitions.PASSWORD_MISSING_LOWER;

		int minNumber = passwordSettings.getMinNumber();
		if (minNumber > 0 && countDigits(value) < minNumber)
			return CredentialMessageDefinitions.PASSWORD_MISSING_NUMBER;

		int minSpecial = passwordSettings.getMinSpecial();
		if (minSpecial > 0 && countSpecial(value) < minSpecial)
			return CredentialMessageDefinitions.PASSWORD_MISSING_SPECIAL;

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
}

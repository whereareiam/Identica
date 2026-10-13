package me.whereareiam.identica.testing.journey;

import lombok.RequiredArgsConstructor;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.common.config.IdenticaModule;
import me.whereareiam.identica.example.provider.config.ExampleMessages;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionMessages;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelMessages;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.UsernameMessages;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The messages a running proxy uses, read from the files it generated into Identica's own models. A journey
 * expectation names a message through these models' getters, so it follows the configured text and a renamed
 * message no longer compiles.
 */
@RequiredArgsConstructor
public final class IdenticaMessages {
	/** Read as the plugin reads them, including Identica's own value types. */
	private static final Configura CONFIGURA = Config.builder().format(Format.YAML).module(new IdenticaModule()).build();

	private final Path dataDirectory;
	private final Map<String, Object> documents = new ConcurrentHashMap<>();

	/**
	 * Returns Identica's own messages.
	 */
	public Messages identica() {
		return read("messages.yml", Messages.class);
	}

	/**
	 * Returns Identica's username messages, such as the refusal of a username another account holds.
	 */
	public UsernameMessages username() {
		return read("identity/username/messages.yml", UsernameMessages.class);
	}

	/**
	 * Returns the Credential provider's messages; the provider must be enabled in the network.
	 */
	public CredentialMessages credential() {
		return read("providers/Credential/messages.yml", CredentialMessages.class);
	}

	/**
	 * Returns the Premium provider's messages; the provider must be enabled in the network.
	 */
	public PremiumMessages premium() {
		return read("providers/Premium/messages.yml", PremiumMessages.class);
	}

	/**
	 * Returns the example provider's messages; the provider must be enabled in the network.
	 */
	public ExampleMessages example() {
		return read("providers/Example/messages.yml", ExampleMessages.class);
	}

	/**
	 * Returns the sentinel feature's messages.
	 */
	public SentinelMessages sentinel() {
		return read("features/sentinel/messages.yml", SentinelMessages.class);
	}

	/**
	 * Returns the verification feature's messages, which its {@code /2fa} commands answer with.
	 */
	public VerificationMessages verification() {
		return read("features/verification/messages.yml", VerificationMessages.class);
	}

	/**
	 * Returns the join restriction feature's messages.
	 */
	public JoinRestrictionMessages joinRestriction() {
		return read("features/restriction/join/messages.yml", JoinRestrictionMessages.class);
	}

	private <T> T read(String file, Class<T> type) {
		return type.cast(documents.computeIfAbsent(file, ignored -> {
			Path path = dataDirectory.resolve(file);
			if (!Files.isRegularFile(path))
				throw new IllegalStateException("The proxy generated no " + file + "; is its provider enabled?");

			return CONFIGURA.read(path, type);
		}));
	}
}

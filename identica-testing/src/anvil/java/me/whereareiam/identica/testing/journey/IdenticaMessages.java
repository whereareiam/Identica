package me.whereareiam.identica.testing.journey;

import lombok.RequiredArgsConstructor;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.common.config.IdenticaModule;
import me.whereareiam.identica.model.config.Messages;
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

	private <T> T read(String file, Class<T> type) {
		return type.cast(documents.computeIfAbsent(file, ignored -> {
			Path path = dataDirectory.resolve(file);
			if (!Files.isRegularFile(path))
				throw new IllegalStateException("The proxy generated no " + file + "; is its provider enabled?");

			return CONFIGURA.read(path, type);
		}));
	}
}

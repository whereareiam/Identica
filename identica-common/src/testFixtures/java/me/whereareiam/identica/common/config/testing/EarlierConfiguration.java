package me.whereareiam.identica.common.config.testing;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The configuration directories earlier versions of Identica left, as test resources under
 * {@code config/<layout>/}. Each one is what that version wrote into its data directory on its first start,
 * captured by starting its plugin on a Velocity proxy, and the {@code index} next to it lists its files. The
 * documents of the Credential and Premium providers are only part of the 2.2.1 layouts, since the core does not
 * migrate them. The layouts are:
 * <ul>
 *     <li>{@code identica-2.2.1}, {@code identica-2.2.1-json}: the release with its defaults, written as YAML and
 *     as JSON; the development builds a95856ec and 927915d2 of May 22 and 25, 2026 wrote the same;</li>
 *     <li>{@code identica-2.2.1-customised}, {@code identica-2.2.1-customised-json}: a 2.2.1 directory in which an
 *     operator changed every core setting, every message and the provider entries, as 2.2.1 rewrote it on its
 *     next start; the database and its file name stay those of a default installation;</li>
 *     <li>{@code development-build-988e397c}: the build of May 25, 2026 that put the untrusted addresses of
 *     recognition under {@code eligibility};</li>
 *     <li>{@code development-build-d1cab43f}: May 25 (d1cab43f, 97daad33), with the provider join restriction;</li>
 *     <li>{@code development-build-37204281}: May 25 to 27 (37204281, 5f202bb6, 35a2276a, 2cb79a2c, 7d26f0a2,
 *     ea5e6f4e), with the conflict rules in {@code providers/conflicts};</li>
 *     <li>{@code development-build-1d8e0b60}: June 8 (1d8e0b60, 2858dd55), which split the settings into the
 *     engine, routing and provider documents;</li>
 *     <li>{@code development-build-01c65c0e}: June 8, with the features and the provider capabilities under
 *     {@code providers/capabilities/};</li>
 *     <li>{@code development-build-dae9c581}: June 8 to 13 (dae9c581, 5595a235, e605d1ba);</li>
 *     <li>{@code development-build-ee80c0f8}: October 10 (ee80c0f8, 97163a16), with every feature under
 *     {@code features/} and the username documents under {@code identity/username/};</li>
 *     <li>{@code development-build-616e6194}: October 11, the first build that recorded version 2;</li>
 *     <li>{@code development-build-898aac4b}: October 11 and 12 (898aac4b, 63f54991, b3ab26dd), in the current
 *     layout.</li>
 * </ul>
 * The documents the builds of June 8 to 13 only write once their feature is in use, those of the sentinel and the
 * join restriction's and username messages, were written by those builds' own document providers.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EarlierConfiguration {
	/** The release 2.2.1 with its defaults, in YAML. */
	public static final String RELEASE_221 = "identica-2.2.1";

	/** The release 2.2.1 with its defaults, in JSON. */
	public static final String RELEASE_221_JSON = "identica-2.2.1-json";

	/** A 2.2.1 directory an operator changed, in YAML. */
	public static final String RELEASE_221_CUSTOMISED = "identica-2.2.1-customised";

	/** A 2.2.1 directory an operator changed, in JSON. */
	public static final String RELEASE_221_CUSTOMISED_JSON = "identica-2.2.1-customised-json";

	/** Every layout of a development build, oldest first. */
	public static final List<String> DEVELOPMENT_BUILDS = List.of(
			"development-build-988e397c",
			"development-build-d1cab43f",
			"development-build-37204281",
			"development-build-1d8e0b60",
			"development-build-01c65c0e",
			"development-build-dae9c581",
			"development-build-ee80c0f8",
			"development-build-616e6194",
			"development-build-898aac4b"
	);

	/**
	 * Returns the files of a layout, relative to the data directory.
	 *
	 * @param layout name of the layout, such as {@link #RELEASE_221}
	 * @return paths such as {@code providers/providers.yml}
	 */
	public static List<String> files(String layout) {
		return read(layout, "index").lines().filter(line -> !line.isBlank()).toList();
	}

	/**
	 * Writes the files of a layout into a data directory.
	 *
	 * @param layout   name of the layout, such as {@link #RELEASE_221}
	 * @param dataPath the data directory, created if needed
	 */
	public static void copy(String layout, Path dataPath) {
		try {
			for (String file : files(layout)) {
				Path target = dataPath.resolve(file);
				Files.createDirectories(target.getParent());
				Files.writeString(target, read(layout, file));
			}
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}

	private static String read(String layout, String file) {
		String resource = "config/" + layout + "/" + file;
		try (InputStream input = EarlierConfiguration.class.getClassLoader().getResourceAsStream(resource)) {
			if (input == null) throw new IllegalArgumentException("Missing test resource " + resource);

			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}
}

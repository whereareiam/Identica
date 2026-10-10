package me.whereareiam.identica.replication.codec;

import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * Codec for snapshot serialization.
 *
 * @param <S> snapshot type
 */
public interface SnapshotCodec<S> {
	/**
	 * Encodes a snapshot into bytes.
	 *
	 * @param snapshot snapshot value
	 * @return encoded bytes
	 */
	byte @NotNull [] encode(@Nullable S snapshot);

	/**
	 * Decodes bytes into a snapshot.
	 *
	 * @param payload encoded payload
	 * @return decoded snapshot
	 */
	S decode(byte @Nullable [] payload);

	/**
	 * JSON codec using Configura for the given type. It is derived from the shared Configura instance
	 * at the time of the call, so it serializes with the modules Identica registered there.
	 *
	 * @param type snapshot type
	 * @param <S> snapshot type
	 * @return JSON codec
	 */
	static <S> @NotNull SnapshotCodec<S> json(@NotNull Class<S> type) {
		return new SnapshotCodec<>() {
			private final Configura configura = Config.configured().toBuilder().format(Format.JSON).build();

			@Override
			public byte @NotNull [] encode(@Nullable S snapshot) {
				if (snapshot == null) return new byte[0];
				return configura.writeBytes(snapshot);
			}

			@Override
			public S decode(byte @Nullable [] payload) {
				if (payload == null || payload.length == 0) return null;
				return configura.read(payload, type);
			}
		};
	}

	/**
	 * UTF-8 string codec.
	 *
	 * @return string codec
	 */
	static @NotNull SnapshotCodec<String> string() {
		return new SnapshotCodec<>() {
			@Override
			public byte @NotNull [] encode(@Nullable String snapshot) {
				if (snapshot == null) return new byte[0];
				return snapshot.getBytes(StandardCharsets.UTF_8);
			}

			@Override
			public String decode(byte @Nullable [] payload) {
				if (payload == null || payload.length == 0) return "";
				return new String(payload, StandardCharsets.UTF_8);
			}
		};
	}
}

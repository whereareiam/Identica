package me.whereareiam.identica.pipeline.state;

import lombok.Getter;
import lombok.Setter;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.PipelineCursor;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.exception.UnreadablePipelineStateException;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.StateScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The working state of one pipeline: where it stands and the values its phases and steps share, each under its
 * {@link StateKey}.
 *
 * <p>Values are live objects. A read returns the held instance, so a change to a mutable value needs no further
 * write; an immutable value is replaced through {@link #put(StateKey, Object)}. Only values of
 * {@link StateScope#PERSISTED persisted} keys are saved, through {@link #store()}; values of
 * {@link StateScope#RUN run} keys, such as a typed password, end with the run.</p>
 *
 * <pre>{@code
 * PrepareContextItem context = state.getOrCreate(PrepareContextItem.KEY, PrepareContextItem::new);
 * context.setProvider(provider);
 *
 * state.put(CredentialAuthenticationAttempt.KEY, new CredentialAuthenticationAttempt(password));
 * }</pre>
 */
public final class PipelineState {
	@Getter
	private @Nullable PipelineType pipelineType;
	@Getter
	private @NotNull PipelineCursor cursor;
	@Getter
	private long updatedAt;
	private final @NotNull Map<String, Entry> entries = new LinkedHashMap<>();
	/**
	 * Revision of the saved state this one was read from, or {@code null} for a state that expects no saved state,
	 * such as a new or a consumed one. A pipeline state store sets it when it reads or saves a state and compares it
	 * when it saves, so that a writer whose state was changed by someone else since it read it does not save over
	 * that change, and a writer that found nothing does not save over a state another writer created meanwhile.
	 */
	@Getter
	@Setter
	private @Nullable String revision;

	private PipelineState(@Nullable PipelineType pipelineType, @NotNull PipelineCursor cursor, long updatedAt) {
		this.pipelineType = pipelineType;
		this.cursor = cursor;
		this.updatedAt = updatedAt;
	}

	/**
	 * Creates the state of a pipeline that has not run yet.
	 *
	 * @return empty state
	 */
	public static @NotNull PipelineState initial() {
		return new PipelineState(null, PipelineCursor.initial(), System.currentTimeMillis());
	}

	/**
	 * Restores a saved state. Values stay encoded until a key reads them.
	 *
	 * @param stored saved state
	 * @return restored state
	 */
	public static @NotNull PipelineState restore(@NotNull StoredPipelineState stored) {
		PipelineState state = new PipelineState(
				stored.getPipelineType(),
				stored.getCursor() != null ? stored.getCursor() : PipelineCursor.initial(),
				stored.getUpdatedAt()
		);
		stored.getValues().forEach((id, value) -> {
			if (value != null) state.entries.put(id, Entry.encoded(value.getPayload(), value.getExpiresAt()));
		});

		return state;
	}

	/**
	 * Returns the saved form of this state: its persisted values, encoded, without values that expired or belong to
	 * run keys.
	 *
	 * @return saved form
	 */
	public @NotNull StoredPipelineState store() {
		long now = System.currentTimeMillis();
		Map<String, StoredPipelineState.StoredValue> values = new LinkedHashMap<>();
		entries.forEach((id, entry) -> {
			if (entry.isExpired(now)) return;

			String payload = entry.encode();
			if (payload != null) values.put(id, new StoredPipelineState.StoredValue(payload, entry.expiresAt));
		});

		return new StoredPipelineState(pipelineType, cursor, updatedAt, values);
	}

	/**
	 * Returns the value of a key.
	 *
	 * @param key key to read
	 * @param <T> value type
	 * @return value, or empty when none is held or it expired
	 * @throws UnreadablePipelineStateException when a saved value cannot be read back
	 */
	public <T> @NotNull Optional<T> get(@NotNull StateKey<T> key) {
		Entry entry = entries.get(key.id());
		if (entry == null) return Optional.empty();
		if (entry.isExpired(System.currentTimeMillis())) {
			entries.remove(key.id());
			return Optional.empty();
		}

		return Optional.of(entry.value(key));
	}

	/**
	 * Returns the value of a key, holding a new one from the factory when none is held.
	 *
	 * @param key key to read
	 * @param factory creates the value when none is held
	 * @param <T> value type
	 * @return held value
	 */
	public <T> @NotNull T getOrCreate(@NotNull StateKey<T> key, @NotNull Supplier<? extends T> factory) {
		Optional<T> held = get(key);
		if (held.isPresent()) return held.get();

		T created = factory.get();
		put(key, created);

		return created;
	}

	/**
	 * Holds a value for a key for as long as the state lives, replacing any value the key had.
	 *
	 * @param key key to write
	 * @param value value to hold
	 * @param <T> value type
	 */
	public <T> void put(@NotNull StateKey<T> key, @NotNull T value) {
		put(key, value, 0L);
	}

	/**
	 * Holds a value for a key for a limited time, replacing any value the key had.
	 *
	 * @param key key to write
	 * @param value value to hold
	 * @param ttlMillis how long the value lives, or {@code 0} or less to keep it as long as the state
	 * @param <T> value type
	 */
	public <T> void put(@NotNull StateKey<T> key, @NotNull T value, long ttlMillis) {
		long expiresAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : 0L;
		entries.put(key.id(), Entry.live(key, value, expiresAt));
		touch();
	}

	/**
	 * Removes the value of a key.
	 *
	 * @param key key to remove
	 */
	public void remove(@NotNull StateKey<?> key) {
		if (entries.remove(key.id()) != null) touch();
	}

	/**
	 * Removes the values of every {@link StateScope#RUN run} key, such as a typed password, so nothing of the run
	 * outlives it.
	 */
	public void removeRunValues() {
		if (entries.values().removeIf(Entry::isRun)) touch();
	}

	/**
	 * Returns whether the state holds a value for a key that has not expired.
	 *
	 * @param key key to check
	 * @return {@code true} when a value is held
	 */
	public boolean has(@NotNull StateKey<?> key) {
		Entry entry = entries.get(key.id());
		return entry != null && !entry.isExpired(System.currentTimeMillis());
	}

	/**
	 * Returns whether the state holds no value that has not expired.
	 *
	 * @return {@code true} when no value is held
	 */
	public boolean isEmpty() {
		long now = System.currentTimeMillis();
		return entries.values().stream().allMatch(entry -> entry.isExpired(now));
	}

	/**
	 * Sets the pipeline type the state belongs to.
	 *
	 * @param pipelineType pipeline type, or {@code null} when it is not known yet
	 */
	public void setPipelineType(@Nullable PipelineType pipelineType) {
		this.pipelineType = pipelineType;
		touch();
	}

	/**
	 * Sets where the pipeline stands.
	 *
	 * @param cursor position of the pipeline
	 */
	public void setCursor(@NotNull PipelineCursor cursor) {
		this.cursor = cursor;
		touch();
	}

	/**
	 * Returns the scenario context the state holds, whichever scenario it belongs to.
	 *
	 * @return scenario context, or {@code null} when none is held
	 */
	public @Nullable ScenarioContext getScenario() {
		for (PipelineType type : PipelineType.values()) {
			ScenarioContext context = getScenario(type);
			if (context != null) return context;
		}

		return null;
	}

	/**
	 * Returns the scenario context of a pipeline type.
	 *
	 * @param pipelineType pipeline type, or {@code null} for whichever scenario the state holds
	 * @return scenario context, or {@code null} when none is held
	 */
	public @Nullable ScenarioContext getScenario(@Nullable PipelineType pipelineType) {
		if (pipelineType == null) return getScenario();

		return switch (pipelineType) {
			case AUTHENTICATION -> get(AuthContext.KEY).orElse(null);
			case REGISTRATION -> get(RegistrationContext.KEY).orElse(null);
			case MIGRATION -> get(MigrationContext.KEY).orElse(null);
		};
	}

	/**
	 * Holds the scenario context, replacing the context of any scenario.
	 *
	 * @param context scenario context, or {@code null} to remove it
	 */
	public void setScenario(@Nullable ScenarioContext context) {
		remove(AuthContext.KEY);
		remove(RegistrationContext.KEY);
		remove(MigrationContext.KEY);
		if (context instanceof AuthContext authContext) put(AuthContext.KEY, authContext);
		if (context instanceof RegistrationContext registrationContext) put(RegistrationContext.KEY, registrationContext);
		if (context instanceof MigrationContext migrationContext) put(MigrationContext.KEY, migrationContext);

		touch();
	}

	private void touch() {
		updatedAt = System.currentTimeMillis();
	}

	/**
	 * One held value: live once a key wrote or read it, encoded while it is only restored from a saved state.
	 */
	private static final class Entry {
		private @Nullable StateKey<?> key;
		private @Nullable Object value;
		private @Nullable String payload;
		private final long expiresAt;

		private Entry(@Nullable StateKey<?> key, @Nullable Object value, @Nullable String payload, long expiresAt) {
			this.key = key;
			this.value = value;
			this.payload = payload;
			this.expiresAt = expiresAt;
		}

		private static @NotNull Entry live(@NotNull StateKey<?> key, @NotNull Object value, long expiresAt) {
			return new Entry(key, value, null, expiresAt);
		}

		private static @NotNull Entry encoded(@NotNull String payload, long expiresAt) {
			return new Entry(null, null, payload, expiresAt);
		}

		private boolean isExpired(long now) {
			return expiresAt > 0 && expiresAt <= now;
		}

		private boolean isRun() {
			return key != null && key.scope() == StateScope.RUN;
		}

		private <T> @NotNull T value(@NotNull StateKey<T> key) {
			if (value == null) {
				value = decode(key);
				payload = null;
			}
			this.key = key;

			return key.type().cast(value);
		}

		private <T> @NotNull T decode(@NotNull StateKey<T> key) {
			SnapshotCodec<T> codec = key.codec();
			if (codec == null || payload == null)
				throw new UnreadablePipelineStateException(key.id(), new IllegalStateException("No saved value to read"));

			try {
				T decoded = codec.decode(payload.getBytes(StandardCharsets.UTF_8));
				if (decoded == null) throw new IllegalStateException("The saved value is empty");

				return decoded;
			} catch (RuntimeException failure) {
				throw new UnreadablePipelineStateException(key.id(), failure);
			}
		}

		/**
		 * Returns the saved form of the value, or {@code null} when it belongs to a run key.
		 */
		@SuppressWarnings("unchecked")
		private @Nullable String encode() {
			if (value == null) return payload;
			if (key == null || key.scope() == StateScope.RUN) return null;

			SnapshotCodec<Object> codec = (SnapshotCodec<Object>) key.codec();
			if (codec == null) return null;

			return new String(codec.encode(value), StandardCharsets.UTF_8);
		}
	}
}

package me.whereareiam.identica.pipeline.state;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.PipelineCursor;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("unused")
public final class PipelineState {
	private @Nullable PipelineType pipelineType;
	private @NotNull PipelineCursor cursor;
	private @NotNull List<StateItem> items;
	private long updatedAt;

	public static @NotNull PipelineState initial() {
		return new PipelineState(null, PipelineCursor.initial(), new ArrayList<>(), System.currentTimeMillis());
	}

	public <T extends PipelineStateItem> @NotNull Optional<T> item(@NotNull Class<T> type) {
		String typeName = type.getName();
		long now = System.currentTimeMillis();
		for (StateItem record : safeItems()) {
			if (record == null || !typeName.equals(record.type))
				continue;
			if (record.expiresAt > 0 && record.expiresAt <= now)
				return Optional.empty();
			return Optional.ofNullable(decode(record.payload, type));
		}
		return Optional.empty();
	}

	public <T extends PipelineStateItem> @NotNull PipelineState withItem(@NotNull T item, long ttlMs) {
		String typeName = item.getClass().getName();
		List<StateItem> next = new ArrayList<>(safeItems());
		next.removeIf(record -> record != null && typeName.equals(record.type));
		long expiresAt = ttlMs > 0 ? System.currentTimeMillis() + ttlMs : 0L;
		next.add(new StateItem(typeName, encode(item), expiresAt));
		return new PipelineState(pipelineType, cursor, next, System.currentTimeMillis());
	}

	public @NotNull PipelineState withoutItem(@NotNull Class<? extends PipelineStateItem> type) {
		String typeName = type.getName();
		List<StateItem> next = new ArrayList<>(safeItems());
		next.removeIf(record -> record != null && typeName.equals(record.type));
		return new PipelineState(pipelineType, cursor, next, System.currentTimeMillis());
	}

	public @NotNull PipelineState withCursor(@NotNull PipelineCursor cursor) {
		return new PipelineState(pipelineType, cursor, safeItems(), System.currentTimeMillis());
	}

	public @NotNull PipelineState pruneExpired(long now) {
		List<StateItem> next = new ArrayList<>();
		boolean trimmed = false;
		for (StateItem record : safeItems()) {
			if (record == null) continue;
			if (record.expiresAt > 0 && record.expiresAt <= now) {
				trimmed = true;
				continue;
			}

			next.add(record);
		}
		if (!trimmed) return this;
		return new PipelineState(pipelineType, cursor, next, System.currentTimeMillis());
	}

	public void setPipelineType(@Nullable PipelineType pipelineType) {
		this.pipelineType = pipelineType;
		this.updatedAt = System.currentTimeMillis();
	}

	public void setCursor(@NotNull PipelineCursor cursor) {
		this.cursor = cursor;
		this.updatedAt = System.currentTimeMillis();
	}

	/**
	 * Returns the scenario context held in {@link #getItems() items}. It is a view of an item, not a field of its
	 * own: written as one, the state could not be read back, because the context type is abstract.
	 */
	@JsonIgnore
	public @Nullable ScenarioContext getScenario() {
		ScenarioContext context = item(AuthContext.class).orElse(null);
		if (context != null) return context;
		context = item(RegistrationContext.class).orElse(null);
		if (context != null) return context;
		return item(MigrationContext.class).orElse(null);
	}

	public @Nullable ScenarioContext getScenario(@Nullable PipelineType pipelineType) {
		if (pipelineType == PipelineType.AUTHENTICATION)
			return item(AuthContext.class).orElse(null);
		if (pipelineType == PipelineType.REGISTRATION)
			return item(RegistrationContext.class).orElse(null);
		if (pipelineType == PipelineType.MIGRATION)
			return item(MigrationContext.class).orElse(null);

		return getScenario();
	}

	@JsonIgnore
	public void setScenario(@Nullable ScenarioContext context) {
		removeItem(AuthContext.class);
		removeItem(RegistrationContext.class);
		removeItem(MigrationContext.class);
		if (context instanceof AuthContext authContext)
			putItem(authContext, 0L);
		if (context instanceof RegistrationContext registrationContext)
			putItem(registrationContext, 0L);
		if (context instanceof MigrationContext migrationContext)
			putItem(migrationContext, 0L);

		this.updatedAt = System.currentTimeMillis();
	}

	public <T extends PipelineStateItem> void putItem(@NotNull T item, long ttlMs) {
		PipelineState updated = withItem(item, ttlMs);
		this.items = updated.items;
		this.updatedAt = updated.updatedAt;
	}

	public void removeItem(@NotNull Class<? extends PipelineStateItem> type) {
		PipelineState updated = withoutItem(type);
		this.items = updated.items;
		this.updatedAt = updated.updatedAt;
	}

	private @NotNull List<StateItem> safeItems() {
		return items;
	}

	@SuppressWarnings("unchecked")
	private static <T> String encode(@NotNull T value) {
		SnapshotCodec<T> codec = SnapshotCodec.json((Class<T>) value.getClass());
		return new String(codec.encode(value), StandardCharsets.UTF_8);
	}

	private static <T> T decode(String payload, Class<T> type) {
		if (payload == null) return null;

		try {
			SnapshotCodec<T> codec = SnapshotCodec.json(type);
			return codec.decode(payload.getBytes(StandardCharsets.UTF_8));
		} catch (Exception ignored) {
			return null;
		}
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static final class StateItem {
		private String type;
		private String payload;
		private long expiresAt;
	}
}

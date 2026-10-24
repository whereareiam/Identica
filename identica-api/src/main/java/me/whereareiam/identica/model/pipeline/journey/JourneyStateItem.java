package me.whereareiam.identica.model.pipeline.journey;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.pipeline.state.StateKey;
import org.jetbrains.annotations.Nullable;

@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public final class JourneyStateItem {
	/**
	 * Key of the position of a waiting journey in a pipeline state. Saved with a pending pipeline.
	 */
	public static final StateKey<JourneyStateItem> KEY = StateKey.persisted("journey:position", JourneyStateItem.class);

	private @Nullable String stageId;
	private int stepIndex;
}

package me.whereareiam.identica.model.pipeline.journey;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.pipeline.state.StateKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public final class JourneyOverrideItem {
	/**
	 * Key of the requested journey position and the excluded providers and methods in a pipeline state. Saved with a pending pipeline.
	 */
	public static final StateKey<JourneyOverrideItem> KEY =
			StateKey.persisted("journey:override", JourneyOverrideItem.class);

	private @Nullable String stageId;
	private int stepIndex;
	private boolean clearProvider;
	private @Nullable String providerId;
	private @Nullable List<String> excludedProviders;
	/**
	 * IDs of provider methods the journey no longer tries, {@code <provider>:<name>}, such as one that failed.
	 */
	private @Nullable List<String> excludedMethods;
}

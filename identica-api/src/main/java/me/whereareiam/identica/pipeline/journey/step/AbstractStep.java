package me.whereareiam.identica.pipeline.journey.step;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Base of a step that keeps its name. Whether the step asks the player for anything is up to what it returns, see
 * {@link Step}.
 */
@Getter
@RequiredArgsConstructor
public abstract class AbstractStep implements Step {
	private final String name;
}

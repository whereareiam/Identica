package me.whereareiam.identica.pipeline.journey.step.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.step.StepContextRequirement;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

/**
 * Base of a step that talks to the player, such as one that asks for a password. It runs in interactive journeys only
 * and needs the player online, so the journey waits for the player on the step's server before it runs.
 *
 * <p>A step that decides from the login alone, even one that leads to a prompt, extends {@link SeamlessStep}.</p>
 */
@Getter
@RequiredArgsConstructor
public abstract class InteractiveStep implements Step {
	private final String name;

	@Override
	public @NotNull Set<JourneyMode> journeyModes() {
		return EnumSet.of(JourneyMode.INTERACTIVE);
	}

	@Override
	public @NotNull StepContextRequirement contextRequirement() {
		return StepContextRequirement.ONLINE;
	}
}

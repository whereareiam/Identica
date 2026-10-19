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
 * Base of a step that decides from the login alone, such as recognising a returning player. It runs in every journey,
 * while the player logs in, so a journey it finishes never sends the player to the step's server.
 *
 * <p>Such a step may still end waiting for the player's input; the player is then sent to the step's server and the
 * step runs again once the player is there.</p>
 */
@Getter
@RequiredArgsConstructor
public abstract class SeamlessStep implements Step {
	private final String name;

	@Override
	public @NotNull Set<JourneyMode> journeyModes() {
		return EnumSet.allOf(JourneyMode.class);
	}

	@Override
	public @NotNull StepContextRequirement contextRequirement() {
		return StepContextRequirement.LOGIN;
	}
}

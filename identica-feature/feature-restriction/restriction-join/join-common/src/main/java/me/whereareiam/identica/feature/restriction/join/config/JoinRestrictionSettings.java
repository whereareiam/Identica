package me.whereareiam.identica.feature.restriction.join.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.ConfigDocument;
import org.jetbrains.annotations.NotNull;

/**
 * Join restriction policy stored in features/restriction/join/settings.
 */
@Getter
@Setter
@ToString
public class JoinRestrictionSettings extends ConfigDocument {
	private boolean enabled;
	private @NotNull java.util.List<String> allow = new java.util.ArrayList<>();

	private @NotNull ResumeBypass resumeBypass = new ResumeBypass();

	/**
	 * Explicit opt-ins for bypassing active join restriction on resumed flows.
	 */
	@Getter
	@Setter
	@ToString
	public static class ResumeBypass {
		private boolean authentication;
		private boolean registration;
	}
}

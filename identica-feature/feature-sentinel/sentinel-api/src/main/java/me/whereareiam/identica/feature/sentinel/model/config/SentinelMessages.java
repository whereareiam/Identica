package me.whereareiam.identica.feature.sentinel.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Sentinel feature message configuration document.
 */
@Getter
@Setter
@ToString
public class SentinelMessages {
	private @NotNull ResumeSpam resumeSpam;

	/**
	 * Messages used by the built-in resume-spam sentinel.
	 */
	@Getter
	@Setter
	@ToString
	public static class ResumeSpam {
		private @NotNull List<String> denied;
	}
}

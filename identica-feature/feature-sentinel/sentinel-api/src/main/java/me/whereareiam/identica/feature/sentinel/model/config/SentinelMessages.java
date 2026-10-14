package me.whereareiam.identica.feature.sentinel.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.model.config.MessageDocument;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Sentinel feature message configuration document.
 */
@Getter
@Setter
@ToString
public class SentinelMessages implements MessageDocument {
	private @NotNull ResumeSpam resumeSpam;
	private @NotNull ContestedConnection contestedConnection;

	/**
	 * Messages used by the built-in resume-spam sentinel.
	 */
	@Getter
	@Setter
	@ToString
	public static class ResumeSpam {
		private @NotNull List<String> denied;
	}

	/**
	 * Messages used by the built-in contested-connection sentinel.
	 */
	@Getter
	@Setter
	@ToString
	public static class ContestedConnection {
		/**
		 * Reason shown while the address is locked out; {@code {seconds}} is the time left.
		 */
		private @NotNull List<String> denied;
	}
}

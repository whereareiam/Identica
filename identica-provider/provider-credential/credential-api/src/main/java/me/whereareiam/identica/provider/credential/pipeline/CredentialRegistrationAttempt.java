package me.whereareiam.identica.provider.credential.pipeline;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.whereareiam.identica.pipeline.state.PipelineInputItem;

/**
 * A password a player typed to register, or to confirm the one they typed before. It lives only in the run the
 * command starts and is never saved with the pipeline state.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CredentialRegistrationAttempt implements PipelineInputItem {
	private String password;
	private boolean confirm;
}

package me.whereareiam.identica.provider.credential.pipeline;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.whereareiam.identica.pipeline.state.PipelineInputItem;

/**
 * The password a player typed to sign in. It lives only in the run the command starts and is never saved with
 * the pipeline state.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CredentialAuthenticationAttempt implements PipelineInputItem {
	private String password;
}

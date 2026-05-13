package me.whereareiam.identica.provider.password.pipeline;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.whereareiam.identica.pipeline.state.PipelineStateItem;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordRegistrationAttempt implements PipelineStateItem {
	private String password;
	private boolean confirm;
}

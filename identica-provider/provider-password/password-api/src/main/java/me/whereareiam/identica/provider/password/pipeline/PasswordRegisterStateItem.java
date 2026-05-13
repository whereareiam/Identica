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
public class PasswordRegisterStateItem implements PipelineStateItem {
	private String passwordHash;
	private String hashingMethod;
}

package me.whereareiam.identica.model.conflict;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * The settings of an outcome that has none, such as {@code refuse}. It is written as an empty object.
 */
// A class annotation lets Jackson write a model without properties as an empty object.
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString
@EqualsAndHashCode
public final class NoSettings {
}

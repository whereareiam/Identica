package me.whereareiam.identica.pipeline.state;

/**
 * Marker interface for state items that belong to a single run, such as a password a player typed. A command
 * hands such an item to the pipeline through a {@link PipelineInput}; a step of that run may take it. Whether or
 * not a step took it, the item is removed from the state when the run ends, and a {@link PipelineStateStore}
 * never saves it, so it never reaches the replicated state other runtimes read.
 *
 * <pre>{@code
 * public class CredentialAuthenticationAttempt implements PipelineInputItem {
 *     private String password;
 * }
 * }</pre>
 */
public interface PipelineInputItem extends PipelineStateItem {
}

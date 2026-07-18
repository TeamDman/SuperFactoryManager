package ca.teamdman.sfm.client.action;

@FunctionalInterface
public interface SFMClientActionRequirement<T> {
    SFMClientActionAvailability<T> resolve(SFMClientActionContext context);
}

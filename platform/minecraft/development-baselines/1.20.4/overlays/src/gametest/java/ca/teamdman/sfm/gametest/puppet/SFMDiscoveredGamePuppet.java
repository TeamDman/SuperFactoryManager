package ca.teamdman.sfm.gametest.puppet;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public record SFMDiscoveredGamePuppet(
        String puppetName,
        Method method,
        SFMGamePuppetViewportProfile viewportProfile
) {
    public void declare(SFMGamePuppetHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot invoke game puppet " + location(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Game puppet " + location() + " failed while declaring actions", cause);
        }
    }

    public String location() {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }
}

package com.bbscn;


import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Represents a listener for GUI events.
 * <p>
 * It extends the {@code TabOrderedElement} interface, providing tab order functionality for GUI components.
 */
@SideOnly(Side.CLIENT)
public interface GuiEventListener {
    long DOUBLE_CLICK_THRESHOLD_MS = 250L;

    /**
     * Called when the mouse is moved within the GUI element.
     *
     * @param pMouseX the X coordinate of the mouse.
     * @param pMouseY the Y coordinate of the mouse.
     */
    default void mouseMoved(int pMouseX, int pMouseY) {
    }

    /**
     * Called when a mouse button is clicked within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param pMouseX the X coordinate of the mouse.
     * @param pMouseY the Y coordinate of the mouse.
     * @param pButton the button that was clicked.
     */
    default boolean mouseClicked(int pMouseX, int pMouseY, int pButton) {
        return false;
    }

    /**
     * Called when a mouse button is released within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param pMouseX the X coordinate of the mouse.
     * @param pMouseY the Y coordinate of the mouse.
     * @param pButton the button that was released.
     */
    default boolean mouseReleased(int pMouseX, int pMouseY, int pButton) {
        return false;
    }

    /**
     * Called when the mouse is dragged within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param pMouseX the X coordinate of the mouse.
     * @param pMouseY the Y coordinate of the mouse.
     * @param pButton the button that is being dragged.
     * @param pDragX  the X distance of the drag.
     * @param pDragY  the Y distance of the drag.
     */
    default boolean mouseDragged(int pMouseX, int pMouseY, int pButton, int pDragX, int pDragY) {
        return false;
    }

    default boolean mouseScrolled(int pMouseX, int pMouseY, int pScrollX, int pScrollY) {
        return false;
    }

    /**
     * Called when a keyboard key is pressed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param pKeyCode   the key code of the pressed key.
     * @param pScanCode  the scan code of the pressed key.
     * @param pModifiers the keyboard modifiers.
     */
    default boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        return false;
    }

    /**
     * Called when a keyboard key is released within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param pKeyCode   the key code of the released key.
     * @param pScanCode  the scan code of the released key.
     * @param pModifiers the keyboard modifiers.
     */
    default boolean keyReleased(int pKeyCode, int pScanCode, int pModifiers) {
        return false;
    }

    /**
     * Called when a character is typed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param pCodePoint the code point of the typed character.
     * @param pModifiers the keyboard modifiers.
     */
    default boolean charTyped(char pCodePoint, int pModifiers) {
        return false;
    }


    /**
     * Checks if the given mouse coordinates are over the GUI element.
     * <p>
     * @return {@code true} if the mouse is over the GUI element, {@code false} otherwise.
     *
     * @param pMouseX the X coordinate of the mouse.
     * @param pMouseY the Y coordinate of the mouse.
     */
    default boolean isMouseOver(int pMouseX, int pMouseY) {
        return false;
    }

    /**
     * Sets the focus state of the GUI element.
     *
     * @param pFocused {@code true} to apply focus, {@code false} to remove focus
     */
    void setFocused(boolean pFocused);

    boolean isFocused();

}

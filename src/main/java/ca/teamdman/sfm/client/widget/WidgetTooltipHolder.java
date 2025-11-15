package ca.teamdman.sfm.client.widget;


import com.bbscn.ScreenRectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.time.Duration;

@SideOnly(Side.CLIENT)
public class WidgetTooltipHolder {
    @Nullable
    private Tooltip tooltip;
    private Duration delay = Duration.ZERO;
    private long displayStartTime;
    private boolean wasDisplayed;

    public void setDelay(Duration pDelay) {
        this.delay = pDelay;
    }

    public void set(@Nullable Tooltip pTooltip) {
        this.tooltip = pTooltip;
    }

    @Nullable
    public Tooltip get() {
        return this.tooltip;
    }

    public void refreshTooltipForNextRenderPass(boolean pHovering, boolean pFocused, ScreenRectangle pScreenRectangle) {
        if (this.tooltip == null) {
            this.wasDisplayed = false;
        } else {
            boolean flag = pHovering || pFocused ;
            if (flag != this.wasDisplayed) {
                if (flag) {
                    this.displayStartTime = Minecraft.getSystemTime();
                }

                this.wasDisplayed = flag;
            }

            if (flag && Minecraft.getSystemTime() - this.displayStartTime > this.delay.toMillis()) {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen != null) {
                    screen.drawHoveringText(this.tooltip.toStrings(),pScreenRectangle.getPosition().x(),pScreenRectangle.getPosition().y());
                }
            }
        }
    }

    public void updateNarration(ITextComponent component) {
        if (this.tooltip != null) {
            this.tooltip.setNarration(component);
        }
    }
}

package ca.teamdman.sfm.gametest.puppet.action;
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.gametest.puppet.*;
import ca.teamdman.sfm.gametest.tests.compat.justdirethings.Issue610VisualFixtureGameTestGenerator;
import com.direwolf20.justdirethings.client.screens.DropperT2Screen;
import com.direwolf20.justdirethings.util.MagicHelpers;
import mekanism.client.gui.GuiEnergyCube;
import mekanism.client.gui.element.gauge.GuiEnergyGauge;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Real GameTest setup, normal block use, virtual gauge hover and frame captures. */
public final class Issue610EnergyEvidencePuppetAction implements SFMPuppetAction {
    private int phase,ticks,frame;
    private boolean opened,started,initialCaptured;
    private EnergyUnit previousUnit;
    @Override public String description(){return "issue610 energy GUI evidence phase "+phase;}
    @Override public boolean tick(ISFMGamePuppetRuntime runtime){
        if(++ticks>20*180)throw new IllegalStateException("Issue610 evidence action timed out at phase "+phase);
        if(phase==0){
            var window=Minecraft.getInstance().getWindow();
            if(window.getScreenWidth()!=1280 || window.getScreenHeight()!=720 || window.getGuiScale()!=2D)
                throw new IllegalStateException("Issue610 evidence requires --width1280 --height720 --variant1280x720@2");
            if(!runtime.runGameTest("issue_610_visual_fixture"))return false;
            if(Issue610VisualFixtureGameTestGenerator.state==null)throw new IllegalStateException("No visual fixture state");
            previousUnit=MekanismConfig.common.energyUnit.get();
            MekanismConfig.common.energyUnit.set(EnergyUnit.FORGE_ENERGY);
            phase=1;frame=0;return false;
        }
        var s=Issue610VisualFixtureGameTestGenerator.state;
        if(s.failure!=null)throw new IllegalStateException("Visual server observation failed",s.failure);
        if(phase==2 && initialCaptured){
            if(!started){Minecraft.getInstance().getSingleplayerServer().execute(s.start);started=true;}
            if(!s.finished)return false;
            SFM.LOGGER.info("ISSUE610_VISUAL result={} dropperFE={} laterFE={} sourceJ={}",
                s.finalHealthy==100000?"fixed_progress":"baseline_starvation",s.finalDropper,s.finalHealthy,s.finalJoules);
            phase=3;frame=0;return false;
        }
        if(phase>=6){MekanismConfig.common.energyUnit.set(previousUnit);return true;}
        var pos=phase==1 || phase==3?s.sourcePos:phase==2 || phase==4?s.dropperPos:s.healthyPos;
        if(!opened){
            // Teleport once, then allow its acknowledgement before normal block use.
            // Repeated teleports keep the server waiting and it ignores use-item packets.
            if(frame==0)runtime.positionForBlockUse(pos);
            if(++frame<8)return false;
            runtime.useBlock(pos);
            SFM.LOGGER.info("ISSUE610_BLOCK_USE phase={} localPos={} normalInteraction=true",phase,pos);
            opened=true;frame=0;return false;
        }
        var screen=Minecraft.getInstance().screen;
        if(!(screen instanceof AbstractContainerScreen<?>))return false;
        if(phase==1 || phase==3){
            if(!(screen instanceof GuiEnergyCube))throw new IllegalStateException("Expected actual Mekanism cube GUI, got "+screen.getClass());
            var gauge=screen.children().stream().filter(GuiEnergyGauge.class::isInstance).map(GuiEnergyGauge.class::cast)
                .findFirst().orElseThrow(()->new IllegalStateException("Cube energy gauge missing"));
            double x=gauge.getX()+gauge.getWidth()/2.0,y=gauge.getY()+gauge.getHeight()/2.0;
            SFMGamePuppetPointer.moveVirtual(screen,x,y);
            if(++frame<12)return false;
            if(frame==12)SFM.LOGGER.info("ISSUE610_TOOLTIP phase={} actualText={} pointerX={} pointerY={} unit=FE",
                phase,gauge.getTooltipText().stream().map(Component::getString).toList(),x,y);
        }else {
            if(!(screen instanceof DropperT2Screen dropper))
                throw new IllegalStateException("Expected actual JDT Advanced Dropper GUI, got "+screen.getClass());
            // Pinned JDT1.5.7: Dropper extraWidth60, extraHeight0; BaseMachineScreen
            // topSectionLeft=guiLeft-30, topSectionTop=guiTop-26. Energy hover is18x72.
            if(dropper.getGuiTop()-44<0)throw new IllegalStateException("JDT title would be cropped");
            double x=dropper.getGuiLeft()-30+dropper.getEnergyBarOffset()+9D;
            double y=dropper.getGuiTop()-26+5+36D;
            SFMGamePuppetPointer.moveVirtual(screen,x,y);
            if(++frame<12)return false;
            if(frame==12){
                int clientEnergy=dropper.getMenu().getEnergy();
                int expectedEnergy=phase==2?99975:phase==4?s.finalDropper:s.finalHealthy;
                if(phase==2 && (s.armed || s.finished))throw new IllegalStateException("Initial gap capture must precede every SFM transfer");
                if(phase==2)SFM.LOGGER.info("ISSUE610_INITIAL_GAP rawClientFE={} capacityFE=100000 fixtureUnarmed={} fixtureFinished={}",clientEnergy,!s.armed,s.finished);
                if(clientEnergy!=expectedEnergy)throw new IllegalStateException("JDT client energy not synchronized: "+clientEnergy+" expected "+expectedEnergy);
                boolean shift=Screen.hasShiftDown();
                String expectedText=Component.translatable("justdirethings.screen.energy",
                    shift?MagicHelpers.formatted(clientEnergy):MagicHelpers.withSuffix(clientEnergy),
                    shift?MagicHelpers.formatted(100000):MagicHelpers.withSuffix(100000)).getString();
                SFM.LOGGER.info("ISSUE610_JDT_HOVER phase={} clientMenuFE={} expectedFromPinnedWidgetFormula={} shift={} pointerX={} pointerY={} titleTop={}",
                    phase,clientEnergy,expectedText,shift,x,y,dropper.getGuiTop()-44);
            }
        }
        String id=phase==2?"dropper-initial":phase==1?"source-before-hover":phase==3?"source-after-hover":phase==4?"dropper-after":"healthy-after";
        String text=phase==2?"Initial first machine before any SFM transfer. Actual hovered tooltip; exact energy asserted separately in the run log.":phase==1?"Before transfer: real Mekanism cube. Hovered FE display; source raw "+s.fullJoules+" J.":
            phase==3?"After two SFML passes: source "+s.finalJoules+" J; later consumer "+s.finalHealthy+" FE. GUI rounds values; raw readings logged.":
            phase==4?"Real idle JDT dropper: "+s.finalDropper+" / 100000 FE. A one-FE gap persists; hovered energy tooltip rounds it.":
            "Later real JDT consumer: "+s.finalHealthy+" / 100000 FE. Hovered energy tooltip; synthetic load drained between passes, then frozen.";
        if(!runtime.capture(id,Component.literal(text)))return false;
        runtime.closeScreen();opened=false;frame=0;if(phase==2){initialCaptured=true;return false;}phase++;return false;
    }
}

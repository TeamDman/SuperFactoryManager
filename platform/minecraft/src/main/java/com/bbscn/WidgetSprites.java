package com.bbscn;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class WidgetSprites {
    ResourceLocation enabled;
    ResourceLocation disabled;
    ResourceLocation enabledFocused;
    ResourceLocation disabledFocused;

    public WidgetSprites(ResourceLocation p_295225_, ResourceLocation p_294772_) {
        this(p_295225_, p_295225_, p_294772_, p_294772_);
    }

    public WidgetSprites(ResourceLocation p_296152_, ResourceLocation p_296020_, ResourceLocation p_296073_) {
        this(p_296152_, p_296020_, p_296073_, p_296020_);
    }

    public WidgetSprites(ResourceLocation enabled, ResourceLocation disabled, ResourceLocation enabledFocused, ResourceLocation disabledFocused) {
        this.enabled=enabled;
        this.disabled=disabled;
        this.enabledFocused=enabledFocused;
        this.disabledFocused=disabledFocused;
    }

    public ResourceLocation get(boolean pEnabled, boolean pFocused) {
        if (pEnabled) {
            return pFocused ? this.enabledFocused : this.enabled;
        } else {
            return pFocused ? this.disabledFocused : this.disabled;
        }
    }
}
package com.bbscn;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public enum Whence {
    ABSOLUTE,
    RELATIVE,
    END;
}
package ca.teamdman.sfm.client.registry;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;

import ca.teamdman.sfm.client.screen.ManagerScreen;

public enum SFMKeyConflictContext implements IKeyConflictContext {
    MANAGER {

        @Override
        public boolean isActive() {
            return Minecraft.getMinecraft().currentScreen instanceof ManagerScreen;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == KeyConflictContext.GUI;
        }
    },
    LABEL_GUN {

        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == KeyConflictContext.IN_GAME;
        }
    },
    LABEL_GUN_MODIFIER {

        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == KeyConflictContext.IN_GAME;
        }
    },
    LABEL_GUN_SCROLL_MODIFIER {

        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == KeyConflictContext.IN_GAME;
        }
    },
    LABEL_GUN_ON_MANAGER {

        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == KeyConflictContext.IN_GAME;
        }
    },
    NETWORK_TOOL {

        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == KeyConflictContext.IN_GAME;
        }
    }
}

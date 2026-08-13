package com.cleanroommc.ivgtmb.client;

import com.cleanroommc.ivgtmb.client.gui.BuilderUI;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (KeyBindings.openBuilder != null && KeyBindings.openBuilder.isPressed()) {
            BuilderUI.open();
        }
    }
}

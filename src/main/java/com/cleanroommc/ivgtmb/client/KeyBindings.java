package com.cleanroommc.ivgtmb.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public final class KeyBindings {

    public static final String CATEGORY = "key.categories.ivgtmb";

    public static KeyBinding openBuilder;

    private KeyBindings() {
    }

    public static void init() {
        openBuilder = new KeyBinding("key.ivgtmb.open", Keyboard.KEY_Y, CATEGORY);
        ClientRegistry.registerKeyBinding(openBuilder);
    }
}

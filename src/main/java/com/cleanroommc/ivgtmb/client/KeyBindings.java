/*
 * In-game Visual GT Machine Builder (ivgtmb)
 * Copyright (c) 2026 SiO-0
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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

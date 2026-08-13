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
package com.cleanroommc.ivgtmb.client.gui;

import com.cleanroommc.ivgtmb.IVGTMB;

import gregtech.client.renderer.ICubeRenderer;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates the existing GTCEu {@link ICubeRenderer}s exposed as static fields
 * of {@code gregtech.client.renderer.texture.Textures}, so the UI can offer
 * {@code getFrontOverlay} and {@code getBaseTexture} choices.
 */
@SideOnly(Side.CLIENT)
public final class RendererOptions {

    public static final List<String> overlays = new ArrayList<>();
    public static final List<String> baseTextures = new ArrayList<>();
    private static boolean loaded = false;

    private RendererOptions() {
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Class<?> texturesClass = Class.forName("gregtech.client.renderer.texture.Textures");
            for (Field field : texturesClass.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!ICubeRenderer.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                String name = field.getName();
                if (name.contains("OVERLAY")) {
                    overlays.add(name);
                } else if (name.contains("CASING") || name.contains("TEXTURE") || name.contains("BRICKS")
                        || name.contains("PLASCRETE")) {
                    baseTextures.add(name);
                }
            }
        } catch (Exception e) {
            IVGTMB.LOGGER.error("Failed to enumerate GTCEu renderer options", e);
        }
    }

    public static String overlayAt(int index) {
        return overlays.isEmpty() ? "NONE" : overlays.get(Math.floorMod(index, overlays.size()));
    }

    public static String baseTextureAt(int index) {
        return baseTextures.isEmpty() ? "NONE" : baseTextures.get(Math.floorMod(index, baseTextures.size()));
    }

    /**
     * Returns the actual {@link ICubeRenderer} instance for the overlay at the
     * given
     * index, or {@code null} if it cannot be resolved. Used by the machine preview.
     */
    public static ICubeRenderer overlayRendererAt(int index) {
        if (overlays.isEmpty()) {
            return null;
        }
        return rendererByName(overlays.get(Math.floorMod(index, overlays.size())));
    }

    /**
     * Returns the actual {@link ICubeRenderer} instance for the base texture at the
     * given index, or {@code null} if it cannot be resolved. Used by the machine
     * preview.
     */
    public static ICubeRenderer baseTextureRendererAt(int index) {
        if (baseTextures.isEmpty()) {
            return null;
        }
        return rendererByName(baseTextures.get(Math.floorMod(index, baseTextures.size())));
    }

    private static ICubeRenderer rendererByName(String fieldName) {
        try {
            Class<?> texturesClass = Class.forName("gregtech.client.renderer.texture.Textures");
            Field field = texturesClass.getField(fieldName);
            return (ICubeRenderer) field.get(null);
        } catch (Exception e) {
            IVGTMB.LOGGER.error("Failed to resolve renderer {}", fieldName, e);
            return null;
        }
    }
}

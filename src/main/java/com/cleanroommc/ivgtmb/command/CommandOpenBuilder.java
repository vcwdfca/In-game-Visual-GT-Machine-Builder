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
package com.cleanroommc.ivgtmb.command;

import com.cleanroommc.ivgtmb.client.gui.BuilderUI;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-side command {@code /ivgtmb} that opens the builder UI directly on the
 * client, so it works in both single-player and multiplayer without any network
 * round trip.
 */
@SideOnly(Side.CLIENT)
public class CommandOpenBuilder extends CommandBase {

    @Override
    public String getName() {
        return "ivgtmb";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.ivgtmb.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        BuilderUI.open();
    }
}

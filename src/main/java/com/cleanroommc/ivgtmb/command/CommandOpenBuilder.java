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

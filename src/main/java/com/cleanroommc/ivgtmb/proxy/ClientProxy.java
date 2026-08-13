package com.cleanroommc.ivgtmb.proxy;

import com.cleanroommc.ivgtmb.client.ClientEventHandler;
import com.cleanroommc.ivgtmb.client.KeyBindings;
import com.cleanroommc.ivgtmb.command.CommandOpenBuilder;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        KeyBindings.init();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
        ClientCommandHandler.instance.registerCommand(new CommandOpenBuilder());
    }
}

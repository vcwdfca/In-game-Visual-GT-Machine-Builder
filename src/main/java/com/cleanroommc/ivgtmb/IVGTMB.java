package com.cleanroommc.ivgtmb;

import com.cleanroommc.ivgtmb.proxy.CommonProxy;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = IVGTMB.MODID, name = IVGTMB.NAME, version = IVGTMB.VERSION, acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:gregtech@[2.8.10-beta,)")
public class IVGTMB {

    public static final String MODID = Tags.MOD_ID;
    public static final String NAME = Tags.MOD_NAME;
    public static final String VERSION = Tags.VERSION;

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.Instance
    public static IVGTMB INSTANCE;

    @SidedProxy(modId = MODID, clientSide = "com.cleanroommc.ivgtmb.proxy.ClientProxy", serverSide = "com.cleanroommc.ivgtmb.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}

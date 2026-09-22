package fr.shoqapik.blockend;

import com.mojang.logging.LogUtils;
import fr.shoqapik.blockend.network.PacketHandler;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

@Mod(BlockEndMod.MODID)
public class BlockEndMod
{
    public static final String MODID = "blockend";
    public static final Logger LOGGER = LogUtils.getLogger();




    public BlockEndMod() {
        PacketHandler.registerMessages();
        MinecraftForge.EVENT_BUS.register(this);
    }




    public static MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

}

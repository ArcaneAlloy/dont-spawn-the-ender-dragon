package fr.shoqapik.blockend;

import fr.shoqapik.blockend.network.PacketHandler;
import fr.shoqapik.blockend.network.message.PacketTower;
import fr.shoqapik.blockend.server.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BlockEndMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Event {
    @SubscribeEvent
    public static void onWorldLoad(EntityTravelToDimensionEvent event) {
        if(event.getDimension().equals(Level.END)){
            if(BlockEndMod.getServer()!=null){
                if(!event.getEntity().level.isClientSide && event.getEntity() instanceof Player){
                    PacketHandler.sendToPlayer(new PacketTower(true), (ServerPlayer) event.getEntity());
                }
                if(!event.getEntity().level.isClientSide && event.getEntity() instanceof Player){
                    PacketHandler.sendToPlayer(new PacketTower(false), (ServerPlayer) event.getEntity());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.END) {
            ServerData.get().getStructureManager().getStructure().getCentre();
            ServerData.get().getStructureManager().getStructure().tick();
        }
    }
}

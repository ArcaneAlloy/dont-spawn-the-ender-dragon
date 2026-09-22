package fr.shoqapik.blockend.mixins;

import fr.shoqapik.blockend.structures.StructureManager;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.end.DragonRespawnAnimation;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.List;

@Mixin(EndDragonFight.class)
public class EndDragonFigthMixin {

    @Shadow @Nullable private DragonRespawnAnimation respawnStage;

    @Shadow private boolean dragonKilled;

    @Shadow private int respawnTime;
    @Shadow @Final private ServerBossEvent dragonEvent;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void injectMethod(CallbackInfo info) {
        this.dragonEvent.setVisible(false);
        info.cancel();
    }
    /**
     * @author
     * @reason
     */
    @Overwrite
    private void spawnExitPortal(boolean p_64094_) {
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    private void findOrCreateDragon() {
    }


    /**
     * @author
     * @reason
     */
    @Overwrite
    protected void setRespawnStage(DragonRespawnAnimation p_64088_) {
        if (this.respawnStage == null) {
            throw new IllegalStateException("Dragon respawn isn't in progress, can't skip ahead in the animation.");
        } else {
            this.respawnTime = 0;
            if (p_64088_ == DragonRespawnAnimation.END) {
                this.respawnStage = null;
                this.dragonKilled = false;
            } else {
                this.respawnStage = p_64088_;
            }

        }
    }

}

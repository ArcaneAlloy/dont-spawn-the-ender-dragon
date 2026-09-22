package fr.shoqapik.blockend.mixins;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.*;

import java.util.stream.Stream;

@Mixin(TheEndBiomeSource.class)
public abstract class TheEndBiomeSourceMixin extends BiomeSource {


    @Shadow @Final private Holder<Biome> midlands;

    @Shadow @Final private Holder<Biome> highlands;

    @Shadow @Final private Holder<Biome> islands;

    @Shadow @Final private Holder<Biome> barrens;
    @Shadow @Final private Holder<Biome> end;
    protected TheEndBiomeSourceMixin(Stream<Holder<Biome>> p_47896_) {
        super(p_47896_);
    }
    /**
     * @author
     * @reason
     */
    @Overwrite
    @Override
    public Holder<Biome> getNoiseBiome(int p_204292_, int p_204293_, int p_204294_, Climate.Sampler p_204295_) {
        int i = QuartPos.toBlock(p_204292_);
        int j = QuartPos.toBlock(p_204293_);
        int k = QuartPos.toBlock(p_204294_);
        int l = SectionPos.blockToSectionCoord(i);
        int i1 = SectionPos.blockToSectionCoord(k);
        long distance =  (long)l * (long)l + (long)i1 * (long)i1;
        if (distance <= 100L) {
            return this.end;
        }else if(distance <= 4096L){
            return this.islands;
        } else {
            int j1 = (SectionPos.blockToSectionCoord(i) * 2 + 1) * 8;
            int k1 = (SectionPos.blockToSectionCoord(k) * 2 + 1) * 8;
            double d0 = p_204295_.erosion().compute(new DensityFunction.SinglePointContext(j1, j, k1));
            if (d0 > 0.25D) {
                return this.highlands;
            } else if (d0 >= -0.0625D) {
                return this.midlands;
            } else {
                return d0 < -0.21875D ? this.islands : this.barrens;
            }
        }
    }

}

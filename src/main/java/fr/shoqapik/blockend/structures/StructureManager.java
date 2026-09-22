package fr.shoqapik.blockend.structures;

import com.mojang.datafixers.util.Pair;
import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.elevator.ElevatorGroupCapability;
import eu.asangarin.meaddon.block.CustomRemoteControllerBlockEntity;
import fr.shoqapik.blockend.BlockEndMod;
import fr.shoqapik.blockend.api.Savable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.util.*;

public class StructureManager implements Savable {
    private Structure structure;

    public StructureManager() {}
    public StructureManager(CompoundTag data) {
        this.deserialise(data);
    }

    @Override
    public CompoundTag serialise() {
        CompoundTag data = new CompoundTag();

        data.put("Structure", this.getStructure().serialise());

        return data;
    }

    @Override
    public void deserialise(CompoundTag data) {
        this.structure = new Structure(data.getCompound("Structure"));
    }

    public Structure getStructure() {
        if (this.structure == null) {
            BlockEndMod.LOGGER.warn("Missing realm structure! Creating..");
            this.structure = new Structure();
        }

        return this.structure;
    }
    public static ServerLevel getDimension() {
        return BlockEndMod.getServer().getLevel(Level.END);
    }

    public static class Structure implements Savable {

        private final ResourceLocation structure;
        private boolean isPlaced;
        private BlockPos centre;

        private final List<BlockPos> posElevators = List.of(
                new BlockPos(-8,54,9),
                new BlockPos(-8,355,9)
        );

        public Queue<Runnable> tasks = new ArrayDeque<>();

        private static final int BLOCK_BATCH = 400;
        private static final int CLEAN_BATCH = 2000;

        public Structure(ResourceLocation structure, @Nullable BlockPos centre) {
            this.structure = structure;
            this.isPlaced = false;
            this.centre = centre;
        }

        public Structure() {
            this(getDefaultStructure(), new BlockPos(0, 40, 0));
        }

        public Structure(CompoundTag data) {
            this.structure = new ResourceLocation(data.getString("structure"));
            this.deserialise(data);
        }

        @Override
        public CompoundTag serialise() {
            CompoundTag data = new CompoundTag();
            data.putString("structure", this.structure.toString());
            data.putBoolean("isPlaced", this.isPlaced);

            if (this.centre != null)
                data.put("Centre", NbtUtils.writeBlockPos(this.centre));

            return data;
        }

        @Override
        public void deserialise(CompoundTag data) {
            this.isPlaced = data.getBoolean("isPlaced");

            if (data.contains("Centre")) {
                this.centre = NbtUtils.readBlockPos(data.getCompound("Centre"));
            }
        }

        private static ResourceLocation getDefaultStructure() {
            return new ResourceLocation(BlockEndMod.MODID, "island_center");
        }

        private Optional<StructureTemplate> findStructure(ResourceLocation structure) {
            return BlockEndMod.getServer().getStructureManager().get(structure);
        }

        public void verify() {
            if (!this.isPlaced) {
                this.place();
            }
        }

        private void place() {
            this.place(getDimension(), true);
        }

        private void place(ServerLevel level, boolean inform) {
            if (level == null) return;

            if (inform) {
                for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                    p.sendSystemMessage(Component.literal("Generating island..."));
                }
            }

            this.makeInitialIsland(level);

            tasks.add(() -> {this.settingElevator(level);});
            this.isPlaced = true;
        }


        private Vec3i placeComponentBatched(ServerLevel level, ResourceLocation loc, int addX, int y, int addZ) {

            StructureTemplate template = this.findStructure(loc).orElse(null);
            if (template == null) return Vec3i.ZERO;

            Vec3i size = template.getSize();

            BlockPos offset = new BlockPos(
                    -size.getX() / 2 + addX,
                    y,
                    -size.getZ() / 2 + addZ
            );

            StructurePlaceSettings settings = new StructurePlaceSettings();

            List<StructureTemplate.StructureBlockInfo> raw = settings.getRandomPalette(template.palettes, offset).blocks();

            List<Pair<BlockPos, BlockState>> blocks = new ArrayList<>();

            for (StructureTemplate.StructureBlockInfo info : raw) {
                BlockPos pos = StructureTemplate
                        .calculateRelativePosition(settings, info.pos)
                        .offset(offset);

                BlockState state = info.state;

                if (!state.isAir()) {
                    blocks.add(Pair.of(pos, state));
                }
            }

            for (int i = 0; i < blocks.size(); i += BLOCK_BATCH) {
                int start = i;
                int end = Math.min(i + BLOCK_BATCH, blocks.size());

                tasks.add(() -> {
                    for (int j = start; j < end; j++) {
                    Pair<BlockPos, BlockState> p = blocks.get(j);
                    level.setBlock(p.getFirst(), p.getSecond(), Block.UPDATE_NONE);
                    }
                });
            }

            return size;
        }


        // Antes: recogia aqui mismo, de forma sincrona, TODAS las posiciones
        // a limpiar (hasta ~4,37M comprobaciones getBlockState, radio 200,
        // y< 70) antes de encolar nada. Eso es lo que bloqueaba el hilo del
        // servidor varios segundos de un tiron.
        //
        // Ahora: cleanAreaBatched() se limita a encolar una tarea de
        // escaneo POR CHUNK (16x16 columnas) -- 256 tareas para la rejilla
        // -125..125 -- que se reparten via la misma cola `tasks`/tick() que
        // ya existe. Cada tarea de escaneo, cuando le toca, salta secciones
        // de 16 niveles enteras que ya son aire (LevelChunkSection#hasOnlyAir)
        // sin llamar a getBlockState() ni una vez para esas posiciones, y
        // solo entonces encola sus propios lotes de limpieza de CLEAN_BATCH
        // en bloques, exactamente igual que antes.
        //
        // El resultado final en el mundo es identico: se limpia lo mismo
        // (todo lo que no sea obsidiana dentro del radio), solo cambia
        // como se reparte el trabajo en el tiempo.
        private void cleanAreaBatched(ServerLevel level) {
            int minY = level.getMinBuildHeight();

            int minChunk = -125 >> 4;
            int maxChunk = (125 - 1) >> 4;

            for (int cxOuter = minChunk; cxOuter <= maxChunk; cxOuter++) {
                for (int czOuter = minChunk; czOuter <= maxChunk; czOuter++) {
                    final int cx = cxOuter;
                    final int cz = czOuter;
                    tasks.add(() -> scanAndQueueChunkCleanup(level, cx, cz, minY));
                }
            }
        }

        private void scanAndQueueChunkCleanup(ServerLevel level, int cx, int cz, int minY) {
            int baseX = cx << 4;
            int baseZ = cz << 4;

            if (!chunkTouchesCleanupRadius(baseX, baseZ)) {
                return;
            }

            LevelChunk chunk = level.getChunk(cx, cz);
            LevelChunkSection[] sections = chunk.getSections();

            List<BlockPos> positions = new ArrayList<>();

            for (int y = minY; y < 70; y++) {
                if (((y - minY) & 15) == 0) {
                    int sectionIndex = (y - minY) >> 4;
                    LevelChunkSection section = (sectionIndex >= 0 && sectionIndex < sections.length)
                            ? sections[sectionIndex]
                            : null;

                    if (section != null && section.hasOnlyAir()) {
                        // Seccion entera (16 niveles) ya vacia: nos la
                        // saltamos sin leer ni un solo bloque.
                        y = Math.min(y + 16, 70) - 1;
                        continue;
                    }
                }

                for (int x = baseX; x < baseX + 16; x++) {
                    if (x < -125 || x >= 125) continue;

                    for (int z = baseZ; z < baseZ + 16; z++) {
                        if (z < -125 || z >= 125) continue;
                        if (!(Math.sqrt((double) x * x + (double) z * z) < 200.0)) continue;

                        BlockPos pos = new BlockPos(x, y, z);
                        if (level.getBlockState(pos).is(Blocks.OBSIDIAN)) continue;

                        positions.add(pos);
                    }
                }
            }

            for (int i = 0; i < positions.size(); i += CLEAN_BATCH) {
                int start = i;
                int end = Math.min(i + CLEAN_BATCH, positions.size());

                tasks.add(() -> {
                    for (int j = start; j < end; j++) {
                        level.setBlock(positions.get(j), Blocks.AIR.defaultBlockState(), 1);
                    }
                });
            }
        }

        private static boolean chunkTouchesCleanupRadius(int baseX, int baseZ) {
            for (int dx = 0; dx < 16; dx++) {
                int x = baseX + dx;
                if (x < -125 || x >= 125) continue;

                for (int dz = 0; dz < 16; dz++) {
                    int z = baseZ + dz;
                    if (z < -125 || z >= 125) continue;

                    if (Math.sqrt((double) x * x + (double) z * z) < 200.0) {
                        return true;
                    }
                }
            }
            return false;
        }

        public void makeInitialIsland(ServerLevel level) {
            cleanAreaBatched(level);

            Vec3i sizeIsland = placeComponentBatched(level, new ResourceLocation(BlockEndMod.MODID, "island_center"), 0, 0, 0);

            placeComponentBatched(level, new ResourceLocation(BlockEndMod.MODID, "island_sourth"), -24, 0, sizeIsland.getZ() - 69);

            placeComponentBatched(level, new ResourceLocation(BlockEndMod.MODID, "island_north"), 0, 0, -sizeIsland.getZ() + 59);

            placeComponentBatched(level, new ResourceLocation(BlockEndMod.MODID, "island_west"), -sizeIsland.getX() + 63, 0, 0);

            placeComponentBatched(level, new ResourceLocation(BlockEndMod.MODID, "island_east"), sizeIsland.getX() - 48, 0, -21);

            Vec3i sizeTower = placeComponentBatched(level, new ResourceLocation(BlockEndMod.MODID, "tower0"), -12, sizeIsland.getY(), 16);

            placeComponentBatched(level, new ResourceLocation(BlockEndMod.MODID, "tower1"), -10, sizeIsland.getY() + sizeTower.getY(), 15);
        }


        public void tick() {
            int maxPerTick = 2;

            for (int i = 0; i < maxPerTick; i++) {
                Runnable task = tasks.poll();
                if (task == null) return;

                task.run();
            }
        }


        public void settingElevator(ServerLevel level) {
            ElevatorGroupCapability cap = ElevatorGroupCapability.get(level);
            boolean isFirstConfig = true;

            for (BlockPos pos : this.posElevators) {

                ControllerBlockEntity controller = (ControllerBlockEntity) level.getBlockEntity(pos);
                if (controller == null) continue;

                cap.add(controller);

                if (pos.getY() == 355) {
                    setRemotes(level, pos, controller, new BlockPos(-5, 358, 16), new BlockPos(-12, 358, 15), new BlockPos(-12, 358, 10), new BlockPos(-5, 358, 10));
                } else {
                    setRemotes(level, pos, controller, new BlockPos(-12, 58, 17), new BlockPos(-11, 58, 10), new BlockPos(-5, 58, 16), new BlockPos(-5, 58, 10));
                }

                if (isFirstConfig) {
                    ElevatorGroup data = cap.get(-8, 9, controller.getFacing());
                    if (data != null) {
                        data.increaseCageDepthOffset();
                        data.setTargetSpeed(0.6F);

                        for (int i = 0; i < 2; i++) {
                            data.increaseCageDepth();
                            data.increaseCageWidth();
                        }
                    }
                    isFirstConfig = false;
                }
            }
        }
        private void setRemotes(ServerLevel level, BlockPos controllerPos, ControllerBlockEntity controller, BlockPos... bases) {

            for (BlockPos base : bases) {
                for (int i = 0; i < 2; i++) {

                    BlockPos relativePos = new BlockPos(base.getX(), base.getY() - i, base.getZ());

                    CustomRemoteControllerBlockEntity remote =
                            (CustomRemoteControllerBlockEntity) level.getBlockEntity(relativePos);

                    if (remote != null) {
                        remote.setCamoState(Blocks.CALCITE.defaultBlockState());
                        remote.setValues(remote.getFacing(), controllerPos, controller.getFacing());
                    }
                }
            }
        }
        public BlockPos getCentre() {
            this.verify();

            if (this.centre != null) return this.centre;

            this.centre = new BlockPos(0, 60, 0);
            return this.centre;
        }
    }
}

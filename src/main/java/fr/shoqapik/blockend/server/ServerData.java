package fr.shoqapik.blockend.server;

import fr.shoqapik.blockend.BlockEndMod;
import fr.shoqapik.blockend.structures.StructureManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * Data that will be saved to the world in .nbt form
 * For saving across server restarts.
 * Remember to call markDirty() after setting a value to ensure it saves
 *
 * @author duzo
 */
public class ServerData extends SavedData {
	private StructureManager realmManager;
	public StructureManager getStructureManager() {
		if (this.realmManager == null) {
			BlockEndMod.LOGGER.warn("Missing realm manager! Creating..");
			this.realmManager = new StructureManager();
		}

		return this.realmManager;
	}

	public static ServerData get() {
		DimensionDataStorage manager = BlockEndMod.getServer().getLevel(Level.OVERWORLD).getDataStorage();

		ServerData state = manager.computeIfAbsent(
				ServerData::load,
				ServerData::new,
				BlockEndMod.MODID
		);

		state.setDirty(); // bad code

		return state;
	}

	@Override
	public CompoundTag save(CompoundTag data) {
		data.put("StructureManager",this.getStructureManager().serialise());
		return data;
	}
	public static ServerData load(CompoundTag data) {
		ServerData created = new ServerData();
		CompoundTag tag=data.getCompound("StructureManager");
		created.realmManager = new StructureManager(tag);
		return created;
	}

}

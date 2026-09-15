package dev.lohrel.plasticmemories;

import dev.lohrel.plasticmemories.network.PlasticMemoriesNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(PlasticMemories.MOD_ID)
public final class PlasticMemories {
    public static final String MOD_ID = "plastic_memories";

    public PlasticMemories(IEventBus modBus) {
        modBus.addListener(PlasticMemoriesNetwork::registerPayloads);
    }
}

package capsule.loot;

import capsule.Config;
import net.minecraft.world.storage.loot.ILootGenerator;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.ILootCondition;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public class CapsuleLootTableHook {

    private static final int DEFAULT_WEIGHT = 3;

    public static LootPool capsulePool = null;

    public CapsuleLootTableHook() {

    }

    @SubscribeEvent
    public void hookCapsulesOnLootTable(LootTableLoadEvent event) {

        if (!Config.lootTablesList.contains(event.getName().toString()))
            return;

        // create a capsule loot entry per folder
        if (capsulePool == null) {
            List<CapsuleLootEntry> entries = new ArrayList<>();
            for (String path : Config.lootTemplatesPaths.get()) {
                int weight = findConfiguredWeight(path);
                entries.add(new CapsuleLootEntry(path, weight, 0, new ILootCondition[0], "capsule:capsuleLootsEntry" + path.replace("/", "_")));
            }

            capsulePool = new LootPool(
                    entries.toArray(new ILootGenerator[0]),    // the loot is taken from a Capsule managed entry list
                    new ILootCondition[0],                // no particular condition, always loot one capsule
                    new RandomValueRange(1.0F, 1.0F),    // spawn one capsule using that pool
                    new RandomValueRange(0.0F, 0.0F),    // no extra capsuleList
                    "capsulePool");
        }

        // add a new pool containing all weighted entries
        event.getTable().addPool(capsulePool);

    }

    public int findConfiguredWeight(String path) {
        int weight = DEFAULT_WEIGHT;
        if (Config.lootTemplatesData.containsKey(path)) {
            weight = Config.lootTemplatesData.get(path).weigth.get();
        }
        return weight;
    }
}

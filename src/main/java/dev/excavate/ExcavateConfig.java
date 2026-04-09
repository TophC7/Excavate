package dev.excavate;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ExcavateConfig {

    public static final ModConfigSpec SPEC;

    // harvest settings
    public static final ModConfigSpec.BooleanValue AUTO_REPLANT;

    // special tool interaction settings
    public static final ModConfigSpec.BooleanValue AREA_HOE_TILLING;
    public static final ModConfigSpec.BooleanValue AREA_SHOVEL_PATHING;
    public static final ModConfigSpec.BooleanValue AREA_AXE_ACTIONS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Crop Harvesting Settings")
               .push("harvesting");

        AUTO_REPLANT = builder
                .comment(
                    "When enabled, area-harvested crops are automatically replanted.",
                    "Consumes one seed from the drops to replant. If no seed drops,",
                    "the crop is not replanted.")
                .define("autoReplant", true);

        builder.pop();

        builder.comment("Special Tool Interaction Settings")
               .push("interactions");

        AREA_HOE_TILLING = builder
                .comment("Allow hoes with Excavation to till dirtlike blocks in an area.")
                .define("areaHoeTilling", true);

        AREA_SHOVEL_PATHING = builder
                .comment("Allow shovels with Excavation to create dirt paths in an area.")
                .define("areaShovelPathing", true);

        AREA_AXE_ACTIONS = builder
                .comment(
                    "Allow axes with Excavation to apply right-click area actions.",
                    "This includes log stripping, copper scraping, and wax removal.")
                .define("areaAxeActions", true);

        builder.pop();

        SPEC = builder.build();
    }
}

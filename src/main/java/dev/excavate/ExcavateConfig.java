package dev.excavate;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ExcavateConfig {

    public static final ModConfigSpec SPEC;

    // harvest settings
    public static final ModConfigSpec.BooleanValue AUTO_REPLANT;

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

        SPEC = builder.build();
    }
}

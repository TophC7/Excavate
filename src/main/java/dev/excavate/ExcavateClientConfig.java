package dev.excavate;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ExcavateClientConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SHOW_HIGHLIGHT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Visual Settings")
               .push("visual");

        SHOW_HIGHLIGHT = builder
                .comment(
                    "Show per-block outlines for the area that will be affected.",
                    "Disable if you find the overlay distracting.")
                .define("showHighlight", true);

        builder.pop();

        SPEC = builder.build();
    }
}

package dev.excavate;

import net.minecraft.client.gui.screens.Screen;
import xyz.kwahson.core.config.ConfigTab;
import xyz.kwahson.core.config.KwahsConfigScreen;

/**
 * Configuration screen for Excavate.
 */
public final class ExcavateConfigScreen {

    private ExcavateConfigScreen() {}

    public static Screen create(Screen parent) {
        return KwahsConfigScreen.builder("Excavate", parent, ExcavateConfig.SPEC)
                .tab("Harvesting", ExcavateConfigScreen::buildHarvestingTab)
                .build();
    }

    private static void buildHarvestingTab(ConfigTab tab) {
        tab.section("Crops");
        tab.left(tab.toggle("Auto-Replant", ExcavateConfig.AUTO_REPLANT));
    }
}

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
                .tab("General", ExcavateConfigScreen::buildGeneralTab)
                .tab("Harvesting", ExcavateConfigScreen::buildHarvestingTab)
                .build();
    }

    private static void buildGeneralTab(ConfigTab tab) {
        tab.section("Visual");
        tab.left(tab.toggle("Show Highlight", ExcavateConfig.SHOW_HIGHLIGHT));
    }

    private static void buildHarvestingTab(ConfigTab tab) {
        tab.section("Crops");
        tab.left(tab.toggle("Auto-Replant", ExcavateConfig.AUTO_REPLANT));
    }
}

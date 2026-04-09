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
        return KwahsConfigScreen.builder("Excavate", parent, ExcavateClientConfig.SPEC, ExcavateConfig.SPEC)
                .tab("Settings", ExcavateConfigScreen::buildSettingsTab)
                .build();
    }

    private static void buildSettingsTab(ConfigTab tab) {
        tab.section("Visual");
        tab.left(tab.toggle("Show Highlight", ExcavateClientConfig.SHOW_HIGHLIGHT));

        tab.nextRow();
        tab.section("Special Tool Actions");
        tab.left(tab.toggle("Area Hoe Tilling", ExcavateConfig.AREA_HOE_TILLING));
        tab.right(tab.toggle("Area Shovel Pathing", ExcavateConfig.AREA_SHOVEL_PATHING));
        tab.nextRow();
        tab.left(tab.toggle("Area Axe Actions", ExcavateConfig.AREA_AXE_ACTIONS));

        tab.nextRow();
        tab.section("Crops");
        tab.left(tab.toggle("Auto-Replant", ExcavateConfig.AUTO_REPLANT));
    }
}

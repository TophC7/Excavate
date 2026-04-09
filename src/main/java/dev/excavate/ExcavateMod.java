package dev.excavate;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ExcavateMod.MOD_ID)
public class ExcavateMod {
    public static final String MOD_ID = "excavate";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ResourceLocation EXCAVATION_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "excavation");

    public ExcavateMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ExcavateConfig.SPEC);

        if (FMLEnvironment.dist.isClient()) {
            modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (mc, parent) -> ExcavateConfigScreen.create(parent));
        }

        LOGGER.info("Excavate loaded");
    }

    /**
     * Safely gets the excavation enchantment level for a player
     * Returns 0 if the enchantment isn't registered or the player doesn't have it
     */
    public static int getExcavationLevel(Level level, Player player) {
        return level.registryAccess()
                .registry(Registries.ENCHANTMENT)
                .flatMap(reg -> reg.getHolder(EXCAVATION_ID))
                .map(holder -> EnchantmentHelper.getEnchantmentLevel(holder, player))
                .orElse(0);
    }

    public static int getExcavationLevel(Level level, ItemStack stack) {
        return level.registryAccess()
                .registry(Registries.ENCHANTMENT)
                .flatMap(reg -> reg.getHolder(EXCAVATION_ID))
                .map(stack::getEnchantmentLevel)
                .orElse(0);
    }
}

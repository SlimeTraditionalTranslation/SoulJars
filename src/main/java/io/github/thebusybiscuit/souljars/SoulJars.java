package io.github.thebusybiscuit.souljars;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import de.unpixelt.locale.Translate;
import io.github.bakedlibs.dough.updater.GitHubBuildsUpdaterTR;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.bstats.bukkit.Metrics;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.implementation.items.blocks.BrokenSpawner;
import io.github.thebusybiscuit.slimefun4.implementation.items.blocks.UnplaceableBlock;
import io.github.thebusybiscuit.slimefun4.libraries.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import org.mini2Dx.gettext.GetText;
import org.mini2Dx.gettext.PoFile;

public class SoulJars extends JavaPlugin implements Listener, SlimefunAddon {

    private static final String JAR_TEXTURE = "bd1c777ee166c47cae698ae6b769da4e2b67f468855330ad7bddd751c5293f";
    private final Map<EntityType, Integer> mobs = new EnumMap<>(EntityType.class);

    private Config cfg;
    private ItemGroup itemGroup;
    private RecipeType recipeType;
    private SlimefunItemStack emptyJar;

    @Override
    public void onEnable() {
        cfg = new Config(this);

        // Setting up bStats
        new Metrics(this, 5581);

        if (cfg.getBoolean("options.auto-update") && getDescription().getVersion().startsWith("Build_STCT - ")) {
            new GitHubBuildsUpdaterTR(this, getFile(), "SlimeTraditionalTranslation/SoulJars/master").start();
        }

        GetText.setLocale(Locale.TRADITIONAL_CHINESE);
        InputStream inputStream = getClass().getResourceAsStream("/translations/zh_tw.po");
        if (inputStream == null) {
            getLogger().severe("錯誤！無法找到翻譯檔案，請回報給翻譯者。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        } else {
            getLogger().info("載入繁體翻譯檔案...");
            try {
                PoFile poFile = new PoFile(Locale.TRADITIONAL_CHINESE, inputStream);
                GetText.add(poFile);
            } catch (ParseCancellationException | IOException e) {
                getLogger().severe("錯誤！讀取翻譯時發生錯誤，請回報給翻譯者：" + e.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        emptyJar = new SlimefunItemStack("SOUL_JAR", JAR_TEXTURE, GetText.tr("&bSoul Jar &7(Empty)"), "", GetText.tr("&rKill a Mob while having this"), GetText.tr("&rItem in your Inventory to bind"), GetText.tr("&rtheir Soul to this Jar"));
        itemGroup = new ItemGroup(new NamespacedKey(this, "soul_jars"), new CustomItemStack(emptyJar, GetText.tr("&bSoul Jars"), "", GetText.tr("&a> Click to open")));
        recipeType = new RecipeType(new NamespacedKey(this, "mob_killing"), new CustomItemStack(Material.DIAMOND_SWORD, GetText.tr("&cKill the specified Mob"), GetText.tr("&cwhile having an empty Soul Jar"), GetText.tr("&cin your Inventory")));

        new SlimefunItem(itemGroup, emptyJar, RecipeType.ANCIENT_ALTAR, new ItemStack[] { SlimefunItems.EARTH_RUNE, new ItemStack(Material.SOUL_SAND), SlimefunItems.WATER_RUNE, new ItemStack(Material.SOUL_SAND), SlimefunItems.NECROTIC_SKULL, new ItemStack(Material.SOUL_SAND), SlimefunItems.AIR_RUNE, new ItemStack(Material.SOUL_SAND), SlimefunItems.FIRE_RUNE }, new CustomItemStack(emptyJar, 3)).register(this);
        new JarsListener(this);

        for (String mob : cfg.getStringList("mobs")) {
            try {
                EntityType type = EntityType.valueOf(mob);
                registerSoul(type);
            } catch (Exception x) {
                getLogger().log(Level.WARNING, GetText.tr("{0}: Possibly invalid mob type: {1}", x.getClass().getSimpleName(), mob));
            }
        }

        cfg.save();
    }

    private void registerSoul(EntityType type) {
        String name = ChatUtils.humanize(type.name());
        String nameTR = Translate.getEntity(de.unpixelt.locale.Locale.zh_tw, type);

        int souls = cfg.getOrSetDefault("souls-required." + type.toString(), 128);
        mobs.put(type, souls);

        Material mobEgg = Material.getMaterial(type.toString() + "_SPAWN_EGG");

        if (mobEgg == null) {
            mobEgg = Material.ZOMBIE_SPAWN_EGG;
        }

        // @formatter:off
        SlimefunItemStack jarItem = new SlimefunItemStack(type.name() + "_SOUL_JAR", JAR_TEXTURE, GetText.tr("&cSoul Jar &7({0})", nameTR), "", GetText.tr("&7Infused Souls: &e1"));
        SlimefunItem jar = new UnplaceableBlock(itemGroup, jarItem, recipeType,
        new ItemStack[] { null, null, null, emptyJar, null, new CustomItemStack(mobEgg, GetText.tr("&rKill {0}x {1}", souls, nameTR)), null, null, null });
        jar.register(this);

        SlimefunItemStack filledJarItem = new SlimefunItemStack("FILLED_" + type.name() + "_SOUL_JAR", JAR_TEXTURE, GetText.tr("&cFilled Soul Jar &7({0})", nameTR), "", GetText.tr("&7Infused Souls: &e{0}", souls));
        SlimefunItem filledJar = new FilledJar(itemGroup, filledJarItem, recipeType,
        new ItemStack[] { null, null, null, emptyJar, null, new CustomItemStack(mobEgg, GetText.tr("&rKill {0}x {1}", souls, nameTR)), null, null, null });
        filledJar.register(this);

        BrokenSpawner brokenSpawner = SlimefunItems.BROKEN_SPAWNER.getItem(BrokenSpawner.class);

        SlimefunItemStack spawnerItem = new SlimefunItemStack(type.toString() + "_BROKEN_SPAWNER", Material.SPAWNER, GetText.tr("&cBroken Spawner &7({0})", nameTR));
        new SlimefunItem(itemGroup, spawnerItem, RecipeType.ANCIENT_ALTAR,
        new ItemStack[] { new ItemStack(Material.IRON_BARS), SlimefunItems.EARTH_RUNE, new ItemStack(Material.IRON_BARS), SlimefunItems.EARTH_RUNE, filledJarItem, SlimefunItems.EARTH_RUNE, new ItemStack(Material.IRON_BARS), SlimefunItems.EARTH_RUNE, new ItemStack(Material.IRON_BARS) }, 
        brokenSpawner.getItemForEntityType(type)).register(this);
        // @formatter:on
    }

    public Map<EntityType, Integer> getRequiredSouls() {
        return mobs;
    }

    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Override
    public String getBugTrackerURL() {
        return "https://github.com/SlimeTraditionalTranslation/SoulJars/issues";
    }

}

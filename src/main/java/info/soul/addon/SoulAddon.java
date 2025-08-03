package info.soul.addon;

import info.soul.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class SoulAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Soul");

    @Override
    public void onInitialize() {
        // Register modules
        Modules.get().add(new SpawnerProtect());
        Modules.get().add(new AutoSpawnerDeliver());
        Modules.get().add(new AutoAHSniper());
        Modules.get().add(new PanicSell());
        Modules.get().add(new AntiTrap());
        Modules.get().add(new AutoShulker());
        Modules.get().add(new CoordFinder());
        Modules.get().add(new HideScoreboard());
        Modules.get().add(new AdvancedESPPlus());
        Modules.get().add(new AutoCrystal());
        Modules.get().add(new RotatedDeepslateESP());
	    Modules.get().add(new GambleBot());
		Modules.get().add(new SoulTunnler());


        LOG.info("Soul Addon initialized with {} modules and 3 themes!", Modules.get().getGroup(CATEGORY).size());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "info.soul.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Kypulse", "Soul");
    }
}

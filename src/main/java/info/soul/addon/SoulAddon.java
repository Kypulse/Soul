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
        Modules.get().add(new SpawnerProtect());//should work but if not idk sorry
        Modules.get().add(new AutoSpawnerDeliver());//works
        Modules.get().add(new AutoAHSniper());//works 
        Modules.get().add(new AntiTrap());//works but idk if its undetected or not
        Modules.get().add(new AutoShulker()); //works well and pretty ud
        Modules.get().add(new CoordFinder());//pretty sure it works
        Modules.get().add(new HideScoreboard()); //Works great
        Modules.get().add(new AdvancedESPPlus()); //May remove vine esp but idk 
        Modules.get().add(new AutoCrystal()); // "Legit Auto crystal" because im lazy to make it rage cheat instead  
        Modules.get().add(new RotatedDeepslateESP()); //works well
        Modules.get().add(new GambleBot()); //mostly working
	Modules.get().add(new SoulTunnler()); //working just need to check to see how ud it is


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

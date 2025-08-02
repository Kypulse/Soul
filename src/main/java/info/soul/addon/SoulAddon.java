package info.soul.addon;

import info.soul.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class SoulAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Soul");

    @Override
    public void onInitialize() {
    Modules.get().add(new BlockESP());;
    Modules.get().add(new TunnelBaseFinder());
    Modules.get().add(new SpawnerProtect());
    Modules.get().add(new AutoSpawnerDeliver());
    Modules.get().add(new AutoAHSniper());
    //Modules.get().add(new IDKYET());
    Modules.get().add(new AntiTrap());
    Modules.get().add(new AutoShulker());
	//Modules.get().add(new GrimAutoTotem());//grim auto totem bypass skidded from 
	Modules.get().add(new HideScoreboard());//yes
	Modules.get().add(new AdvancedESPPlus());
	Modules.get().add(new AutoCrystal());//should work
	Modules.get().add(new RotatedDeepslateESP()); 
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

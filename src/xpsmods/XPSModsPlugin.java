package xpsmods;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

public class XPSModsPlugin extends BaseModPlugin {

    private static final Logger log = Global.getLogger(XPSModsPlugin.class);

    @Override
    public void onApplicationLoad() throws Exception {
        log.info("XP S-Mods: application loaded");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        log.info("XP S-Mods: game loaded (newGame=" + newGame + ")");
    }
}

package com.epicseed.vampirism.bootstrap;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.commands.VampirismCommand;
import com.epicseed.vampirism.commands.VampirismPotionCommand;
import com.epicseed.vampirism.commands.VampirismRelicBindingCommand;
import com.epicseed.vampirism.commands.VampirismRelicBindingsCommand;
import com.epicseed.vampirism.commands.VampirismRelicCommand;
import com.epicseed.vampirism.commands.VampirismSkillTreeCommand;

public final class CommandRegistrar {

    private CommandRegistrar() {
    }

    public static void register(@Nonnull Vampirism plugin, @Nonnull VampirismRuntime runtime) {
        plugin.getCommandRegistry().registerCommand(new VampirismCommand());
        plugin.getCommandRegistry().registerCommand(new VampirismSkillTreeCommand(runtime.progressionPageFactory()));
        plugin.getCommandRegistry().registerCommand(new VampirismPotionCommand());
        plugin.getCommandRegistry().registerCommand(new VampirismRelicCommand());
        plugin.getCommandRegistry().registerCommand(new VampirismRelicBindingsCommand(runtime.progressionPageFactory()));
        plugin.getCommandRegistry().registerCommand(new VampirismRelicBindingCommand(runtime.progressionPageFactory()));
    }
}

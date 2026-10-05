package com.syhros.packextract.forge;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/** {@code /packextract} (client-side): export everything. {@code /packextract noimages} skips the images. */
public class ExportCommand extends CommandBase {

    @Override
    public String getName() {
        return "packextract";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/packextract [noimages]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
        @Nullable BlockPos targetPos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "noimages") : Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        boolean images = !Arrays.asList(args).contains("noimages");
        sender.sendMessage(new TextComponentString("[Pack Extract] Starting the export..."));
        ClientEvents.startNextTick(ClientEvents.settings(images));
    }
}

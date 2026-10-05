package com.syhros.packextract.client;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

/** {@code /packextract} (client-side): export everything. {@code /packextract noimages} skips the images. */
public class ExportCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "packextract";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/packextract [noimages]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "noimages") : null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        boolean images = !Arrays.asList(args).contains("noimages");
        sender.addChatMessage(new ChatComponentText("[Pack Extract] Starting the export..."));
        ClientProxy.startNextTick(ClientProxy.settings(images));
    }
}

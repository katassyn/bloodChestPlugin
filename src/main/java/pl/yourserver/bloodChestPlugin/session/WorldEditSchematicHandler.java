package pl.yourserver.bloodChestPlugin.session;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.edit.SessionLimitExceededException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class WorldEditSchematicHandler implements SchematicHandler {

    @Override
    public void pasteSchematic(File schematicFile, World world, Location origin) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            throw new IOException("Unsupported schematic format for file " + schematicFile.getAbsolutePath());
        }
        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            clipboard = reader.read();
        }
        BlockVector3 to = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        try (com.sk89q.worldedit.EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            Operation operation = holder
                    .createPaste(editSession)
                    .to(to)
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(operation);
        } catch (SessionLimitExceededException ex) {
            throw new IOException("Failed to paste schematic: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void clearRegion(World world, Location origin, Vector size) {
        BlockVector3 min = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        BlockVector3 max = min.add((int) size.getX() - 1, (int) size.getY() - 1, (int) size.getZ() - 1);
        try (com.sk89q.worldedit.EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            CuboidRegion region = new CuboidRegion(min, max);
            editSession.setBlocks(region, BlockTypes.AIR.getDefaultState());
        }
    }
}

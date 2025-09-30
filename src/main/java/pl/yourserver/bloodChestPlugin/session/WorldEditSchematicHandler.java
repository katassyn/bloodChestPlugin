package pl.yourserver.bloodChestPlugin.session;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEditException;
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
    public PasteResult pasteSchematic(File schematicFile, World world, Location origin) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            throw new IOException("Unsupported schematic format for file " + schematicFile.getAbsolutePath());
        }
        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            clipboard = reader.read();
        }
        BlockVector3 to = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            Operation operation = holder
                    .createPaste(editSession)
                    .to(to)
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(operation);
        } catch (WorldEditException ex) {
            throw new IOException("Failed to paste schematic: " + ex.getMessage(), ex);
        }
        BlockVector3 dimensions = clipboard.getDimensions();
        Vector regionSize = new Vector(dimensions.getBlockX(), dimensions.getBlockY(), dimensions.getBlockZ());
        return new PasteResult(regionSize);
    }

    @Override
    public void clearRegion(World world, Location origin, Vector size) {
        BlockVector3 min = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        int width = Math.max(1, (int) Math.ceil(size.getX()));
        int height = Math.max(1, (int) Math.ceil(size.getY()));
        int depth = Math.max(1, (int) Math.ceil(size.getZ()));
        BlockVector3 max = min.add(width - 1, height - 1, depth - 1);
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            CuboidRegion region = new CuboidRegion(min, max);
            editSession.setBlocks(region, BlockTypes.AIR.getDefaultState());
        } catch (WorldEditException ex) {
            throw new IllegalStateException("Failed to clear region: " + ex.getMessage(), ex);
        }
    }
}

package pl.yourserver.bloodChestPlugin.session;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.io.File;

public interface SchematicHandler {

    void pasteSchematic(File schematicFile, World world, Location origin) throws Exception;

    void clearRegion(World world, Location origin, Vector size);
}

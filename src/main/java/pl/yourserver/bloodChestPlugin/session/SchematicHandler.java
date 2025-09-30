package pl.yourserver.bloodChestPlugin.session;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.io.File;

public interface SchematicHandler {

    PasteResult pasteSchematic(File schematicFile, World world, Location origin) throws Exception;

    void clearRegion(World world, Location origin, Vector size);

    record PasteResult(Vector regionSize) {

        public PasteResult {
            if (regionSize != null) {
                regionSize = regionSize.clone();
            }
        }

        @Override
        public Vector regionSize() {
            return regionSize == null ? null : regionSize.clone();
        }
    }
}

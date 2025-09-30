package pl.yourserver.bloodChestPlugin.session;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.io.File;

public interface SchematicHandler {

    PasteResult pasteSchematic(File schematicFile, World world, Location origin) throws Exception;

    void clearRegion(World world, Location origin, Vector size);

    record PasteResult(Vector minimumOffset, Vector maximumOffset, Vector regionSize) {

        public PasteResult {
            if (minimumOffset != null) {
                minimumOffset = minimumOffset.clone();
            }
            if (maximumOffset != null) {
                maximumOffset = maximumOffset.clone();
            }
            if (regionSize != null) {
                regionSize = regionSize.clone();
            }
        }

        @Override
        public Vector minimumOffset() {
            return minimumOffset == null ? null : minimumOffset.clone();
        }

        @Override
        public Vector maximumOffset() {
            return maximumOffset == null ? null : maximumOffset.clone();
        }

        @Override
        public Vector regionSize() {
            return regionSize == null ? null : regionSize.clone();
        }
    }
}

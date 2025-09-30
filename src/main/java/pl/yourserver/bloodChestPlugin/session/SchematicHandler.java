package pl.yourserver.bloodChestPlugin.session;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface SchematicHandler {

    PasteResult pasteSchematic(File schematicFile,
                               World world,
                               Location origin,
                               MarkerConfiguration markerConfiguration) throws Exception;

    void clearRegion(World world, Location origin, Vector size);

    record MarkerConfiguration(Material mobMarker,
                               Material chestMarker,
                               Optional<Material> minorMobMarker) {

        public MarkerConfiguration {
            Objects.requireNonNull(mobMarker, "mobMarker");
            Objects.requireNonNull(chestMarker, "chestMarker");
            minorMobMarker = minorMobMarker == null ? Optional.empty() : minorMobMarker;
        }
    }

    record BlockOffset(int x, int y, int z) {
        public BlockOffset {
            // No validation needed; values originate from schematic coordinates.
        }
    }

    record PasteResult(Vector minimumOffset,
                       Vector maximumOffset,
                       Vector regionSize,
                       List<BlockOffset> mobMarkerOffsets,
                       List<BlockOffset> chestMarkerOffsets,
                       List<BlockOffset> minorMobMarkerOffsets,
                       List<BlockOffset> playerSpawnMarkerOffsets) {

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
            mobMarkerOffsets = mobMarkerOffsets == null
                    ? List.of()
                    : List.copyOf(mobMarkerOffsets);
            chestMarkerOffsets = chestMarkerOffsets == null
                    ? List.of()
                    : List.copyOf(chestMarkerOffsets);
            minorMobMarkerOffsets = minorMobMarkerOffsets == null
                    ? List.of()
                    : List.copyOf(minorMobMarkerOffsets);
            playerSpawnMarkerOffsets = playerSpawnMarkerOffsets == null
                    ? List.of()
                    : List.copyOf(playerSpawnMarkerOffsets);
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

        @Override
        public List<BlockOffset> mobMarkerOffsets() {
            return Collections.unmodifiableList(mobMarkerOffsets);
        }

        @Override
        public List<BlockOffset> chestMarkerOffsets() {
            return Collections.unmodifiableList(chestMarkerOffsets);
        }

        @Override
        public List<BlockOffset> minorMobMarkerOffsets() {
            return Collections.unmodifiableList(minorMobMarkerOffsets);
        }

        @Override
        public List<BlockOffset> playerSpawnMarkerOffsets() {
            return Collections.unmodifiableList(playerSpawnMarkerOffsets);
        }
    }
}

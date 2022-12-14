package dev.pixirora.janerator;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

public class Janerator {
    private static MinecraftServer server = null;
    public static final Logger LOGGER = LoggerFactory.getLogger("Janerator");
    private static Map<ResourceKey<Level>, ChunkGenerator> generators = Maps.newHashMapWithExpectedSize(3);
    private static double log_phi = Math.log((1 + Math.sqrt(5)) / 2);

    public static boolean shouldOverride(int x, int z) {
        double angle = Math.PI / 4 * Math.log(Math.pow(x, 2) + Math.pow(z, 2)) / log_phi + Math.PI;
        double tan_angle = Math.tan(angle);
        return (x * tan_angle - z) * Math.signum(tan_angle / Math.sin(angle)) > 0;
    }

    public static boolean shouldOverride(ChunkPos chunkPos) {
        return shouldOverride(chunkPos.x*16, chunkPos.z*16);
    }

    public static void setMinecraftServer(MinecraftServer server) {
        Janerator.server = server;
    }

    public static synchronized ChunkGenerator getGenerator(ResourceKey<Level> dimension) {
        if (Janerator.generators.isEmpty()) {
            initializeGenerators();
        }

        ChunkGenerator generator = Janerator.generators.get(dimension);
        return generator != null ? generator : Janerator.generators.get(Level.OVERWORLD);
    }

    public static MultiGenerator getGeneratorAt(
        ChunkPos chunkPos, 
        ResourceKey<Level> dimension,
        ChunkGenerator defaultGenerator
    ) {
        Map<ChunkGenerator, List<List<Integer>>> generatorMap = Maps.newHashMap();

        ChunkGenerator modifiedGenerator = getGenerator(dimension);

        int actual_x = chunkPos.getMinBlockX();
        int actual_z = chunkPos.getMinBlockZ();

        List<List<Integer>> newCoordinates = new ArrayList<>();

        for (int x = actual_x; x < actual_x + 16; x++) {
            for (int z = actual_z; z < actual_z + 16; z++) {
                ChunkGenerator generatorAtPos = shouldOverride(x, z) ? modifiedGenerator : defaultGenerator;
                generatorMap.getOrDefault(generatorAtPos, newCoordinates).add(Arrays.asList(x, z));

                if (newCoordinates.size() != 0) {
                    generatorMap.put(generatorAtPos, newCoordinates);
                    newCoordinates = new ArrayList<>();
                }
            }
        }

        return new MultiGenerator(generatorMap);
    }

    public static <T> Registry<T> getRegistry(ResourceKey<? extends Registry<? extends T>> registryKey) {
        return Janerator.server.registryAccess().registryOrThrow(registryKey);
    }

    private static void initializeGenerators() {
        Janerator.generators.put(Level.OVERWORLD, createOverworldGenerator());
        Janerator.generators.put(Level.NETHER, createNetherGenerator());
        Janerator.generators.put(Level.END, createEndGenerator());
    }

    private static FlatLevelSource createOverworldGenerator() {
        List<FlatLayerInfo> layers = new ArrayList<>();

        layers.add(new FlatLayerInfo(1, Blocks.BEDROCK));
        layers.add(new FlatLayerInfo(63, Blocks.DEEPSLATE));

        layers.add(new FlatLayerInfo(60, Blocks.STONE));
        layers.add(new FlatLayerInfo(2, Blocks.DIRT));
        layers.add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));

        return createGenerator(layers, Biomes.MUSHROOM_FIELDS);
    }

    private static FlatLevelSource createNetherGenerator() {
        List<FlatLayerInfo> layers = new ArrayList<>();

        layers.add(new FlatLayerInfo(1, Blocks.BEDROCK));

        layers.add(new FlatLayerInfo(30, Blocks.NETHERRACK));
        layers.add(new FlatLayerInfo(1, Blocks.WARPED_NYLIUM));

        return createGenerator(layers, Biomes.DEEP_DARK);
    }

    private static FlatLevelSource createEndGenerator() {
        List<FlatLayerInfo> layers = new ArrayList<>();

        layers.add(new FlatLayerInfo(1, Blocks.BEDROCK));

        layers.add(new FlatLayerInfo(59, Blocks.STONE));
        layers.add(new FlatLayerInfo(2, Blocks.DIRT));
        layers.add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));

        return createGenerator(layers, Biomes.DEEP_DARK);
    }

    private static FlatLevelSource createGenerator(List<FlatLayerInfo> layers, ResourceKey<Biome> biome) {
        List<Holder<PlacedFeature>> placedFeatures = List.of();
        Optional<HolderSet<StructureSet>> optional = Optional.of(HolderSet.direct());

        Holder<Biome> biomeHolder = Janerator.getRegistry(Registries.BIOME).getHolderOrThrow(biome);
        return new FlatLevelSource(new FlatLevelGeneratorSettings(optional, biomeHolder, placedFeatures)
                .withBiomeAndLayers(layers, optional, biomeHolder));
    }
}

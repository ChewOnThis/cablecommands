package net.earthcomputer.clientcommands.command;
import net.minecraft.world.phys.AABB;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.camel.Camel; // If using Minecraft 1.20 or later
import net.minecraft.world.entity.animal.Pig;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;
import org.joml.Math;

import static dev.xpple.clientarguments.arguments.CBlockPosArgument.blockPos;
import static dev.xpple.clientarguments.arguments.CBlockPosArgument.getBlockPos;
import static dev.xpple.clientarguments.arguments.CBlockPredicateArgument.blockPredicate;
import static dev.xpple.clientarguments.arguments.CBlockPredicateArgument.getBlockPredicate;
import static dev.xpple.clientarguments.arguments.CBlockStateArgument.blockState;
import static dev.xpple.clientarguments.arguments.CBlockStateArgument.getBlockState;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class GhostBlockCommand {

    private static final SimpleCommandExceptionType SET_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.setblock.failed"));
    private static final SimpleCommandExceptionType FILL_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.fill.failed"));

    // Variables to control the continuous circle update
    private static boolean isCircleRunning = false;
    private static BlockPos circleCenter = null;
    private static int circleRadius = 0;
    private static BlockState circleBlockState = null;

    // Variables for the surf command
    private static boolean isSurfRunning = false;
    private static int surfRadius = 0;
    private static BlockState surfBlockState = null;

    private static Integer surfYLevel = null; // This will store the custom Y level


    // Register the tick event handler
    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onTick(client));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("cghostblock")
            .then(literal("set")
                .then(argument("pos", blockPos())
                    .then(argument("block", blockState(context))
                        .executes(ctx -> setGhostBlock(ctx.getSource(), getBlockPos(ctx, "pos"), getBlockState(ctx, "block").getState())))))
            .then(literal("fill")
                .then(argument("from", blockPos())
                    .then(argument("to", blockPos())
                        .then(argument("block", blockState(context))
                            .executes(ctx -> fillGhostBlocks(
                                ctx.getSource(),
                                getBlockPos(ctx, "from"),
                                getBlockPos(ctx, "to"),
                                getBlockState(ctx, "block").getState(),
                                pos -> true
                            )))
                            .then(literal("replace")
                                .then(argument("filter", blockPredicate(context))
                                    .executes(ctx -> fillGhostBlocks(
                                        ctx.getSource(),
                                        getBlockPos(ctx, "from"),
                                        getBlockPos(ctx, "to"),
                                        getBlockState(ctx, "block").getState(),
                                        getBlockPredicate(ctx, "filter")
                                    )))))))
            .then(literal("circle")
                .then(argument("diameter", IntegerArgumentType.integer(1))
                    .then(argument("block", blockState(context))
                        .executes(ctx -> startCircleGhostBlocks(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "diameter"), getBlockState(ctx, "block").getState())))))
            .then(literal("surf")
                .then(argument("diameter", IntegerArgumentType.integer(1))
                    .then(argument("block", blockState(context))
                        .executes(ctx -> startSurfGhostBlocks(
                            ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "diameter"),
                            getBlockState(ctx, "block").getState(),
                            null // Use default entity-based Y level adjustment
                        ))
                        .then(argument("yLevel", integer()) // Allow specifying Y level directly
                            .executes(ctx -> startSurfGhostBlocks(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "diameter"),
                                getBlockState(ctx, "block").getState(),
                                IntegerArgumentType.getInteger(ctx, "yLevel") // Pass the custom Y level as an Integer
                            ))))))
            // Reintroduce the stop command
            .then(literal("stop")
                .executes(ctx -> stopGhostBlockReplacement(ctx.getSource()))));
    }
    

// Method to stop both the circle and surf ghost block replacements
private static int stopGhostBlockReplacement(FabricClientCommandSource source) {
    isCircleRunning = false;
    isSurfRunning = false;
    surfYLevel = null; // Reset the surf Y level if it was set manually
    source.sendFeedback(Component.literal("Stopped ghost block replacement."));
    return Command.SINGLE_SUCCESS;
}

    private static int setGhostBlock(FabricClientCommandSource source, BlockPos pos, BlockState state) throws CommandSyntaxException {
        ClientLevel level = source.getWorld();
        assert level != null;

        checkLoaded(level, pos);

        boolean result = level.setBlock(pos, state, 18);
        if (result) {
            source.sendFeedback(Component.translatable("commands.cghostblock.set.success"));
            return Command.SINGLE_SUCCESS;
        } else {
            throw SET_FAILED_EXCEPTION.create();
        }
    }

    private static int fillGhostBlocks(FabricClientCommandSource source, BlockPos from, BlockPos to, BlockState state, Predicate<BlockInWorld> filter) throws CommandSyntaxException {
        ClientLevel level = source.getWorld();
        assert level != null;

        checkLoaded(level, from);
        checkLoaded(level, to);

        int successCount = 0;

        try {
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                if (filter.test(new BlockInWorld(level, pos, true))) {
                    if (level.setBlock(pos, state, 18)) {
                        successCount++;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw FILL_FAILED_EXCEPTION.create();
        }

        if (successCount == 0) {
            throw FILL_FAILED_EXCEPTION.create();
        }

        source.sendFeedback(Component.translatable("commands.cghostblock.fill.success", successCount));

        return successCount;
    }

    // Method to start the continuous circle update
    private static int startCircleGhostBlocks(FabricClientCommandSource source, int diameter, BlockState state) {
        circleRadius = diameter / 2;
        circleBlockState = state;
        isCircleRunning = true;
        source.sendFeedback(Component.literal("Started circle ghost block replacement."));
        return Command.SINGLE_SUCCESS;
    }

    // Method to start the continuous surf update
    private static int startSurfGhostBlocks(FabricClientCommandSource source, int diameter, BlockState state, @Nullable Integer yLevel) {
        surfRadius = diameter / 2;
        surfBlockState = state;
    
        if (yLevel == null) {
            isSurfRunning = true; // Use entity-based Y adjustment
        } else {
            surfYLevel = yLevel; // Use provided Y level
            isSurfRunning = true;
        }
    
        source.sendFeedback(Component.literal("Started surf ghost block replacement."));
        return Command.SINGLE_SUCCESS;
    }
    

    

    // Method to stop the continuous circle or surf update
    private static int stopCircleGhostBlocks(FabricClientCommandSource source) {
        isCircleRunning = false;
        isSurfRunning = false;
        source.sendFeedback(Component.literal("Stopped ghost block replacement."));
        return Command.SINGLE_SUCCESS;
    }

    // Tick event handler that runs every tick
    private static void onTick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }

        if (isCircleRunning) {
            runCircleReplacement(client, level);
        }

        if (isSurfRunning) {
            runSurfReplacement(client, level);
        }
    }

    private static void runCircleReplacement(Minecraft client, ClientLevel level) {
        if (client.player == null) {
            return; // No player, can't proceed
        }
    
        // Update circleCenter to player's current position
        circleCenter = client.player.blockPosition();
    
        BlockPos center = circleCenter;
        int radius = circleRadius;
        BlockState state = circleBlockState;
    
        int startX = center.getX() - radius;
        int endX = center.getX() + radius;
        int startZ = center.getZ() - radius;
        int endZ = center.getZ() + radius;
    
        int playerY = center.getY();
    
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                int dx = x - center.getX();
                int dz = z - center.getZ();
                if (dx * dx + dz * dz <= radius * radius) {
                    BlockPos pos = new BlockPos(x, playerY, z);
                    if (!level.hasChunkAt(pos)) {
                        continue;
                    }
    
                    // Ensure we check blocks at the correct Y level (from the surface downward)
                    for (int y = playerY; y >= level.getMinBuildHeight(); y--) {
                        BlockPos currentPos = new BlockPos(x, y, z);
                        BlockState blockState = level.getBlockState(currentPos);
                        if (!blockState.isAir()) {
                            pos = currentPos;
                            break;
                        }
                    }
    
                    // Check if the block at the position should be replaced
                    if (shouldReplaceBlock(level, pos)) {
                        level.setBlock(pos, state, 18); // Replace the block if allowed
                    }
                }
            }
        }
    }
    




    private static void runSurfReplacement(Minecraft client, ClientLevel level) {
        if (client.player == null) {
            return; // No player, can't proceed
        }
    
        int y;
        
        // Check if a custom Y level is set
        if (surfYLevel != null) {
            y = surfYLevel; // Use the custom Y level provided by the user
        } else {
            // Use the default entity-based adjustment if no Y level is provided
            Entity vehicle = client.player.getVehicle();
            int yAdjustment;
    
            if (vehicle instanceof Boat) {
                yAdjustment = 0;
            } else if (vehicle instanceof Horse || vehicle instanceof Camel || vehicle instanceof Pig) {
                yAdjustment = -1;
            } else {
                yAdjustment = -1; // Default adjustment when on foot or other entities
            }
    
            y = client.player.blockPosition().getY() + yAdjustment;
        }
    
        BlockPos center = client.player.blockPosition();
        int radius = surfRadius;
        BlockState state = surfBlockState;
    
        int startX = center.getX() - radius;
        int endX = center.getX() + radius;
        int startZ = center.getZ() - radius;
        int endZ = center.getZ() + radius;
    
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                int dx = x - center.getX();
                int dz = z - center.getZ();
                if (dx * dx + dz * dz <= radius * radius) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(pos)) {
                        continue;
                    }
                    level.setBlock(pos, state, 18);
                }
            }
        }
    }
    
    

  
/* 
BOAT METHOD SURF
   private static void runSurfReplacement(Minecraft client, ClientLevel level) {
    if (client.player == null) {
        return; // No player, can't proceed
    }

    BlockPos center = client.player.blockPosition();
    int radius = surfRadius;
    BlockState state = surfBlockState;

    int y;
    if (client.player.getVehicle() instanceof Boat) {
        y = center.getY() - 2; // Adjusted Y-coordinate when in a boat
    } else {
        y = center.getY() - 1; // Normal adjustment
    }

    int startX = center.getX() - radius;
    int endX = center.getX() + radius;
    int startZ = center.getZ() - radius;
    int endZ = center.getZ() + radius;

    for (int x = startX; x <= endX; x++) {
        for (int z = startZ; z <= endZ; z++) {
            int dx = x - center.getX();
            int dz = z - center.getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                level.setBlock(pos, state, 18);
            }
        }
    }
}
    */

    // Method to determine if the block should be replaced
// Method to determine if the block should be replaced
private static boolean shouldReplaceBlock(ClientLevel level, BlockPos pos) {
    BlockState blockState = level.getBlockState(pos);
    Block block = blockState.getBlock();
    
    // Check if the block is in the exception list (e.g. glass, stained glass, etc.)
    if (EXCEPTION_TRANSPARENT_BLOCKS.contains(block)) {
        return false; // Do not replace this block
    }

    // Check if the block is a flower, grass, or other blocks that should not be replaced
    if (isFlowerOrGrass(block)) {
        return false; // Do not replace this block
    }

    // If the block is not air and not in the exception list, it can be replaced
    return !blockState.isAir();
}



    // Method to check if a block is a flower or grass
    private static boolean isFlowerOrGrass(Block block) {
        return block == Blocks.DANDELION
            || block == Blocks.POPPY
            || block == Blocks.AIR
            || block == Blocks.BLUE_ORCHID
            || block == Blocks.ALLIUM
            || block == Blocks.AZURE_BLUET
            || block == Blocks.RED_TULIP
            || block == Blocks.ORANGE_TULIP
            || block == Blocks.WHITE_TULIP
            || block == Blocks.PINK_TULIP
            || block == Blocks.OXEYE_DAISY
            || block == Blocks.CORNFLOWER
            || block == Blocks.LILY_OF_THE_VALLEY
            || block == Blocks.WITHER_ROSE
            || block == Blocks.SUNFLOWER
            || block == Blocks.LILAC
            || block == Blocks.ROSE_BUSH
            || block == Blocks.PEONY
            || block == Blocks.SHORT_GRASS
            || block == Blocks.TALL_GRASS
            || block == Blocks.FERN
            || block == Blocks.LARGE_FERN
            || block == Blocks.DEAD_BUSH
            // Add other blocks as needed
            ;
    }

    private static void checkLoaded(ClientLevel level, BlockPos pos) throws CommandSyntaxException {
        if (!level.hasChunkAt(pos)) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.setblock.failed")).create();
        } else if (!level.isInWorldBounds(pos)) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.setblock.failed")).create();
        }
    }

    private static final Set<Block> EXCEPTION_TRANSPARENT_BLOCKS = new HashSet<>();

    static {
        // Initialize EXCEPTION_TRANSPARENT_BLOCKS with all stained glass blocks
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.TINTED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.WHITE_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.ORANGE_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.MAGENTA_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.LIGHT_BLUE_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.YELLOW_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.LIME_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.PINK_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.GRAY_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.LIGHT_GRAY_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.CYAN_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.PURPLE_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.BLUE_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.BROWN_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.GREEN_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.RED_STAINED_GLASS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.BLACK_STAINED_GLASS);
    
        // Add all stairs
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.OAK_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.SPRUCE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.BIRCH_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.JUNGLE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.ACACIA_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.DARK_OAK_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.CRIMSON_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.WARPED_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.STONE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.COBBLESTONE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.BRICK_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.STONE_BRICK_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.NETHER_BRICK_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.SANDSTONE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.RED_SANDSTONE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.QUARTZ_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.PURPUR_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.PRISMARINE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.PRISMARINE_BRICK_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.DARK_PRISMARINE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.POLISHED_GRANITE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.SMOOTH_RED_SANDSTONE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.MOSSY_STONE_BRICK_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.POLISHED_DIORITE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.MOSSY_COBBLESTONE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.END_STONE_BRICK_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.SMOOTH_SANDSTONE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.SMOOTH_QUARTZ_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.GRANITE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.ANDESITE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.RED_NETHER_BRICK_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.POLISHED_ANDESITE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.DIORITE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.BLACKSTONE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.POLISHED_BLACKSTONE_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.OXIDIZED_CUT_COPPER_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.WEATHERED_CUT_COPPER_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.EXPOSED_CUT_COPPER_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.CUT_COPPER_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.WAXED_CUT_COPPER_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS);
        // Add any other stairs introduced in newer versions
    
        // Add all slabs
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.OAK_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.SPRUCE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.BIRCH_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.JUNGLE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.ACACIA_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.DARK_OAK_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.CRIMSON_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.WARPED_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.STONE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.SMOOTH_STONE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.SANDSTONE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.CUT_SANDSTONE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.PETRIFIED_OAK_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.COBBLESTONE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.BRICK_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.STONE_BRICK_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.NETHER_BRICK_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.QUARTZ_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.RED_SANDSTONE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.CUT_RED_SANDSTONE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.PURPUR_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.PRISMARINE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.PRISMARINE_BRICK_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.DARK_PRISMARINE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.POLISHED_GRANITE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.SMOOTH_RED_SANDSTONE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.MOSSY_STONE_BRICK_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.POLISHED_DIORITE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.MOSSY_COBBLESTONE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.END_STONE_BRICK_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.SMOOTH_SANDSTONE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.SMOOTH_QUARTZ_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.GRANITE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.ANDESITE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.RED_NETHER_BRICK_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.POLISHED_ANDESITE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.DIORITE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.BLACKSTONE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.POLISHED_BLACKSTONE_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.OXIDIZED_CUT_COPPER_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.WEATHERED_CUT_COPPER_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.EXPOSED_CUT_COPPER_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.CUT_COPPER_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.WAXED_CUT_COPPER_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB);
        EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB);
    
        // Add more transparent exception blocks as needed
        // EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.GLASS_PANE);
        // EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.IRON_BARS);
        // EXCEPTION_TRANSPARENT_BLOCKS.add(Blocks.STONE_BRICK_WALL);
    }
}

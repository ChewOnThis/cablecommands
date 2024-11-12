package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import static com.mojang.brigadier.arguments.FloatArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static dev.xpple.clientarguments.arguments.CParticleArgument.*;
import static dev.xpple.clientarguments.arguments.CVec3Argument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import static com.mojang.brigadier.arguments.FloatArgumentType.floatArg;
import static com.mojang.brigadier.arguments.FloatArgumentType.getFloat;
import java.util.ArrayList;
import java.util.List;

public class CParticleCommand {

    private static final List<ContrailConfig> activeContrails = new ArrayList<>();

    private static class ContrailConfig {
        ParticleOptions particle;
        float distance;
        float height;
        Vec3 delta;
        float speed;
        int count;
        boolean force;

        ContrailConfig(ParticleOptions particle, float distance, float height, Vec3 delta, float speed, int count, boolean force) {
            this.particle = particle;
            this.distance = distance;
            this.height = height;
            this.delta = delta;
            this.speed = speed;
            this.count = count;
            this.force = force;
        }
    }

    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onTick(client));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("cparticle")
            .then(literal("contrail")
                .then(argument("particle", particle(context))
                    .then(argument("distance", floatArg(-100.0f, 100.0f))  // Allow for negative, zero, and decimal values
                        .then(argument("height", floatArg(-100.0f, 100.0f))  // Allow for negative, zero, and decimal values
                            .then(argument("delta", vec3(false))
                                .then(argument("speed", floatArg(0))
                                    .then(argument("count", integer(1))
                                        .executes(ctx -> addContrail(
                                            ctx.getSource(),
                                            getParticle(ctx, "particle"),
                                            getFloat(ctx, "distance"),
                                            getFloat(ctx, "height"),
                                            getVec3(ctx, "delta"),
                                            getFloat(ctx, "speed"),
                                            getInteger(ctx, "count"),
                                            false
                                        ))
                                        .then(literal("force")
                                            .executes(ctx -> addContrail(
                                                ctx.getSource(),
                                                getParticle(ctx, "particle"),
                                                getFloat(ctx, "distance"),
                                                getFloat(ctx, "height"),
                                                getVec3(ctx, "delta"),
                                                getFloat(ctx, "speed"),
                                                getInteger(ctx, "count"),
                                                true
                                            ))
                                            .then(literal("with")
                                                .then(argument("particle", particle(context))
                                                    .then(argument("distance", floatArg(-100.0f, 100.0f))
                                                        .then(argument("height", floatArg(-100.0f, 100.0f))
                                                            .then(argument("delta", vec3(false))
                                                                .then(argument("speed", floatArg(0))
                                                                    .then(argument("count", integer(1))
                                                                        .executes(ctx -> addContrail(
                                                                            ctx.getSource(),
                                                                            getParticle(ctx, "particle"),
                                                                            getFloat(ctx, "distance"),
                                                                            getFloat(ctx, "height"),
                                                                            getVec3(ctx, "delta"),
                                                                            getFloat(ctx, "speed"),
                                                                            getInteger(ctx, "count"),
                                                                            false
                                                                        ))
                                                                        .then(literal("force")
                                                                            .executes(ctx -> addContrail(
                                                                                ctx.getSource(),
                                                                                getParticle(ctx, "particle"),
                                                                                getFloat(ctx, "distance"),
                                                                                getFloat(ctx, "height"),
                                                                                getVec3(ctx, "delta"),
                                                                                getFloat(ctx, "speed"),
                                                                                getInteger(ctx, "count"),
                                                                                true
                                                                            ))
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
            .then(literal("stopContrail")
                .executes(ctx -> stopAllContrails(ctx.getSource()))
            )
        );
    }

    private static int addContrail(FabricClientCommandSource source, ParticleOptions particle, float distance, float height, Vec3 delta, float speed, int count, boolean force) {
        activeContrails.add(new ContrailConfig(particle, distance, height, delta, speed, count, force));
        source.sendFeedback(Component.literal("Added contrail with particles."));
        return Command.SINGLE_SUCCESS;
    }

    private static int stopAllContrails(FabricClientCommandSource source) {
        activeContrails.clear();
        source.sendFeedback(Component.literal("Stopped all contrails."));
        return Command.SINGLE_SUCCESS;
    }

    private static void onTick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || activeContrails.isEmpty() || client.player == null) {
            return;
        }

        Vec3 playerPos = client.player.position();
        double yaw = Math.toRadians(client.player.getYRot());

        for (ContrailConfig config : activeContrails) {
            Vec3 contrailPos = playerPos.add(-Math.sin(yaw) * config.distance, config.height, Math.cos(yaw) * config.distance);
            for (int i = 0; i < config.count; i++) {
                double offsetX = config.delta.x * level.random.nextGaussian();
                double offsetY = config.delta.y * level.random.nextGaussian();
                double offsetZ = config.delta.z * level.random.nextGaussian();

                level.addParticle(
                    config.particle,
                    config.force,
                    contrailPos.x + offsetX,
                    contrailPos.y + offsetY,
                    contrailPos.z + offsetZ,
                    config.delta.x * config.speed,
                    config.delta.y * config.speed,
                    config.delta.z * config.speed
                );
            }
        }
    }
}

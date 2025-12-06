package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.Senrenbanka;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.Scaffold;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.Rotation;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(
        name = "BedAura",
        category = Category.MISC,
        description = "Automatically finds and breaks nearby beds"
)
public class BedAura extends Module {
    private final FloatValue range = ValueBuilder.create(this, "Range")
            .setDefaultFloatValue(4.0F)
            .setMinFloatValue(1.0F)
            .setMaxFloatValue(6.0F)
            .setFloatStep(0.5F)
            .build()
            .getFloatValue();
    private final FloatValue delay = ValueBuilder.create(this, "Delay")
            .setDefaultFloatValue(150.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(5000.0F)
            .setFloatStep(50.0F)
            .build()
            .getFloatValue();
    private final BooleanValue rotations = ValueBuilder.create(this, "Rotations")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();
    private final BooleanValue scatter = ValueBuilder.create(this, "Scatter")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();
    private final BooleanValue swing = ValueBuilder.create(this, "Swing")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();
    private final BooleanValue mark = ValueBuilder.create(this, "Mark")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();
    private final BooleanValue priorityNearest = ValueBuilder.create(this, "Priority Nearest")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();
    private final BooleanValue auraCheck = ValueBuilder.create(this, "Aura Check")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();
    private final BooleanValue scaffoldCheck = ValueBuilder.create(this, "Scaffold Check")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private BlockPos targetBed;
    private boolean needUpdate = false;
    private long lastActionTime = 0;

    private final List<Direction> directionOffsets = List.of(
            Direction.EAST, Direction.WEST,
            Direction.UP, Direction.SOUTH, Direction.NORTH
    );
    @EventTarget
    public void onMotion(EventMotion event) {
        if (shouldPause()) return;
        if (!event.getType().toString().equals("PRE")) return;

        if (needUpdate) {
            Minecraft.getInstance().options.keyAttack.setDown(false);
            needUpdate = false;
        }
        targetBed = findNearbyBed(range.getCurrentValue());
        if (targetBed == null) return;
        if (rotations.getCurrentValue()) {
            Rotation rotation = RotationUtils.getRotations(targetBed, 1.0f);
            event.setYaw(rotation.getYaw());
            event.setPitch(rotation.getPitch());
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActionTime < delay.getCurrentValue()) return;
        if (canSeeBed(targetBed)) {
            Minecraft.getInstance().options.keyAttack.setDown(true);
            needUpdate = true;
            lastActionTime = currentTime;

            if (swing.getCurrentValue()) {
                Minecraft.getInstance().player.swing(InteractionHand.MAIN_HAND);
            }
        } else if (scatter.getCurrentValue()) {
            tryScatterBreak(targetBed);
            lastActionTime = currentTime;
        }
    }
    private boolean shouldPause() {
        if (scaffoldCheck.getCurrentValue()) {
            Scaffold scaffoldModule = (Scaffold) Senrenbanka.getInstance().getModuleManager().getModule(Scaffold.class);
            if (scaffoldModule != null && scaffoldModule.isEnabled()) {
                return true;
            }
        }

        if (auraCheck.getCurrentValue()) {
            Aura auraModule = (Aura) Senrenbanka.getInstance().getModuleManager().getModule(Aura.class);
            if (auraModule != null && auraModule.isEnabled() && Aura.target != null) {
                return true;
            }
        }

        return false;
    }
    @EventTarget
    public void onRender(EventRender event) {
        if (targetBed != null && mark.getCurrentValue()) {
            RenderUtils.drawOutlinedBox(
                    new net.minecraft.world.phys.AABB(targetBed),
                    event.getPMatrixStack()
            );
        }
    }
    @EventTarget
    public void onPacket(EventPacket event) {
    }
    public void onUpdate() {
    }

    public void onWorldChange() {
        targetBed = null;
        needUpdate = false;
        lastActionTime = 0;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        targetBed = null;
        needUpdate = false;
        lastActionTime = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (needUpdate) {
            Minecraft.getInstance().options.keyAttack.setDown(false);
            needUpdate = false;
        }
    }

    private BlockPos findNearbyBed(float range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;

        int rangeInt = (int) Math.ceil(range);
        BlockPos playerPos = mc.player.blockPosition();
        List<BlockPos> foundBeds = new ArrayList<>();
        for (int x = -rangeInt; x <= rangeInt; x++) {
            for (int y = -rangeInt; y <= rangeInt; y++) {
                for (int z = -rangeInt; z <= rangeInt; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.getBlock() instanceof BedBlock) {
                        double distance = mc.player.distanceToSqr(
                                pos.getX() + 0.5,
                                pos.getY() + 0.5,
                                pos.getZ() + 0.5
                        );

                        if (distance <= range * range) {
                            foundBeds.add(pos);
                        }
                    }
                }
            }
        }
        if (foundBeds.isEmpty()) return null;
        if (priorityNearest.getCurrentValue()) {
            foundBeds.sort(Comparator.comparingDouble(bedPos ->
                    mc.player.distanceToSqr(
                            bedPos.getX() + 0.5,
                            bedPos.getY() + 0.5,
                            bedPos.getZ() + 0.5
                    )
            ));
            return foundBeds.get(0);
        } else {
            return foundBeds.get(0);
        }
    }
    private boolean canSeeBed(BlockPos bedPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Vec3 eyePos = mc.player.getEyePosition();
        VoxelShape shape = mc.level.getBlockState(bedPos).getShape(mc.level, bedPos);
        if (shape.isEmpty()) return false;
        for (Direction direction : Direction.values()) {
            net.minecraft.world.phys.AABB faceBounds = shape.bounds().move(bedPos);
            Vec3 faceCenter = new Vec3(
                    faceBounds.minX + (faceBounds.maxX - faceBounds.minX) / 2,
                    faceBounds.minY + (faceBounds.maxY - faceBounds.minY) / 2,
                    faceBounds.minZ + (faceBounds.maxZ - faceBounds.minZ) / 2
            );

            BlockHitResult result = mc.level.clip(
                    new net.minecraft.world.level.ClipContext(
                            eyePos,
                            faceCenter,
                            net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE,
                            mc.player
                    )
            );
            if (result.getType() == net.minecraft.world.phys.HitResult.Type.MISS ||
                    result.getBlockPos().equals(bedPos)) {
                return true;
            }
        }

        return false;
    }

    private void tryScatterBreak(BlockPos bedPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        for (Direction direction : directionOffsets) {
            BlockPos targetPos = bedPos.relative(direction);
            BlockState state = mc.level.getBlockState(targetPos);
            if (state.isAir() || state.getBlock() instanceof BedBlock) continue;
            mc.getConnection().send(
                    new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                            targetPos,
                            direction
                    )
            );
            mc.getConnection().send(
                    new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                            targetPos,
                            direction
                    )
            );

            if (swing.getCurrentValue()) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
            break;
        }
    }
}
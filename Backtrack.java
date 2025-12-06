package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.Senrenbanka;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.*;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.PacketSnapshot;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.SmoothAnimationTimer;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

@ModuleInfo(
        name = "Backtrack",
        description = "Stuck Network,but adversaries",
        category = Category.COMBAT
)
public class Backtrack extends Module {

    private static class TrackedPlayer {
        Vec3 serverPosition;
        Vec3 frozenPosition;
        long lastUpdateTime;

        TrackedPlayer(Vec3 initialPosition) {
            this.serverPosition = initialPosition;
            this.frozenPosition = initialPosition;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        void updateServerPosition(Vec3 newPosition) {
            this.serverPosition = newPosition;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }

    // ========== 基础设置 ==========
    public BooleanValue log = ValueBuilder.create(this, "Logging")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    public ModeValue delayMode = ValueBuilder.create(this, "Delay Mode")
            .setDefaultModeIndex(0)
            .setModes("Ticks", "Milliseconds")
            .build()
            .getModeValue();

    // ========== 触发条件设置 ==========
    public BooleanValue onlyWhenAuraTarget = ValueBuilder.create(this, "Only When Aura Target")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();
    public BooleanValue targetNearbyCheck = ValueBuilder.create(this, "Target Nearby Check")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();
    public FloatValue minDistance = ValueBuilder.create(this, "Min Distance")
            .setDefaultFloatValue(0.0F)
            .setFloatStep(0.1F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(10.0F)
            .build()
            .getFloatValue();
    public FloatValue maxDistance = ValueBuilder.create(this, "Max Distance")
            .setDefaultFloatValue(3.0F)
            .setFloatStep(0.1F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(10.0F)
            .build()
            .getFloatValue();

    // ========== 数据包控制设置 ==========
    public BooleanValue randomPacket = ValueBuilder.create(this, "RandomPacket")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();
    public FloatValue randomMaxPacket = ValueBuilder.create(this, "RandomMaxPacket")
            .setVisibility(this.randomPacket::getCurrentValue)
            .setDefaultFloatValue(60F)
            .setFloatStep(5F)
            .setMinFloatValue(1F)
            .setMaxFloatValue(450F)
            .build()
            .getFloatValue();
    public FloatValue randomMinPacket = ValueBuilder.create(this, "RandomMinPacket")
            .setVisibility(this.randomPacket::getCurrentValue)
            .setDefaultFloatValue(30F)
            .setFloatStep(5F)
            .setMinFloatValue(1F)
            .setMaxFloatValue(450F)
            .build()
            .getFloatValue();
    public FloatValue maxpacket = ValueBuilder.create(this, "Max Packet number")
            .setVisibility(() -> !this.randomPacket.getCurrentValue())
            .setDefaultFloatValue(45F)
            .setFloatStep(5F)
            .setMinFloatValue(1F)
            .setMaxFloatValue(450F)
            .build()
            .getFloatValue();

    // ========== 延迟设置 ==========
    public BooleanValue randomDelay = ValueBuilder.create(this, "RandomDelay")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();
    public FloatValue randomMaxDelay = ValueBuilder.create(this, "RandomMaxDelay")
            .setVisibility(this.randomDelay::getCurrentValue)
            .setDefaultFloatValue(30F)
            .setFloatStep(1F)
            .setMinFloatValue(1F)
            .setMaxFloatValue(200F)
            .build()
            .getFloatValue();
    public FloatValue randomMinDelay = ValueBuilder.create(this, "RandomMinDelay")
            .setVisibility(this.randomDelay::getCurrentValue)
            .setDefaultFloatValue(10F)
            .setFloatStep(1F)
            .setMinFloatValue(1F)
            .setMaxFloatValue(200F)
            .build()
            .getFloatValue();
    FloatValue delayTicks = ValueBuilder.create(this, "Delay(Tick)")
            .setVisibility(() -> !this.randomDelay.getCurrentValue() && delayMode.isCurrentMode("Ticks"))
            .setDefaultFloatValue(20F)
            .setFloatStep(1F)
            .setMinFloatValue(1F)
            .setMaxFloatValue(200F)
            .build()
            .getFloatValue();
    FloatValue delayMs = ValueBuilder.create(this, "Delay(MS)")
            .setVisibility(() -> !this.randomDelay.getCurrentValue() && delayMode.isCurrentMode("Milliseconds"))
            .setDefaultFloatValue(500F)
            .setFloatStep(10F)
            .setMinFloatValue(0F)
            .setMaxFloatValue(2000F)
            .build()
            .getFloatValue();

    // ========== 释放条件设置 ==========
    public BooleanValue OnGroundStop = ValueBuilder.create(this, "OnGroundStop")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();
    public BooleanValue onVelocityRelease = ValueBuilder.create(this, "OnVelocityRelease")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();
    public BooleanValue releaseWhenTP = ValueBuilder.create(this, "Release When TP/BPS Abnormal")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();
    public FloatValue bpsThreshold = ValueBuilder.create(this, "BPS Threshold")
            .setDefaultFloatValue(12.0F)
            .setFloatStep(0.5F)
            .setMinFloatValue(5.0F)
            .setMaxFloatValue(50.0F)
            .build()
            .getFloatValue();
    public BooleanValue updateMyselfHealth = ValueBuilder.create(this, "UpdateMyselfHealth")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    // ========== 拦截模式设置 ==========
    public ModeValue interceptMode = ValueBuilder.create(this, "Intercept Mode")
            .setDefaultModeIndex(1)
            .setModes("Original", "AllExceptSelf", "OnlyTarget")
            .build()
            .getModeValue();
    public BooleanValue debugFilter = ValueBuilder.create(this, "Debug Filter")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    // ========== 渲染设置 ==========
    public BooleanValue btrender = ValueBuilder.create(this, "Render")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();
    public ModeValue btrendermode = ValueBuilder.create(this, "Render Mode")
            .setVisibility(this.btrender::getCurrentValue)
            .setDefaultModeIndex(0)
            .setModes("Normal", "Senrenbanka", "ServerPosition")
            .build()
            .getModeValue();
    public FloatValue boxRed = ValueBuilder.create(this, "Box Red")
            .setDefaultFloatValue(0F)
            .setFloatStep(5F)
            .setMinFloatValue(0F)
            .setMaxFloatValue(255F)
            .build()
            .getFloatValue();
    public FloatValue boxGreen = ValueBuilder.create(this, "Box Green")
            .setDefaultFloatValue(150F)
            .setFloatStep(5F)
            .setMinFloatValue(0F)
            .setMaxFloatValue(255F)
            .build()
            .getFloatValue();
    public FloatValue boxBlue = ValueBuilder.create(this, "Box Blue")
            .setDefaultFloatValue(255F)
            .setFloatStep(5F)
            .setMinFloatValue(0F)
            .setMaxFloatValue(255F)
            .build()
            .getFloatValue();

    // ========== 核心变量 ==========
    public boolean btwork = false;
    private final LinkedBlockingDeque<Packet<?>> airKBQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingQueue<PacketSnapshot> timeBasedQueue = new LinkedBlockingQueue<>();
    private final List<Integer> knockbackPositions = new ArrayList<>();
    private final Map<Integer, TrackedPlayer> trackedEnemies = new ConcurrentHashMap<>();
    private final Map<Entity, Vec3> serverPositions = new HashMap<>();
    private boolean isInterceptingAirKB = false;
    private int interceptedPacketCount = 0;
    private int delayTickCounter = 0;
    private boolean shouldCheckGround = false;
    public String trackingText = "";
    private static final float PROGRESS_BAR_WIDTH = 200.0f;
    private static final float PROGRESS_BAR_HEIGHT = 10.0f;
    private static final float PROGRESS_BAR_Y_OFFSET = 65.0f;
    private static final int BACKGROUND_COLOR = 0x80000000;
    private static final int PROGRESS_COLOR = 0xFF66CCFF;
    private static final int OVERFLOW_COLOR = 0xFFFF6B6B;
    private static final float CORNER_RADIUS = 5.0f;
    private double lastPosX = Double.NaN;
    private double lastPosZ = Double.NaN;
    private boolean attackRequested = false;
    private final SmoothAnimationTimer navenProgress = new SmoothAnimationTimer(0.0F, 0.2F);
    private static final int navenMainColor = new Color(150, 45, 45, 255).getRGB();
    private final Random random = new Random();
    private boolean foundTarget = false;
    public boolean alink = false;
    private static final float[] SERVER_POS_COLOR = new float[]{0.78431374F, 0.0F, 0.0F, 0.39215686F};
    private static final double POSITION_THRESHOLD = 0.1;

    private float getRandomPacketCount() {
        if (randomPacket.getCurrentValue()) {
            float min = randomMinPacket.getCurrentValue();
            float max = randomMaxPacket.getCurrentValue();
            return min + random.nextFloat() * (max - min);
        }
        return maxpacket.getCurrentValue();
    }

    private float getRandomDelay() {
        if (randomDelay.getCurrentValue()) {
            float min = randomMinDelay.getCurrentValue();
            float max = randomMaxDelay.getCurrentValue();
            return min + random.nextFloat() * (max - min);
        }
        return delayMode.isCurrentMode("Ticks") ? delayTicks.getCurrentValue() : delayMs.getCurrentValue();
    }

    @Override
    public void onEnable() {
        reset();
        if (mc != null && mc.player != null) {
            lastPosX = mc.player.getX();
            lastPosZ = mc.player.getZ();
        } else {
            lastPosX = Double.NaN;
            lastPosZ = Double.NaN;
        }
    }

    @Override
    public void onDisable() {
        releaseAllPacketQueue();
        reset();
    }

    public void releaseAllPacketQueue() {
        releaseAirKBQueue();
        releaseTimeBasedQueue();
    }

    public int getPacketCount() {
        return delayMode.isCurrentMode("Ticks") ? airKBQueue.size() : timeBasedQueue.size();
    }

    public void reset() {
        releaseAllPacketQueue();
        isInterceptingAirKB = false;
        interceptedPacketCount = 0;
        delayTickCounter = 0;
        shouldCheckGround = false;
        btwork = false;
        knockbackPositions.clear();
        trackedEnemies.clear();
        serverPositions.clear();
        lastPosX = Double.NaN;
        lastPosZ = Double.NaN;
        attackRequested = false;
        foundTarget = false;
        alink = false;
    }

    private void releaseAirKBQueue() {
        int packetCount = airKBQueue.size();
        while (!this.airKBQueue.isEmpty()) {
            try {
                Packet<?> packet = this.airKBQueue.poll();
                if (packet != null && mc.getConnection() != null) {
                    ((Packet<ClientGamePacketListener>) packet).handle(mc.getConnection());
                }
            } catch (Exception var3) {
                var3.printStackTrace();
            }
        }
        if (packetCount > 0) {
            log("Release " + packetCount + " Packets");
        }
        interceptedPacketCount = 0;
        knockbackPositions.clear();
    }

    private void releaseTimeBasedQueue() {
        int packetCount = timeBasedQueue.size();
        while (!this.timeBasedQueue.isEmpty()) {
            try {
                PacketSnapshot snapshot = this.timeBasedQueue.poll();
                if (snapshot != null && snapshot.packet != null && mc.getConnection() != null) {
                    ((Packet<ClientGamePacketListener>) snapshot.packet).handle(mc.getConnection());
                }
            } catch (Exception var3) {
                var3.printStackTrace();
            }
        }
        if (packetCount > 0) {
            log("Release " + packetCount + " Packets");
        }
        interceptedPacketCount = 0;
        serverPositions.clear();
    }

    private void releaseTimeBasedPackets() {
        if (mc.player == null) return;
        this.timeBasedQueue.removeIf(it -> {
            if (System.currentTimeMillis() - it.tick >= getRandomDelay()) {
                Packet<?> packet = it.packet;
                if (packet != null && mc.getConnection() != null) {
                    try {
                        ((Packet<ClientGamePacketListener>) packet).handle(mc.getConnection());
                        return true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return false;
        });
    }

    private boolean hasNearbyPlayers() {
        if (mc.level == null || mc.player == null) return false;

        double minSq = (double) minDistance.getCurrentValue() * (double) minDistance.getCurrentValue();
        double maxSq = (double) maxDistance.getCurrentValue() * (double) maxDistance.getCurrentValue();

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (player.isAlive()) {
                double distSq = mc.player.distanceToSqr(player.getX(), player.getY(), player.getZ());
                if (distSq >= minSq && distSq <= maxSq) {
                    return true;
                }
            }
        }
        return false;
    }

    private void log(String message) {
        if (this.log.getCurrentValue()) {
            ChatUtils.addChatMessage("[Backtrack] " + message);
        }
    }

    private boolean isKillAuraTargeting() {
        Module killAura = Senrenbanka.getInstance().getModuleManager().getModule(Aura.class);
        return killAura != null && killAura.isEnabled() && Aura.target != null;
    }

    private boolean isSelfRelatedPacket(Packet<?> packet) {
        if (packet instanceof ClientboundPlayerPositionPacket) return true;
        if (packet instanceof ClientboundSetEntityMotionPacket) return false;
        try {
            Method m = packet.getClass().getMethod("getId");
            if (m.getReturnType() == int.class && mc.player != null) {
                int id = (int) m.invoke(packet);
                boolean self = id == mc.player.getId();
                if (debugFilter.getCurrentValue())
                    log("getId Checker: packet=" + packet.getClass().getSimpleName() + ", id=" + id + ", self=" + self);
                if (self) return true;
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            if (debugFilter.getCurrentValue()) log("Reflection acquisition failed: " + e.getClass().getSimpleName());
        }
        return false;
    }

    private Player getAimedPlayer() {
        if (mc == null || mc.player == null || mc.hitResult == null) return null;
        HitResult hr = mc.hitResult;
        if (hr.getType() == HitResult.Type.ENTITY && hr instanceof EntityHitResult ehr) {
            if (ehr.getEntity() instanceof Player p && p != mc.player && p.isAlive()) {
                return p;
            }
        }
        return null;
    }

    private int getPacketEntityId(Packet<?> packet) {
        try {
            Method m = packet.getClass().getMethod("getId");
            if (m.getReturnType() == int.class) {
                return (int) m.invoke(packet);
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            if (debugFilter.getCurrentValue()) log("getPacketEntityId failed: " + e.getClass().getSimpleName());
        }
        return -1;
    }

    @EventTarget
    public void onTick(EventRunTicks event) {
        if (mc.player == null || mc.level == null) return;

        // 时间模式下的数据包释放
        if (delayMode.isCurrentMode("Milliseconds")) {
            releaseTimeBasedPackets();
        }

        // Freeze enemy positions during interception
        if (isInterceptingAirKB) {
            for (Map.Entry<Integer, TrackedPlayer> entry : trackedEnemies.entrySet()) {
                Entity enemy = mc.level.getEntity(entry.getKey());
                if (enemy != null) {
                    Vec3 frozenPos = entry.getValue().frozenPosition;
                    enemy.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
                }
            }
        }

        btwork = isInterceptingAirKB || shouldCheckGround;

        if (isInterceptingAirKB && releaseWhenTP.getCurrentValue()) {
            double curX = mc.player.getX();
            double curZ = mc.player.getZ();
            if (!Double.isNaN(lastPosX) && !Double.isNaN(lastPosZ)) {
                double dx = curX - lastPosX;
                double dz = curZ - lastPosZ;
                double horizDist = Math.sqrt(dx * dx + dz * dz);
                double bps = horizDist * 20.0;
                if (bps > bpsThreshold.getCurrentValue()) {
                    log("BPS Error! (" + String.format("%.2f", bps) + ")，Release All Packets");
                    isInterceptingAirKB = false;
                    shouldCheckGround = false;
                    releaseAllPacketQueue();
                    resetAfterRelease();
                }
            }
            lastPosX = curX;
            lastPosZ = curZ;
        } else {
            if (mc.player != null) {
                lastPosX = mc.player.getX();
                lastPosZ = mc.player.getZ();
            }
        }

        if (delayMode.isCurrentMode("Ticks") && delayTickCounter > 0) {
            delayTickCounter--;
            return;
        }

        // 目标检测逻辑
        boolean shouldIntercept = true;
        if (onlyWhenAuraTarget.getCurrentValue()) {
            shouldIntercept = isKillAuraTargeting();
        }

        boolean shouldStartByAttack = attackRequested;
        boolean shouldStartByNearby = targetNearbyCheck.getCurrentValue() && hasNearbyPlayers();

        this.foundTarget = shouldStartByNearby || shouldStartByAttack;

        if (!isInterceptingAirKB && (shouldStartByAttack || shouldStartByNearby) && shouldIntercept) {
            isInterceptingAirKB = true;
            shouldCheckGround = false;
            interceptedPacketCount = 0;
            airKBQueue.clear();
            timeBasedQueue.clear();
            knockbackPositions.clear();
            trackedEnemies.clear();
            serverPositions.clear();
            attackRequested = false;

            // Track nearby enemies
            double minSq = (double) minDistance.getCurrentValue() * (double) minDistance.getCurrentValue();
            double maxSq = (double) maxDistance.getCurrentValue() * (double) maxDistance.getCurrentValue();

            for (Player player : mc.level.players()) {
                if (player != mc.player && player.isAlive()) {
                    double distSq = mc.player.distanceToSqr(player.getX(), player.getY(), player.getZ());
                    if (distSq >= minSq && distSq <= maxSq) {
                        trackedEnemies.put(player.getId(), new TrackedPlayer(player.position()));
                        serverPositions.put(player, player.position());
                    }
                }
            }

            if (shouldStartByAttack) {
                log("Start intercepting (on attack)");
            } else {
                log("Checked nearby players, start intercepting packets and freezing enemies.");
            }
        }

        if (isInterceptingAirKB && delayMode.isCurrentMode("Ticks") && interceptedPacketCount >= getRandomPacketCount()) {
            if (OnGroundStop.getCurrentValue()) {
                shouldCheckGround = true;
                log("Max Packet number reached, waiting to land before releasing packets");
            } else {
                log("Release Packet");
                releaseAirKBQueue();
                resetAfterRelease();
            }
        }

        if (shouldCheckGround && mc.player.onGround()) {
            log("Release Packet");
            releaseAirKBQueue();
            resetAfterRelease();
        }

        // Clean up tracked enemies
        if (!trackedEnemies.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            trackedEnemies.keySet().removeIf(entityId -> {
                Entity entity = mc.level.getEntity(entityId);
                TrackedPlayer trackedPlayer = trackedEnemies.get(entityId);
                return entity == null || !entity.isAlive() || entity.distanceTo(mc.player) > maxDistance.getCurrentValue() * 2.0f
                        || (trackedPlayer != null && (currentTime - trackedPlayer.lastUpdateTime) > 500);
            });
        }
    }

    @EventTarget
    public void onRender2D(EventRender2D event) {
        if (this.isEnabled()) {
            this.render(event.getGuiGraphics());
        }
    }

    @EventTarget
    public void onRender3D(EventRender event) {
        if (mc.player == null || mc.gameRenderer == null || !isInterceptingAirKB) {
            return;
        }

        if (btrendermode.isCurrentMode("ServerPosition")) {
            renderServerPositions(event);
        } else {
            renderTrackedPlayers(event);
        }
    }

    private void renderTrackedPlayers(EventRender event) {
        if (trackedEnemies.isEmpty()) return;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack poseStack = event.getPMatrixStack();
        float width = mc.player.getBbWidth();
        float height = mc.player.getBbHeight();
        AABB playerBoxAtOrigin = new AABB(-width / 2.0, 0, -width / 2.0, width / 2.0, height, width / 2.0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionShader);

        float red = boxRed.getCurrentValue() / 255.0f;
        float green = boxGreen.getCurrentValue() / 255.0f;
        float blue = boxBlue.getCurrentValue() / 255.0f;
        RenderSystem.setShaderColor(red, green, blue, 0.3f);

        for (TrackedPlayer trackedPlayer : trackedEnemies.values()) {
            poseStack.pushPose();
            Vec3 playerPos = trackedPlayer.serverPosition;
            double renderX = playerPos.x() - cameraPos.x();
            double renderY = playerPos.y() - cameraPos.y();
            double renderZ = playerPos.z() - cameraPos.z();
            poseStack.translate(renderX, renderY, renderZ);
            RenderUtils.drawSolidBox(playerBoxAtOrigin, poseStack);
            poseStack.popPose();
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderServerPositions(EventRender event) {
        if (serverPositions.isEmpty()) return;

        PoseStack stack = event.getPMatrixStack();
        stack.pushPose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderUtils.applyRegionalRenderOffset(stack);

        for (Map.Entry<Entity, Vec3> entry : serverPositions.entrySet()) {
            Entity entity = entry.getKey();
            if (entity instanceof Player player) {
                Vec3 serverPos = entry.getValue();
                Vec3 clientPos = entity.position();

                double distance = serverPos.distanceTo(clientPos);
                if (distance > POSITION_THRESHOLD) {
                    AABB bb = player.getBoundingBox().move(-player.getX(), -player.getY(), -player.getZ())
                            .move(serverPos.x, serverPos.y, serverPos.z);

                    RenderSystem.setShaderColor(SERVER_POS_COLOR[0], SERVER_POS_COLOR[1], SERVER_POS_COLOR[2], SERVER_POS_COLOR[3]);
                    RenderUtils.drawSolidBox(bb, stack);
                }
            }
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        stack.popPose();
    }

    private void resetAfterRelease() {
        isInterceptingAirKB = false;
        shouldCheckGround = false;
        if (delayMode.isCurrentMode("Ticks")) {
            delayTickCounter = (int) getRandomDelay();
            log("BackTrack Delay: " + delayTickCounter + " ticks");
        }
        trackedEnemies.clear();
        serverPositions.clear();
    }

    @EventTarget
    public void onPacket(EventPacket event) {
        if (mc.player == null || mc.getConnection() == null || event.getType() != EventType.RECEIVE) {
            return;
        }

        Packet<?> packet = event.getPacket();

        // Handle velocity release
        if (onVelocityRelease.getCurrentValue() && isInterceptingAirKB && packet instanceof ClientboundSetEntityMotionPacket) {
            ClientboundSetEntityMotionPacket motionPacket = (ClientboundSetEntityMotionPacket) packet;
            if (motionPacket.getId() == mc.player.getId()) {
                log("Velocity packet received, releasing queue due to OnVelocityRelease.");
                releaseAllPacketQueue();
                resetAfterRelease();
                return;
            }
        }

        // Handle TP packets
        if (packet instanceof ClientboundPlayerPositionPacket) {
            if (releaseWhenTP.getCurrentValue()) {
                isInterceptingAirKB = false;
                shouldCheckGround = false;
                log("Checked TP, Release All Packets");
                releaseAllPacketQueue();
                resetAfterRelease();
            }
            return;
        }

        // Handle health updates
        if (updateMyselfHealth.getCurrentValue() && packet instanceof ClientboundSetHealthPacket) {
            return;
        }

        // Handle respawn
        if (packet instanceof ClientboundRespawnPacket) {
            clear();
            return;
        }

        // Handle enemy tracking packets
        if (packet instanceof ClientboundTeleportEntityPacket teleportPacket) {
            if (trackedEnemies.containsKey(teleportPacket.getId())) {
                event.setCancelled(true);
                Vec3 newPos = new Vec3(teleportPacket.getX(), teleportPacket.getY(), teleportPacket.getZ());
                trackedEnemies.get(teleportPacket.getId()).updateServerPosition(newPos);

                // 更新服务器位置
                Entity entity = mc.level.getEntity(teleportPacket.getId());
                if (entity != null) {
                    serverPositions.put(entity, newPos);
                }

                if (delayMode.isCurrentMode("Ticks")) {
                    airKBQueue.add(packet);
                } else {
                    timeBasedQueue.add(new PacketSnapshot(packet, System.currentTimeMillis()));
                }
                interceptedPacketCount++;
                return;
            }
        } else if (packet instanceof ClientboundMoveEntityPacket movePacket) {
            Entity entity = movePacket.getEntity(mc.level);
            if (entity != null && trackedEnemies.containsKey(entity.getId())) {
                event.setCancelled(true);
                TrackedPlayer trackedPlayer = trackedEnemies.get(entity.getId());
                Vec3 lastKnownPos = trackedPlayer.serverPosition;
                double newX = lastKnownPos.x + (double)movePacket.getXa() / 4096.0;
                double newY = lastKnownPos.y + (double)movePacket.getYa() / 4096.0;
                double newZ = lastKnownPos.z + (double)movePacket.getZa() / 4096.0;
                Vec3 newPos = new Vec3(newX, newY, newZ);
                trackedPlayer.updateServerPosition(newPos);

                // 更新服务器位置
                serverPositions.put(entity, newPos);

                if (delayMode.isCurrentMode("Ticks")) {
                    airKBQueue.add(packet);
                } else {
                    timeBasedQueue.add(new PacketSnapshot(packet, System.currentTimeMillis()));
                }
                interceptedPacketCount++;
                return;
            }
        }

        // Handle motion packets
        else if (packet instanceof ClientboundSetEntityMotionPacket motionPacket) {
            if (motionPacket.getId() == mc.player.getId()) {
                if (isInterceptingAirKB) {
                    event.setCancelled(true);
                    if (delayMode.isCurrentMode("Ticks")) {
                        airKBQueue.add(packet);
                    } else {
                        timeBasedQueue.add(new PacketSnapshot(packet, System.currentTimeMillis()));
                    }
                    interceptedPacketCount++;
                    knockbackPositions.add(interceptedPacketCount - 1);
                    log("Intercepting KnockBack Packets #" + interceptedPacketCount);
                }
            }
        }

        // Handle other packets based on intercept mode
        else if (isInterceptingAirKB) {
            // Mode: AllExceptSelf -> Skip packets related to self
            if (interceptMode.isCurrentMode("AllExceptSelf")) {
                boolean skipSelf = isSelfRelatedPacket(packet);
                if (skipSelf) {
                    if (debugFilter.getCurrentValue()) log("[AllExceptSelf] Skip Packet Of Myself: " + packet.getClass().getSimpleName());
                    return;
                }
            }

            // Mode: OnlyTarget -> Only intercept packets related to aimed player
            if (interceptMode.isCurrentMode("OnlyTarget")) {
                Player aimed = getAimedPlayer();
                if (aimed == null) {
                    if (debugFilter.getCurrentValue()) log("[OnlyTarget] No aimed player, skip packet: " + packet.getClass().getSimpleName());
                    return;
                }
                int entId = getPacketEntityId(packet);
                if (entId != aimed.getId()) {
                    if (debugFilter.getCurrentValue()) log("[OnlyTarget] Packet not for target (need=" + aimed.getId() + ", got=" + entId + "): " + packet.getClass().getSimpleName());
                    return;
                }
            }

            // Default/Original -> Direct interception
            event.setCancelled(true);
            if (delayMode.isCurrentMode("Ticks")) {
                airKBQueue.add(packet);
            } else {
                timeBasedQueue.add(new PacketSnapshot(packet, System.currentTimeMillis()));
            }
            interceptedPacketCount++;
            if (debugFilter.getCurrentValue()) log("[" + interceptMode.getCurrentMode() + "] Intercepting Normal Packets #" + interceptedPacketCount + ": " + packet.getClass().getSimpleName());
        }
    }

    public void render(GuiGraphics guiGraphics) {
        if (!isInterceptingAirKB && !shouldCheckGround) return;
        trackingText = "Tracking...";

        if (!btrender.getCurrentValue()) return;
        if (mc.player == null || mc.level == null) return;

        if (btrendermode.isCurrentMode("Normal")) {
            renderNormalProgress(guiGraphics);
        }
        else if (btrendermode.isCurrentMode("Senrenbanka")) {
            renderNavenProgress(guiGraphics);
        }
        // ServerPosition模式主要在3D渲染中显示，这里只显示文本
    }

    private void renderNormalProgress(GuiGraphics guiGraphics) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float x = (screenWidth - PROGRESS_BAR_WIDTH) / 2.0f;
        float y = screenHeight / 2.0f + PROGRESS_BAR_Y_OFFSET;
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        float maxPacketValue = Math.max(1.0f, getRandomPacketCount());
        float progress = Math.min(1.0f, interceptedPacketCount / maxPacketValue);
        float progressWidth = PROGRESS_BAR_WIDTH * progress;
        RenderUtils.drawRoundedRect(poseStack, x, y, PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT, CORNER_RADIUS, BACKGROUND_COLOR);
        if (progressWidth > 0) {
            RenderUtils.drawRoundedRect(poseStack, x, y, progressWidth, PROGRESS_BAR_HEIGHT, CORNER_RADIUS, PROGRESS_COLOR);
        }
        if (OnGroundStop.getCurrentValue() && interceptedPacketCount > getRandomPacketCount()) {
            float overflowProgress = (interceptedPacketCount - getRandomPacketCount()) / maxPacketValue;
            float overflowWidth = Math.min(PROGRESS_BAR_WIDTH * overflowProgress, PROGRESS_BAR_WIDTH);
            RenderUtils.drawRoundedRect(poseStack,
                    x + PROGRESS_BAR_WIDTH - overflowWidth,
                    y,
                    overflowWidth,
                    PROGRESS_BAR_HEIGHT,
                    CORNER_RADIUS,
                    OVERFLOW_COLOR);
        }
        float textScale = 0.35f;
        float textWidth = Fonts.harmony.getWidth(trackingText, textScale);
        float textX = (screenWidth - textWidth) / 2.0f;
        float textY = y - 25f;
        Fonts.harmony.render(
                poseStack,
                trackingText,
                (double) textX,
                (double) textY,
                Color.WHITE,
                false,
                textScale
        );
        poseStack.popPose();
    }

    private void renderNavenProgress(GuiGraphics guiGraphics) {
        this.navenProgress.target = Mth.clamp((float) this.getPacketCount() / this.getRandomPacketCount() * 100.0F, 0.0F, 100.0F);
        this.navenProgress.update(true);

        int barX = mc.getWindow().getGuiScaledWidth() / 2 - 50;
        int barY = mc.getWindow().getGuiScaledHeight() / 2 + 15;
        float barWidth = 100.0F;

        float textScale = 0.35f;
        float textWidth = Fonts.harmony.getWidth(trackingText, textScale);
        float textHeight = (float)Fonts.harmony.getHeight(false, textScale);

        float textX = barX + (barWidth - textWidth) / 2.0f;
        float textY = barY - textHeight - 2;
        Fonts.harmony.render(
                guiGraphics.pose(),
                trackingText,
                (double) textX,
                (double) textY,
                Color.WHITE,
                false,
                textScale
        );
        RenderUtils.drawRoundedRect(guiGraphics.pose(), (float)barX, (float)barY, barWidth, 5.0F, 2.0F, Integer.MIN_VALUE);
        RenderUtils.drawRoundedRect(guiGraphics.pose(), (float)barX, (float)barY, this.navenProgress.value, 5.0F, 2.0F, navenMainColor);
    }

    private void clear() {
        releaseAllPacketQueue();
        foundTarget = false;
        boolean working = false;
        serverPositions.clear();
    }

    @EventTarget
    public void onRespawn(EventRespawn e) {
        clear();
        alink = false;
    }
}
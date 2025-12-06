package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import by.radioegor146.nativeobfuscator.JNICObf;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventUpdate;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMoveInput;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;

@JNICObf
@ModuleInfo(
        name = "Velocity",
        description = "Reduces Knock Back.",
        category = Category.COMBAT
)
public class Velocity extends Module {
    // 主模式选择
    final ModeValue mode = ValueBuilder.create(this, "Mode")
            .setDefaultModeIndex(0)
            .setModes("NoXZ", "JumpReset", "Legit", "BufferAbuse", "GrimReduce", "Grim", "Vulcan")
            .build()
            .getModeValue();

    // NoXZ模式设置
    private final ModeValue noXZMode = ValueBuilder.create(this, "NoXZ Mode")
            .setDefaultModeIndex(0)
            .setModes("OneTime", "PerTick")
            .setVisibility(() -> mode.isCurrentMode("NoXZ"))
            .build()
            .getModeValue();

    private final BooleanValue randomAttackCount = ValueBuilder.create(this, "Random AttackCount")
            .setDefaultBooleanValue(false)
            .setVisibility(() -> mode.isCurrentMode("NoXZ"))
            .build()
            .getBooleanValue();

    private final FloatValue maxAttacks = ValueBuilder.create(this, "Max AttackCount")
            .setDefaultFloatValue(5.0F)
            .setMinFloatValue(1.0F)
            .setMaxFloatValue(10.0F)
            .setFloatStep(1.0F)
            .setVisibility(() -> mode.isCurrentMode("NoXZ") && randomAttackCount.getCurrentValue())
            .build()
            .getFloatValue();

    private final FloatValue minAttacks = ValueBuilder.create(this, "Min AttackCount")
            .setDefaultFloatValue(3.0F)
            .setMinFloatValue(1.0F)
            .setMaxFloatValue(10.0F)
            .setFloatStep(1.0F)
            .setVisibility(() -> mode.isCurrentMode("NoXZ") && randomAttackCount.getCurrentValue())
            .build()
            .getFloatValue();

    private final FloatValue attacks = ValueBuilder.create(this, "Attack Count")
            .setDefaultFloatValue(3.0F)
            .setMinFloatValue(1.0F)
            .setMaxFloatValue(5.0F)
            .setFloatStep(1.0F)
            .setVisibility(() -> mode.isCurrentMode("NoXZ") && !randomAttackCount.getCurrentValue())
            .build()
            .getFloatValue();

    // Legit模式设置
    private final FloatValue chance = ValueBuilder.create(this, "Chance")
            .setDefaultFloatValue(100.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(100.0F)
            .setFloatStep(1.0F)
            .setVisibility(() -> mode.isCurrentMode("Legit"))
            .build()
            .getFloatValue();

    private final BooleanValue legitTiming = ValueBuilder.create(this, "Legit Timing")
            .setDefaultBooleanValue(false)
            .setVisibility(() -> mode.isCurrentMode("Legit"))
            .build()
            .getBooleanValue();

    // BufferAbuse模式设置
    private final FloatValue horizontal = ValueBuilder.create(this, "Horizontal")
            .setDefaultFloatValue(100.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(100.0F)
            .setFloatStep(1.0F)
            .setVisibility(() -> mode.isCurrentMode("BufferAbuse"))
            .build()
            .getFloatValue();

    private final FloatValue vertical = ValueBuilder.create(this, "Vertical")
            .setDefaultFloatValue(100.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(100.0F)
            .setFloatStep(1.0F)
            .setVisibility(() -> mode.isCurrentMode("BufferAbuse"))
            .build()
            .getFloatValue();

    private final FloatValue buffer = ValueBuilder.create(this, "Buffer")
            .setDefaultFloatValue(1.0F)
            .setMinFloatValue(1.0F)
            .setMaxFloatValue(3.0F)
            .setFloatStep(1.0F)
            .setVisibility(() -> mode.isCurrentMode("BufferAbuse"))
            .build()
            .getFloatValue();

    // Vulcan模式设置
    private final FloatValue vulcanHorizontal = ValueBuilder.create(this, "Vulcan Horizontal")
            .setDefaultFloatValue(0.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(100.0F)
            .setFloatStep(1.0F)
            .setVisibility(() -> mode.isCurrentMode("Vulcan"))
            .build()
            .getFloatValue();

    private final FloatValue vulcanVertical = ValueBuilder.create(this, "Vulcan Vertical")
            .setDefaultFloatValue(0.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(100.0F)
            .setFloatStep(1.0F)
            .setVisibility(() -> mode.isCurrentMode("Vulcan"))
            .build()
            .getFloatValue();

    // 通用设置
    private final BooleanValue Logging = ValueBuilder.create(this, "Logging")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private Entity targetEntity;
    private boolean velocityInput = false;
    private boolean attacked = false;
    private boolean jump = false;
    private boolean realVelocity = false;
    private boolean grimVelocity = false;

    private double currentKnockbackSpeed = 0.0;
    private int attackQueue = 0;
    private int bufferAmount = 0;
    private boolean receiveDamage = false;

    @Override
    public void onEnable() {
        this.velocityInput = false;
        this.attacked = false;
        this.jump = false;
        this.realVelocity = false;
        this.grimVelocity = false;
        this.targetEntity = null;
        this.currentKnockbackSpeed = 0.0;
        this.attackQueue = 0;
        this.bufferAmount = 0;
        this.receiveDamage = false;
    }

    @Override
    public void onDisable() {
        this.velocityInput = false;
        this.attacked = false;
        this.jump = false;
        this.realVelocity = false;
        this.grimVelocity = false;
        this.targetEntity = null;
        this.currentKnockbackSpeed = 0.0;
        this.attackQueue = 0;
        this.bufferAmount = 0;
        this.receiveDamage = false;
    }

    @EventTarget
    public void onPacket(EventPacket event) {
        if (mc.level == null || mc.player == null) return;

        Packet<?> packet = event.getPacket();

        // 处理伤害事件
        if (packet instanceof ClientboundDamageEventPacket) {
            ClientboundDamageEventPacket damagePacket = (ClientboundDamageEventPacket)packet;
            if (damagePacket.entityId() == mc.player.getId()) {
                this.receiveDamage = true;

                // Grim模式处理
                if (mode.isCurrentMode("Grim")) {
                    this.realVelocity = true;
                }
            }
        }

        // 处理速度包
        if (packet instanceof ClientboundSetEntityMotionPacket) {
            ClientboundSetEntityMotionPacket velocityPacket = (ClientboundSetEntityMotionPacket)packet;
            if (velocityPacket.getId() != mc.player.getId()) {
                return;
            }

            // 获取速度向量 - 使用反射或其他方法获取私有字段
            Vec3 motion = getMotionFromPacket(velocityPacket);
            if (motion == null) return;

            double motionX = motion.x;
            double motionY = motion.y;
            double motionZ = motion.z;

            this.velocityInput = true;
            // 注意：这里需要检查 Aura.target 是否可用，或者使用其他方式获取目标实体
            // this.targetEntity = Aura.target;

            // NoXZ模式处理
            if (this.mode.isCurrentMode("NoXZ")) {
                if (this.receiveDamage) {
                    this.receiveDamage = false;
                    this.attackQueue = getAttackCount();

                    if (this.Logging.getCurrentValue()) {
                        ChatUtils.addChatMessage("NoXZ Queue set: " + this.attackQueue + " attacks");
                    }
                }
            }

            // Legit模式处理
            if (this.mode.isCurrentMode("Legit")) {
                if (!mc.player.onGround()) {
                    return;
                }

                if (motionY > 0 && (!legitTiming.getCurrentValue() || mc.player.hurtTime <= 14 || mc.player.onGround())) {
                    this.jump = true;
                }
            }

            // BufferAbuse模式处理
            if (this.mode.isCurrentMode("BufferAbuse")) {
                if (bufferAmount < (int) buffer.getCurrentValue()) {
                    event.setCancelled(true);
                    bufferAmount++;
                    return;
                }

                double horizontalValue = horizontal.getCurrentValue() / 100.0;
                double verticalValue = vertical.getCurrentValue() / 100.0;

                Vec3 newMotion = new Vec3(
                        motionX * horizontalValue,
                        motionY * verticalValue,
                        motionZ * horizontalValue
                );

                event.setCancelled(true);
                mc.player.setDeltaMovement(newMotion);
                bufferAmount = 0;
            }

            // Vulcan模式处理
            if (this.mode.isCurrentMode("Vulcan")) {
                double horizontalValue = vulcanHorizontal.getCurrentValue() / 100.0;
                double verticalValue = vulcanVertical.getCurrentValue() / 100.0;

                if (horizontalValue == 0 && verticalValue == 0) {
                    event.setCancelled(true);
                    return;
                }

                Vec3 newMotion = new Vec3(
                        motionX * horizontalValue,
                        motionY * verticalValue,
                        motionZ * horizontalValue
                );

                event.setCancelled(true);
                mc.player.setDeltaMovement(newMotion);
            }

            // Grim模式处理
            if (this.mode.isCurrentMode("Grim") && realVelocity) {
                event.setCancelled(true);
                realVelocity = false;
                grimVelocity = true;
            }
        }

        // 处理爆炸包
        if (packet instanceof ClientboundExplodePacket) {
            ClientboundExplodePacket explosionPacket = (ClientboundExplodePacket)packet;

            // BufferAbuse模式处理爆炸
            if (this.mode.isCurrentMode("BufferAbuse")) {
                if (bufferAmount < (int) buffer.getCurrentValue()) {
                    event.setCancelled(true);
                    bufferAmount++;
                    return;
                }

                // 爆炸包的处理需要更复杂的逻辑，这里简化处理
                bufferAmount = 0;
            }

            // Vulcan模式处理爆炸
            if (this.mode.isCurrentMode("Vulcan")) {
                double horizontalValue = vulcanHorizontal.getCurrentValue() / 100.0;
                double verticalValue = vulcanVertical.getCurrentValue() / 100.0;

                if (horizontalValue == 0 && verticalValue == 0) {
                    event.setCancelled(true);
                    return;
                }

                // 爆炸包的处理需要更复杂的逻辑，这里简化处理
            }
        }
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (mc.player == null) return;

        // 重置状态
        if (mc.player.hurtTime == 0) {
            this.velocityInput = false;
            this.currentKnockbackSpeed = 0.0;
            this.jump = false;
        }

        // JumpReset模式
        if (this.mode.isCurrentMode("JumpReset")) {
            if (this.velocityInput && mc.player.onGround()) {
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.42, mc.player.getDeltaMovement().z);
                this.velocityInput = false;
            }
        }

        // NoXZ模式
        if (this.mode.isCurrentMode("NoXZ") && this.targetEntity != null && this.attackQueue > 0) {
            if (this.noXZMode.isCurrentMode("OneTime")) {
                for (; this.attackQueue >= 1; this.attackQueue--) {
                    mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(this.targetEntity, false));
                    mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(0.6, 1, 0.6));
                    mc.player.setSprinting(false);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
                if (this.Logging.getCurrentValue()) {
                    ChatUtils.addChatMessage("高雅人士观察中");
                }
            } else if (this.noXZMode.isCurrentMode("PerTick")) {
                if (this.attackQueue >= 1) {
                    mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(this.targetEntity, false));
                    mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(0.6, 1, 0.6));
                    mc.player.setSprinting(false);
                    mc.player.swing(InteractionHand.MAIN_HAND);

                    if (this.Logging.getCurrentValue()) {
                        ChatUtils.addChatMessage("NoXZ PerTick attack executed, remaining: " + (this.attackQueue - 1));
                    }
                }
                this.attackQueue--;
            }
        }

        // GrimReduce模式
        if (this.mode.isCurrentMode("GrimReduce")) {
            if (mc.player.hurtTime <= 14 && mc.player.hurtTime > 0) {
                // 这里需要实现攻击逻辑
                // 实际实现中可能需要目标选择组件
            }
        }

        // Grim模式
        if (this.mode.isCurrentMode("Grim") && grimVelocity) {
            // 发送停止破坏方块包
            // 实际实现中需要发送对应的包
            grimVelocity = false;
        }
    }

    @EventTarget
    public void onMoveInput(EventMoveInput event) {
        if (mc.player == null) return;

        // Legit模式处理移动输入
        if (this.mode.isCurrentMode("Legit")) {
            if (jump && isMoving() && Math.random() * 100 < chance.getCurrentValue()) {
                event.setJump(true);
            }
        }
    }

    private int getAttackCount() {
        if (randomAttackCount.getCurrentValue()) {
            int min = (int) minAttacks.getCurrentValue();
            int max = (int) maxAttacks.getCurrentValue();
            if (min > max) {
                int temp = min;
                min = max;
                max = temp;
            }
            if (min == max) {
                return min;
            }
            return new java.util.Random().nextInt(max - min + 1) + min;
        } else {
            return (int) attacks.getCurrentValue();
        }
    }

    private boolean isMoving() {
        return mc.player != null && (mc.player.xxa != 0 || mc.player.zza != 0);
    }

    // 使用反射获取 ClientboundSetEntityMotionPacket 中的运动向量
    private Vec3 getMotionFromPacket(ClientboundSetEntityMotionPacket packet) {
        try {
            // 尝试使用反射获取私有字段
            java.lang.reflect.Field xaField = ClientboundSetEntityMotionPacket.class.getDeclaredField("xa");
            java.lang.reflect.Field yaField = ClientboundSetEntityMotionPacket.class.getDeclaredField("ya");
            java.lang.reflect.Field zaField = ClientboundSetEntityMotionPacket.class.getDeclaredField("za");

            xaField.setAccessible(true);
            yaField.setAccessible(true);
            zaField.setAccessible(true);

            int xa = xaField.getInt(packet);
            int ya = yaField.getInt(packet);
            int za = zaField.getInt(packet);

            // 将固定点数值转换为实际的速度值
            return new Vec3(xa / 8000.0, ya / 8000.0, za / 8000.0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
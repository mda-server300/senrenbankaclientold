package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.Senrenbanka;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.PacketUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.TimeHelper;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.Random;
import java.util.stream.StreamSupport;

@ModuleInfo(
        name = "AutoThrowable",
        description = "Automatically throws throwable items like snowballs or eggs at targets.",
        category = Category.COMBAT
)
public class AutoThrowable extends Module {
    private static final Random random = new Random();
    private final Minecraft mc = Minecraft.getInstance();

    // 配置项 - 时间随机化参数
    private final BooleanValue onlyWhenCombat = ValueBuilder.create(this, "Only Combat")
            .setDefaultBooleanValue(true)
            .build().getBooleanValue();

    private final BooleanValue legitMode = ValueBuilder.create(this, "Legit")
            .setDefaultBooleanValue(false)
            .build().getBooleanValue();

    private final FloatValue prepareTimeMin = ValueBuilder.create(this, "Ready Time Min(ms)")
            .setDefaultFloatValue(100.0F)
            .setFloatStep(50.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(1000.0F)
            .build().getFloatValue();

    private final FloatValue prepareTimeMax = ValueBuilder.create(this, "Ready TimeMax(ms)")
            .setDefaultFloatValue(300.0F)
            .setFloatStep(50.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(1000.0F)
            .build().getFloatValue();

    private final FloatValue throwWaitTimeMin = ValueBuilder.create(this, "Throw Delay Min(ms)")
            .setDefaultFloatValue(50.0F)
            .setFloatStep(20.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(500.0F)
            .build().getFloatValue();

    private final FloatValue throwWaitTimeMax = ValueBuilder.create(this, "Throw Delay Max(ms)")
            .setDefaultFloatValue(200.0F)
            .setFloatStep(20.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(500.0F)
            .build().getFloatValue();

    private final FloatValue switchBackTimeMin = ValueBuilder.create(this, "Switch Back TimeMin(ms)")
            .setDefaultFloatValue(300.0F)
            .setFloatStep(50.0F)
            .setMinFloatValue(100.0F)
            .setMaxFloatValue(2000.0F)
            .build().getFloatValue();

    private final FloatValue switchBackTimeMax = ValueBuilder.create(this, "Switch Backt Time Max(ms)")
            .setDefaultFloatValue(700.0F)
            .setFloatStep(50.0F)
            .setMinFloatValue(100.0F)
            .setMaxFloatValue(2000.0F)
            .build().getFloatValue();

    private final FloatValue cooldownTimeMin = ValueBuilder.create(this, "Delay Min(ms)")
            .setDefaultFloatValue(800.0F)
            .setFloatStep(100.0F)
            .setMinFloatValue(500.0F)
            .setMaxFloatValue(5000.0F)
            .build().getFloatValue();

    private final FloatValue cooldownTimeMax = ValueBuilder.create(this, "Delay Max(ms)")
            .setDefaultFloatValue(1500.0F)
            .setFloatStep(100.0F)
            .setMinFloatValue(500.0F)
            .setMaxFloatValue(5000.0F)
            .build().getFloatValue();

    // 状态变量
    private final TimeHelper timer = new TimeHelper();
    private final TimeHelper cooldownTimer = new TimeHelper();
    private int originalSlot = -1;
    private int throwableSlot = -1;
    private State state = State.IDLE;
    private Entity target = null;
    private long currentStageTime; // 当前阶段的随机时间

    // 新增检查状态，完善状态机
    private enum State {
        IDLE,               // 闲置
        PREPARING,          // 准备阶段
        SWITCHING_TO,       // 切换到弹射物
        CHECKING_THROWABLE, // 验证是否切换成功
        SWITCHED,           // 已切换（等待扔出）
        THROWING,           // 扔出弹射物
        SWITCHING_BACK,     // 切回原物品
        CHECKING_BACK,      // 验证是否切回成功
        COOLDOWN            // 冷却中
    }

    @Override
    public void onEnable() {
        state = State.IDLE;
        originalSlot = -1;
        throwableSlot = -1;
        target = null;
        timer.reset();
        cooldownTimer.reset();
        currentStageTime = 0;
        super.onEnable();
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        LocalPlayer player = mc.player;
        // 界面打开时不执行操作
        if (event.getType() != EventType.PRE || player == null || mc.level == null || mc.screen != null) {
            return;
        }

        // 检查冷却
        if (!cooldownTimer.delay(currentStageTime) && state == State.COOLDOWN) {
            return;
        }

        // 检查战斗模块是否激活
        if (onlyWhenCombat.getCurrentValue() && !isCombatModuleActive()) {
            resetState();
            return;
        }

        // 获取目标
        target = getValidTarget();
        if (target == null) {
            resetState();
            return;
        }

        // 处理投掷逻辑
        handleThrowing();
    }

    private void handleThrowing() {
        LocalPlayer player = mc.player;
        if (player == null) return;

        switch (state) {
            case IDLE:
                // 查找弹射物槽位（主手/副手）
                throwableSlot = findThrowableSlot();
                if (throwableSlot == -1 && !isThrowable(player.getOffhandItem())) {
                    resetState();
                    break;
                }

                originalSlot = player.getInventory().selected;
                // 生成准备时间
                currentStageTime = getRandomTime(prepareTimeMin.getCurrentValue(), prepareTimeMax.getCurrentValue());
                timer.reset();
                state = State.PREPARING;
                break;

            case PREPARING:
                if (timer.delay(currentStageTime)) {
                    // 副手有弹射物直接进入等待扔出阶段
                    if (isThrowable(player.getOffhandItem())) {
                        currentStageTime = getRandomTime(throwWaitTimeMin.getCurrentValue(), throwWaitTimeMax.getCurrentValue());
                        timer.reset();
                        state = State.SWITCHED;
                    } else if (throwableSlot != -1 && throwableSlot != originalSlot) {
                        // 切换到弹射物
                        switchToThrowable(player);
                        state = State.SWITCHING_TO;
                        timer.reset();
                        currentStageTime = 50; // 等待切换生效的短延迟
                    } else {
                        resetState();
                    }
                }
                break;

            case SWITCHING_TO:
                if (timer.delay(currentStageTime)) {
                    state = State.CHECKING_THROWABLE;
                }
                break;

            case CHECKING_THROWABLE:
                // 验证是否成功切换到投掷物
                if (isHoldingThrowable(player)) {
                    currentStageTime = getRandomTime(throwWaitTimeMin.getCurrentValue(), throwWaitTimeMax.getCurrentValue());
                    timer.reset();
                    state = State.SWITCHED;
                } else {
                    // 切换失败重试
                    switchToThrowable(player);
                    timer.reset();
                    currentStageTime = 50;
                    state = State.SWITCHING_TO;
                }
                break;

            case SWITCHED:
                if (timer.delay(currentStageTime)) {
                    // 扔出弹射物
                    InteractionHand hand = isThrowable(player.getOffhandItem()) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                    if (legitMode.getCurrentValue()) {
                        simulateRightClick();
                    } else {
                        PacketUtils.sendSequencedPacket(id -> new ServerboundUseItemPacket(hand, id));
                        player.swing(hand);
                    }
                    state = State.THROWING;
                    timer.reset();
                    currentStageTime = 50; // 等待投掷动画
                }
                break;

            case THROWING:
                if (timer.delay(currentStageTime)) {
                    if (originalSlot != -1 && originalSlot != player.getInventory().selected && !isThrowable(player.getOffhandItem())) {
                        currentStageTime = getRandomTime(switchBackTimeMin.getCurrentValue(), switchBackTimeMax.getCurrentValue());
                        timer.reset();
                        state = State.SWITCHING_BACK;
                    } else {
                        finishThrow();
                    }
                }
                break;

            case SWITCHING_BACK:
                if (timer.delay(currentStageTime) && originalSlot != -1) {
                    switchToOriginal(player);
                    timer.reset();
                    currentStageTime = 50; // 等待切回生效
                    state = State.CHECKING_BACK;
                }
                break;

            case CHECKING_BACK:
                if (player.getInventory().selected == originalSlot) {
                    finishThrow();
                } else {
                    // 切回失败重试
                    switchToOriginal(player);
                    timer.reset();
                    currentStageTime = 50;
                    state = State.SWITCHING_BACK;
                }
                break;

            case COOLDOWN:
                state = State.IDLE;
                break;
        }
    }

    private void switchToThrowable(LocalPlayer player) {
        if (legitMode.getCurrentValue()) {
            simulateKeyPress(getSlotKey(throwableSlot));
        } else {
            player.getInventory().selected = throwableSlot;
        }
    }

    private void switchToOriginal(LocalPlayer player) {
        if (legitMode.getCurrentValue()) {
            simulateKeyPress(getSlotKey(originalSlot));
        } else {
            player.getInventory().selected = originalSlot;
        }
    }

    private void finishThrow() {
        currentStageTime = getRandomTime(cooldownTimeMin.getCurrentValue(), cooldownTimeMax.getCurrentValue());
        cooldownTimer.reset();
        state = State.COOLDOWN;
    }

    private void resetState() {
        state = State.IDLE;
        originalSlot = -1;
        throwableSlot = -1;
        target = null;
        timer.reset();
    }

    private long getRandomTime(float min, float max) {
        if (min >= max) return (long) min;
        return (long) (min + random.nextFloat() * (max - min));
    }

    private int getSlotKey(int slot) {
        return GLFW.GLFW_KEY_1 + slot; // 0→1, 1→2...正确映射
    }

    // 非阻塞按键模拟，使用mc.execute()避免主线程阻塞
    private void simulateKeyPress(int keyCode) {
        // 获取对应快捷栏的KeyMapping（0-8对应KEY_1-KEY_9）
        KeyMapping key = mc.options.keyHotbarSlots[keyCode - GLFW.GLFW_KEY_1];
        if (key == null) return;

        // 获取按键对应的InputConstants.Key（用于静态方法调用）
        InputConstants.Key inputKey = key.getKey();
        // 生成随机延迟（10-30ms）模拟人类按键间隔
        int pressDelay = random.nextInt(20) + 10;

        // 使用mc.tell()提交到主线程执行，避免阻塞
        mc.tell(() -> {
            try {
                // 按下按键：通过KeyMapping静态方法set触发全局状态更新
                KeyMapping.set(inputKey, true);
                KeyMapping.click(inputKey); // 模拟点击事件

                // 非阻塞延迟（使用循环+yield避免线程阻塞）
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < pressDelay) {
                    Thread.yield(); // 让出CPU时间片，不阻塞主线程
                }

                // 释放按键
                KeyMapping.set(inputKey, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    // 非阻塞右键模拟
    private void simulateRightClick() {
        KeyMapping useKey = mc.options.keyUse;
        InputConstants.Key inputKey = useKey.getKey(); // 获取对应InputConstants.Key
        // 生成随机右键按下延迟（20-50ms）
        int clickDelay = random.nextInt(30) + 20;

        // 提交到主线程执行
        mc.tell(() -> {
            try {
                // 按下右键
                KeyMapping.set(inputKey, true);
                KeyMapping.click(inputKey); // 触发点击事件

                // 非阻塞延迟
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < clickDelay) {
                    Thread.yield();
                }

                // 释放右键
                KeyMapping.set(inputKey, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private boolean isCombatModuleActive() {
        Module killaura = Senrenbanka.getInstance().getModuleManager().getModule(Aura.class);
        Module aimAssist = Senrenbanka.getInstance().getModuleManager().getModule(AimAssist.class);
        return (killaura != null && killaura.isEnabled()) || (aimAssist != null && aimAssist.isEnabled());
    }

    private Entity getValidTarget() {
        // 优先从AimAssist获取目标
        Module aimAssistModule = Senrenbanka.getInstance().getModuleManager().getModule(AimAssist.class);
        if (aimAssistModule instanceof AimAssist aimAssist && aimAssist.isEnabled()) {
            // 重新实现目标选择逻辑，而不是调用私有方法
            Entity aimTarget = getAimAssistTarget(aimAssist);
            if (aimTarget != null && isValidTarget(aimTarget)) {
                return aimTarget;
            }
        }

        // 检查Aura目标
        Module killaura =Senrenbanka.getInstance().getModuleManager().getModule(Aura.class);
        if (killaura != null && killaura.isEnabled() && Aura.targets != null && !Aura.targets.isEmpty()) {
            for (Entity entity : Aura.targets) {
                if (isValidTarget(entity)) {
                    return entity;
                }
            }
        }

        return null;
    }

    // 重新实现AimAssist的目标选择逻辑
    private Entity getAimAssistTarget(AimAssist aimAssist) {
        if (mc.level == null || mc.player == null) return null;
        
        return StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity instanceof Entity)
                .filter(aimAssist::isValidAttack)
                .findFirst()
                .orElse(null);
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive()) {
            return false;
        }
        double distance = entity.distanceTo(mc.player);
        // 修复方法名，将inFoV改为inFov
        return distance <= 10.0 && RotationUtils.inFov(entity, 90.0F);
    }

    private int findThrowableSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isThrowable(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isThrowable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.SNOWBALL || item == Items.EGG;
    }

    // 验证是否手持投掷物
    private boolean isHoldingThrowable(LocalPlayer player) {
        return isThrowable(player.getMainHandItem()) || isThrowable(player.getOffhandItem());
    }
}
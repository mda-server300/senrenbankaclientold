package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import by.radioegor146.nativeobfuscator.JNICObf;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventUpdate;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

@JNICObf
@ModuleInfo(
        name = "Eagle",
        description = "Automatically shift when looking down at edges",
        category = Category.MOVEMENT
)
public class Eagle extends Module {

    // 配置选项 - 使用与 NoSlow 相同的简洁风格
    private final BooleanValue enable = ValueBuilder.create(this, "Enable")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private final BooleanValue onlyWhenStill = ValueBuilder.create(this, "Only When Still")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private final BooleanValue strictAngle = ValueBuilder.create(this, "Strict Angle")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private boolean eagleControllingShift = false;

    @EventTarget
    public void onUpdate(EventUpdate event) {
        // 基础检查
        if (mc.level == null || mc.player == null || !enable.getCurrentValue()) {
            if (eagleControllingShift) {
                mc.options.keyShift.setDown(false);
                eagleControllingShift = false;
            }
            return;
        }

        // 检查角度条件（严格模式使用70度，非严格模式使用60度）
        float angleThreshold = strictAngle.getCurrentValue() ? 70.0f : 60.0f;

        // 检查移动条件
        boolean shouldCheck = !onlyWhenStill.getCurrentValue() || !mc.player.input.up;

        if (mc.player.getXRot() > angleThreshold && shouldCheck) {
            BlockPos posUnder = BlockPos.containing(
                    mc.player.getX(),
                    mc.player.getY() - 1,
                    mc.player.getZ()
            );
            BlockState stateUnder = mc.level.getBlockState(posUnder);
            boolean unsafe = stateUnder.isAir() || stateUnder.canBeReplaced();

            if (unsafe) {
                mc.options.keyShift.setDown(true);
                eagleControllingShift = true;
                return;
            }
        }

        if (eagleControllingShift) {
            mc.options.keyShift.setDown(false);
            eagleControllingShift = false;
        }
    }

    @Override
    public void onDisable() {
        // 禁用模块时释放潜行键
        if (eagleControllingShift) {
            mc.options.keyShift.setDown(false);
            eagleControllingShift = false;
        }
    }
}
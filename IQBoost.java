package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;

@ModuleInfo(
        name = "IQBoost",
        description = "Improve your IQ.",
        category = Category.MISC
)
public class IQBoost extends Module {
    public FloatValue iq = ValueBuilder.create(this, "IQ")
            .setDefaultFloatValue(114514.0F)
            .setFloatStep(1.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(114514.0F)
            .build()
            .getFloatValue();

    @EventTarget
    public void onTick(EventRunTicks event) {
        if (mc.player == null || mc.level == null || event.getType() != EventType.PRE) return;
        this.setSuffix(iq.getCurrentValue() + "");
    }
}

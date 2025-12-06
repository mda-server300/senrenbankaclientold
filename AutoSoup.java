package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import by.radioegor146.nativeobfuscator.JNICObf;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

@JNICObf
@ModuleInfo(
        name = "AutoSoup",
        description = "Automatically eat soup when health is low",
        category = Category.COMBAT
)
public class AutoSoup extends Module {
    private final Minecraft mc = Minecraft.getInstance();
    private boolean eating = false;
    private int eatDelay = 0;
    public AutoSoup() {
        super("AutoSoup");
        Category category = Category.COMBAT;
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                TickEvent.ClientTickEvent.class,
                this::onClientTick
        );
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }
    public void onTick() {
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (eating) {
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty() || stack.getItem() != Items.MUSHROOM_STEW) {
                mc.options.keyUse.setDown(false);
                player.getInventory().selected = 0;
                eating = false;
            }
            return;
        }
        /*if (eatDelay > 0) {
            eatDelay--;
            return;
        }*/
        if (player.getHealth() < 14f) {
            int hotbarIndex = findHotbarMushroomStew(player);
            if (hotbarIndex != -1) {
                if (player.getInventory().selected != hotbarIndex) {
                    player.getInventory().selected = hotbarIndex;
                    eatDelay = 2;
                } else {
                    mc.options.keyUse.setDown(true);
                    eating = true;
                }
            }
        }
    }

    private int findHotbarMushroomStew(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.MUSHROOM_STEW) return i;
        }
        return -1;
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            onTick();
        }
    }
}
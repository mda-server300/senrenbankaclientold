package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import by.radioegor146.nativeobfuscator.JNICObf;
import com.heypixel.heypixelmod.obsoverlay.Senrenbanka;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.*;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.KillSay;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.Teams;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.Blink;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.Stuck;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.render.HUD;
import com.heypixel.heypixelmod.obsoverlay.utils.*;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationManager;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@ModuleInfo(
        name = "KillAura",
        description = "Automatically attacks entities",
        category = Category.COMBAT
)
@JNICObf
public class Aura extends Module {
    private static final float[] targetColorRed = new float[]{0.78431374F, 0.0F, 0.0F, 0.23529412F};
    private static final float[] targetColorGreen = new float[]{0.0F, 0.78431374F, 0.0F, 0.23529412F};
    public static Entity target;
    public static Entity aimingTarget;
    public static List<Entity> targets = new ArrayList<>();
    public static Vector2f rotation;

    private static final ResourceLocation NITRO_TEXTURE = new ResourceLocation("heypixel", "target/target1.png");
    private static final ResourceLocation COLORFUL_TEXTURE = new ResourceLocation("heypixel", "target/target2.png");

    private float rotationAngle = 0.0F;
    private float currentRotationSpeed = 3.0F;
    private float targetRotationSpeed = 3.0F;
    private int rotationSpeedTickCounter = 0;
    private int rotationDirectionTickCounter = 0;
    private int rotationDirection = 1;
    private float currentSizeMultiplier = 1.0F;
    private float targetSizeMultiplier = 1.0F;
    private int sizeTickCounter = 0;
    private final Random random = new Random();

    BooleanValue targetHud = ValueBuilder.create(this, "Target HUD").setDefaultBooleanValue(true).build().getBooleanValue();
    ModeValue targetHudMode = ValueBuilder.create(this, "HUD Mode")
            .setModes("New", "old")
            .setDefaultModeIndex(0)
            .build()
            .getModeValue();

    ModeValue targetEsp = ValueBuilder.create(this, "Target ESP")
            .setModes("Off", "Senrenbanka", "Nitro", "Colorful")
            .setDefaultModeIndex(1)
            .build()
            .getModeValue();

    BooleanValue attackPlayer = ValueBuilder.create(this, "Attack Player").setDefaultBooleanValue(true).build().getBooleanValue();
    BooleanValue attackInvisible = ValueBuilder.create(this, "Attack Invisible").setDefaultBooleanValue(false).build().getBooleanValue();
    BooleanValue attackAnimals = ValueBuilder.create(this, "Attack Animals").setDefaultBooleanValue(false).build().getBooleanValue();
    BooleanValue attackMobs = ValueBuilder.create(this, "Attack Mobs").setDefaultBooleanValue(false).build().getBooleanValue();
    BooleanValue multi = ValueBuilder.create(this, "Multi Attack").setDefaultBooleanValue(false).build().getBooleanValue();
    BooleanValue infSwitch = ValueBuilder.create(this, "Infinity Switch").setDefaultBooleanValue(false).build().getBooleanValue();
    BooleanValue preferBaby = ValueBuilder.create(this, "Prefer Baby").setDefaultBooleanValue(false).build().getBooleanValue();
    BooleanValue moreParticles = ValueBuilder.create(this, "More Particles").setDefaultBooleanValue(false).build().getBooleanValue();
    FloatValue aimRange = ValueBuilder.create(this, "Aim Range")
            .setDefaultFloatValue(5.0F)
            .setFloatStep(0.1F)
            .setMinFloatValue(1.0F)
            .setMaxFloatValue(6.0F)
            .build()
            .getFloatValue();
    FloatValue aps = ValueBuilder.create(this, "Attack Per Second")
            .setDefaultFloatValue(10.0F)
            .setFloatStep(1.0F)
            .setMinFloatValue(1.0F)
            .setMaxFloatValue(20.0F)
            .build()
            .getFloatValue();
    FloatValue switchSize = ValueBuilder.create(this, "Switch Size")
            .setDefaultFloatValue(1.0F)
            .setFloatStep(1.0F)
            .setMinFloatValue(1.0F)
            .setMaxFloatValue(5.0F)
            .setVisibility(() -> !this.infSwitch.getCurrentValue())
            .build()
            .getFloatValue();
    FloatValue switchAttackTimes = ValueBuilder.create(this, "Switch Delay (Attack Times)")
            .setDefaultFloatValue(1.0F)
            .setFloatStep(1.0F)
            .setMinFloatValue(1.0F)
            .setMaxFloatValue(10.0F)
            .build()
            .getFloatValue();
    FloatValue fov = ValueBuilder.create(this, "FoV")
            .setDefaultFloatValue(360.0F)
            .setFloatStep(1.0F)
            .setMinFloatValue(10.0F)
            .setMaxFloatValue(360.0F)
            .build()
            .getFloatValue();
    FloatValue hurtTime = ValueBuilder.create(this, "Hurt Time")
            .setDefaultFloatValue(10.0F)
            .setFloatStep(1.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(10.0F)
            .build()
            .getFloatValue();
    ModeValue priority = ValueBuilder.create(this, "Priority").setModes("Health", "FoV", "Range", "None").build().getModeValue();

    BooleanValue autoBlock = ValueBuilder.create(this, "AutoBlock").setDefaultBooleanValue(false).build().getBooleanValue();

    private boolean blockVisual = false;
    private int blockingTicks = 0;

    RotationUtils.Data lastRotationData;
    RotationUtils.Data rotationData;
    int attackTimes = 0;
    float attacks = 0.0F;
    private int index;
    private Vector4f blurMatrix;
    private int interactTicks = 0;
    private int getTicks = 0;

    private Entity previousTarget;
    private boolean animationRunning;
    private long animationStartTime;
    private int animationDirection;
    private float animationProgress;
    private Map<UUID, Float> previousHealthMap = new WeakHashMap<>();

    public static Entity getTarget() {
        return null;
    }

    private void renderEntityHead(PoseStack stack, LivingEntity entity, float x, float y, float width, float height) {
        if (entity instanceof Player) {
            renderPlayerHead(stack, (Player) entity, x, y, width, height);
        } else {
            renderMobHead(stack, entity, x, y, width, height);
        }
    }

    private void renderPlayerHead(PoseStack stack, Player player, float x, float y, float width, float height) {
        try {
            ResourceLocation skin = mc.getSkinManager().getInsecureSkinLocation(player.getGameProfile());

            RenderSystem.setShaderTexture(0, skin);

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            float u1 = 8.0F / 64.0F;
            float v1 = 8.0F / 64.0F;
            float u2 = 16.0F / 64.0F;
            float v2 = 16.0F / 64.0F;

            RenderUtils.drawRoundedRect(stack, x - 1, y - 1, width + 2, height + 2, 4.0F, new Color(50, 50, 50, 200).getRGB());

            drawTexturedRect(stack, x, y, width, height, u1, v1, u2, v2);

        } catch (Exception e) {
            ResourceLocation defaultSkin = DefaultPlayerSkin.getDefaultSkin();
            RenderSystem.setShaderTexture(0, defaultSkin);

            float u1 = 8.0F / 64.0F;
            float v1 = 8.0F / 64.0F;
            float u2 = 16.0F / 64.0F;
            float v2 = 16.0F / 64.0F;

            drawTexturedRect(stack, x, y, width, height, u1, v1, u2, v2);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderMobHead(PoseStack stack, LivingEntity entity, float x, float y, float width, float height) {
        RenderUtils.drawRoundedRect(stack, x - 1, y - 1, width + 2, height + 2, 4.0F, new Color(50, 50, 50, 200).getRGB());

        Color mobColor = getMobColor(entity);
        RenderUtils.drawRoundedRect(stack, x, y, width, height, 3.0F, mobColor.getRGB());

        String mobSymbol = getMobSymbol(entity);
        float textX = x + width / 2 - Fonts.harmony.getWidth(mobSymbol, 0.3F) / 2;
        float textY = y + height / 2 - 4;
        Fonts.harmony.render(stack, mobSymbol, (double)textX, (double)textY, Color.WHITE, true, 0.3F);
    }

    private Color getMobColor(LivingEntity entity) {
        String entityName = entity.getType().toString().toLowerCase();

        if (entityName.contains("zombie")) {
            return new Color(0, 100, 0);
        } else if (entityName.contains("skeleton")) {
            return new Color(200, 200, 200);
        } else if (entityName.contains("creeper")) {
            return new Color(0, 200, 0);
        } else if (entityName.contains("spider")) {
            return new Color(50, 50, 50);
        } else if (entityName.contains("enderman")) {
            return new Color(50, 0, 50);
        } else if (entityName.contains("cow")) {
            return new Color(150, 100, 50);
        } else if (entityName.contains("pig")) {
            return new Color(255, 150, 150);
        } else if (entityName.contains("chicken")) {
            return new Color(255, 255, 200);
        } else if (entityName.contains("sheep")) {
            return new Color(255, 255, 255);
        } else if (entityName.contains("wolf")) {
            return new Color(150, 150, 150);
        } else if (entityName.contains("blaze")) {
            return new Color(255, 200, 0);
        } else if (entityName.contains("ghast")) {
            return new Color(255, 255, 255);
        } else if (entityName.contains("magma") || entityName.contains("slime")) {
            return new Color(255, 100, 0);
        } else if (entityName.contains("guardian")) {
            return new Color(100, 150, 200);
        } else if (entityName.contains("villager")) {
            return new Color(200, 150, 100);
        } else {
            return new Color(100, 100, 100);
        }
    }

    private String getMobSymbol(LivingEntity entity) {
        String entityName = entity.getType().toString().toLowerCase();

        if (entityName.contains("zombie")) return "Z";
        if (entityName.contains("skeleton")) return "S";
        if (entityName.contains("creeper")) return "C";
        if (entityName.contains("spider")) return "S";
        if (entityName.contains("enderman")) return "E";
        if (entityName.contains("cow")) return "C";
        if (entityName.contains("pig")) return "P";
        if (entityName.contains("chicken")) return "C";
        if (entityName.contains("sheep")) return "S";
        if (entityName.contains("wolf")) return "W";
        if (entityName.contains("blaze")) return "B";
        if (entityName.contains("ghast")) return "G";
        if (entityName.contains("magma")) return "M";
        if (entityName.contains("slime")) return "S";
        if (entityName.contains("guardian")) return "G";
        if (entityName.contains("villager")) return "V";
        return "M";
    }

    private void drawTexturedRect(PoseStack stack, float x, float y, float width, float height, float u1, float v1, float u2, float v2) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(stack.last().pose(), x, y + height, 0).uv(u1, v2).endVertex();
        buffer.vertex(stack.last().pose(), x + width, y + height, 0).uv(u2, v2).endVertex();
        buffer.vertex(stack.last().pose(), x + width, y, 0).uv(u2, v1).endVertex();
        buffer.vertex(stack.last().pose(), x, y, 0).uv(u1, v1).endVertex();

        tessellator.end();
    }

    @EventTarget
    public void onShader(EventShader e) {
        if (this.blurMatrix != null && this.targetHud.getCurrentValue()) {
            RenderUtils.drawRoundedRect(e.getStack(), this.blurMatrix.x(), this.blurMatrix.y(), this.blurMatrix.z(), this.blurMatrix.w(), 3.0F, 1073741824);
        }
    }

    @EventTarget
    public void onRender(EventRender2D e) {
        this.blurMatrix = null;
        if (target instanceof LivingEntity && this.targetHud.getCurrentValue()) {
            LivingEntity living = (LivingEntity)target;

            handleTargetSwitchAnimation(living);

            if (animationRunning && previousTarget instanceof LivingEntity) {
                renderEnhancedTargetHUD(e, (LivingEntity) previousTarget);
            } else {
                renderEnhancedTargetHUD(e, living);
            }
        } else {
            if (animationRunning && previousTarget instanceof LivingEntity) {
                renderEnhancedTargetHUD(e, (LivingEntity) previousTarget);
            }
        }
    }

    private void handleTargetSwitchAnimation(LivingEntity currentTarget) {
        long currentTime = System.currentTimeMillis();

        if (currentTarget != previousTarget) {
            animationStartTime = currentTime;
            previousTarget = currentTarget;
            animationRunning = true;
            animationDirection = currentTarget != null ? 1 : -1;
        }

        if (animationRunning) {
            long elapsed = currentTime - animationStartTime;
            long duration = animationDirection > 0 ? 500 : 300;

            if (elapsed >= duration) {
                animationProgress = animationDirection > 0 ? 1f : 0f;
                animationRunning = false;

                if (animationDirection < 0) {
                    previousTarget = null;
                }
            } else {
                float progress = (float) elapsed / duration;
                if (animationDirection > 0) {
                    animationProgress = easeOutBack(progress);
                } else {
                    animationProgress = 1f - easeInCubic(progress);
                }
            }
        }
    }

    private void applyHUDAnimation(PoseStack stack, float x, float y) {
        if (animationRunning) {
            float centerX = x;
            float centerY = y;

            stack.translate(centerX, centerY, 0);

            if (animationDirection > 0) {
                float scale = 0.8f + 0.2f * animationProgress;
                stack.scale(scale, scale, 1.0f);

                float translateY = (1.0f - animationProgress) * 20.0f;
                stack.translate(0, translateY, 0);
            } else {
                float translateY = -animationProgress * 15.0f;
                stack.translate(0, translateY, 0);
            }

            stack.translate(-centerX, -centerY, 0);
        }
    }

    private void renderEnhancedTargetHUD(EventRender2D e, LivingEntity target) {
        PoseStack stack = e.getStack();
        float x = (float)mc.getWindow().getGuiScaledWidth() / 2.0F + 10.0F;
        float y = (float)mc.getWindow().getGuiScaledHeight() / 2.0F + 10.0F;
        float width = getTHUDWidth(target);
        float height = getTHUDHeight();

        stack.pushPose();

        applyHUDAnimation(stack, x, y);

        if (animationDirection < 0) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f - animationProgress);
        }

        switch (targetHudMode.getCurrentMode()) {
            case "New":
                renderRiseHUD(stack, target, x, y);
                break;
            case "old":
                renderLiteHUD(stack, target, x, y);
                break;
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        stack.popPose();
        this.blurMatrix = new Vector4f(x, y, width, height);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    private static float easeInCubic(float x) {
        return x * x * x;
    }

    private float getTHUDWidth(Entity entity) {
        switch (targetHudMode.getCurrentMode()) {
            case "New": return 160.0F;
            case "old": return Math.max(Fonts.harmony.getWidth(entity.getName().getString(), 0.4f) + 20.0F + 24.0F + 5.0F, 100.0F);
            default: return 120;
        }
    }

    private float getTHUDHeight() {
        switch (targetHudMode.getCurrentMode()) {
            case "New": return 45.0F;
            case "old": return 40.0F;
            default: return 45;
        }
    }

    private void renderRiseHUD(PoseStack stack, LivingEntity target, float x, float y) {
        float hudWidth = 160.0F;
        float hudHeight = 45.0F;
        float avatarSize = 32.0F;
        float padding = 4.0F;

        StencilUtils.write(false);
        RenderUtils.drawRoundedRect(stack, x, y, hudWidth, hudHeight, 6.0F, 0x70000000);
        StencilUtils.erase(true);
        RenderUtils.fillBound(stack, x, y, hudWidth, hudHeight, 0x70000000);
        StencilUtils.dispose();

        float avatarX = x + padding;
        float avatarY = y + (hudHeight - avatarSize) / 2;

        renderEntityHead(stack, target, avatarX, avatarY, avatarSize, avatarSize);

        String targetName = target.getName().getString() + (target.isBaby() ? " (Baby)" : "");
        float textX = x + avatarSize + padding * 2;
        float textY = y + padding + 2;
        Fonts.harmony.render(stack, "Name: " + targetName, (double) textX, (double) textY, Color.WHITE, true, 0.30F);

        String healthText = "HP: " + String.format("%.0f", target.getHealth()) + " / " + String.format("%.0f", target.getMaxHealth());
        float healthTextY = textY + 12;
        Fonts.harmony.render(stack, healthText, (double) textX, (double) healthTextY, Color.WHITE, true, 0.30F);

        float healthBarX = x + avatarSize + padding * 2;
        float healthBarY = y + hudHeight - padding - 8;
        float healthBarWidth = hudWidth - (healthBarX - x) - padding;
        float healthBarHeight = 6.0F;

        float healthRatio = target.getHealth() / target.getMaxHealth();
        if (healthRatio > 1.0F) healthRatio = 1.0F;
        float currentHealthWidth = healthBarWidth * healthRatio;

        RenderUtils.drawRoundedRect(stack, healthBarX, healthBarY, healthBarWidth, healthBarHeight, 4, 0x80404040);

        if (currentHealthWidth > 0) {
            RenderUtils.drawRoundedRect(
                    stack,
                    healthBarX,
                    healthBarY,
                    currentHealthWidth,
                    healthBarHeight,
                    4,
                    0xFF962D2D
            );
        }
    }

    private void renderLiteHUD(PoseStack stack, LivingEntity target, float x, float y) {
        handleTargetSwitchAnimation(target);

        Entity renderTarget = target != null ? target : previousTarget;
        if (!(renderTarget instanceof LivingEntity) || animationProgress <= 0) {
            return;
        }

        LivingEntity livingTarget = (LivingEntity) renderTarget;
        String targetName = livingTarget.getName().getString() + (livingTarget.isBaby() ? " (Baby)" : "");
        float avatarSize = 24.0F;
        float avatarPadding = 5.0F;
        float width = Math.max(Fonts.harmony.getWidth(targetName, 0.4f) + 20.0F + avatarSize + avatarPadding, 100.0F);
        float height = 40.0F;

        stack.pushPose();

        float centerX = x + width / 2;
        float centerY = y + height / 2;
        stack.translate(centerX, centerY, 0);

        if (animationDirection > 0) {
            stack.scale(animationProgress, animationProgress, 1);
        } else {
            float scale = 1.0f - (1.0f - animationProgress) * 0.3f;
            stack.scale(scale, scale, 1);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, animationProgress);
        }

        stack.translate(-centerX, -centerY, 0);

        StencilUtils.write(false);
        RenderUtils.drawRoundedRect(stack, x, y, width, height, 5.0F, HUD.headerColor);
        StencilUtils.erase(true);
        RenderUtils.fillBound(stack, x, y, width, height, HUD.bodyColor);

        float avatarX = x + avatarPadding;
        float avatarY = y + (height - avatarSize) / 2.0F;
        float textX = x + avatarSize + avatarPadding * 2;

        Fonts.harmony.render(stack, targetName, textX, y + 6.0, Color.WHITE, true, 0.35);
        Fonts.harmony.render(stack, "HP: " + Math.round(livingTarget.getHealth()) +
                        (livingTarget.getAbsorptionAmount() > 0.0F ? "+" + Math.round(livingTarget.getAbsorptionAmount()) : ""),
                textX, y + 17.0, Color.WHITE, true, 0.35);

        float progressBarY = y + 30.0F;
        float progressBarWidth = width - avatarSize - avatarPadding * 3;
        float progressBarHeight = 4.0F;
        float currentHealth = livingTarget.getHealth() / livingTarget.getMaxHealth();
        float previousHealth = getPreviousHealth(livingTarget);

        RenderUtils.drawRoundedRect(stack, textX, progressBarY, progressBarWidth, progressBarHeight, 2.0F, new Color(100, 100, 100, 150).getRGB());

        int healthColor = new Color(200, 45, 45, 200).getRGB();
        int damageColor = new Color(150, 150, 150, 180).getRGB();

        if (previousHealth > currentHealth) {
            float damageWidth = progressBarWidth * (previousHealth - currentHealth);
            float damageX = textX + progressBarWidth * currentHealth;
            if (damageWidth > 0) {
                damageWidth += 2.0F;
                damageX -= 1.0F;
                if (damageWidth < 4.0F) damageWidth = 4.0F;
                RenderUtils.drawRoundedRect(stack, damageX, progressBarY, damageWidth, progressBarHeight, 2.0F, damageColor);
            }
        }

        if (currentHealth > 0) {
            float healthWidth = progressBarWidth * currentHealth;
            if (healthWidth < 4.0F) healthWidth = 4.0F;
            float extendedHealthWidth = healthWidth + 1.0F;
            RenderUtils.drawRoundedRect(stack, textX, progressBarY, extendedHealthWidth, progressBarHeight, 2.0F, healthColor);
        }

        StencilUtils.dispose();

        renderEntityHead(stack, livingTarget, avatarX, avatarY, avatarSize, avatarSize);

        stack.popPose();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        updatePreviousHealth(livingTarget, currentHealth);
    }

    private float getPreviousHealth(LivingEntity entity) {
        return previousHealthMap.getOrDefault(entity.getUUID(), entity.getHealth() / entity.getMaxHealth());
    }

    private void updatePreviousHealth(LivingEntity entity, float currentHealth) {
        previousHealthMap.put(entity.getUUID(), currentHealth);
    }

    private Color getHealthColor(LivingEntity entity) {
        float health = entity.getHealth() / entity.getMaxHealth();
        if (health > 0.7f) return Color.GREEN;
        if (health > 0.3f) return Color.YELLOW;
        return Color.RED;
    }

    private Color getHealthColor(float healthRatio) {
        if (healthRatio > 0.6) {
            return new Color(0, 255, 0);
        } else if (healthRatio > 0.3) {
            return new Color(255, 255, 0);
        } else {
            return new Color(255, 0, 0);
        }
    }

    private String getBlockingStatus(LivingEntity entity) {
        return entity.isUsingItem() ? "Using Item" : "Not Using Item";
    }

    @EventTarget
    public void onRender(EventRender e) {
        if (!this.targetEsp.isCurrentMode("Off")) {
            switch (this.targetEsp.getCurrentMode()) {
                case "Senrenbanka":
                    renderNavenStyleESP(e);
                    break;
                case "Nitro":
                    renderNitroStyleESP(e);
                    break;
                case "Colorful":
                    renderColorfulStyleESP(e);
                    break;
            }
        }
    }

    private void renderNavenStyleESP(EventRender e) {
        PoseStack stack = e.getPMatrixStack();
        float partialTicks = e.getRenderPartialTicks();
        stack.pushPose();
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        GL11.glEnable(2848);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderUtils.applyRegionalRenderOffset(stack);

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity living) {
                float[] color = target == living ? targetColorRed : targetColorGreen;
                stack.pushPose();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
                double motionX = entity.getX() - entity.xo;
                double motionY = entity.getY() - entity.yo;
                double motionZ = entity.getZ() - entity.zo;
                AABB boundingBox = entity.getBoundingBox()
                        .move(-motionX, -motionY, -motionZ)
                        .move((double)partialTicks * motionX, (double)partialTicks * motionY, (double)partialTicks * motionZ);
                RenderUtils.drawSolidBox(boundingBox, stack);
                stack.popPose();
            }
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(3042);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(2848);
        stack.popPose();
    }

    private void renderNitroStyleESP(EventRender e) {
        renderTextureBasedESP(e, NITRO_TEXTURE, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderColorfulStyleESP(EventRender e) {
        renderTextureBasedESP(e, COLORFUL_TEXTURE, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderTextureBasedESP(EventRender e, ResourceLocation texture, float r, float g, float b, float a) {
        PoseStack stack = e.getPMatrixStack();
        float partialTicks = e.getRenderPartialTicks();
        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        updateAnimationState();

        stack.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity)) continue;

            double entityX = entity.xo + (entity.getX() - entity.xo) * partialTicks;
            double entityY = entity.yo + (entity.getY() - entity.yo) * partialTicks + entity.getBbHeight() / 2.0;
            double entityZ = entity.zo + (entity.getZ() - entity.zo) * partialTicks;

            float distance = (float) cameraPos.distanceTo(new Vec3(entityX, entityY, entityZ));
            float baseSize = 1.0F + (distance / 20.0F);
            baseSize = Math.max(1.0F, Math.min(baseSize, 3.0F));
            float finalSize = baseSize * currentSizeMultiplier;

            stack.pushPose();
            stack.translate(entityX - cameraPos.x, entityY - cameraPos.y, entityZ - cameraPos.z);
            stack.mulPose(camera.rotation());
            stack.mulPose(Axis.ZP.rotationDegrees(rotationAngle));
            stack.scale(finalSize, finalSize, finalSize);

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderColor(r, g, b, a);

            Tesselator tessellator = Tesselator.getInstance();
            BufferBuilder bufferBuilder = tessellator.getBuilder();
            Matrix4f matrix = stack.last().pose();

            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bufferBuilder.vertex(matrix, -0.5F, -0.5F, 0.0F).uv(0.0F, 0.0F).endVertex();
            bufferBuilder.vertex(matrix, -0.5F, 0.5F, 0.0F).uv(0.0F, 1.0F).endVertex();
            bufferBuilder.vertex(matrix, 0.5F, 0.5F, 0.0F).uv(1.0F, 1.0F).endVertex();
            bufferBuilder.vertex(matrix, 0.5F, -0.5F, 0.0F).uv(1.0F, 0.0F).endVertex();
            tessellator.end();

            stack.popPose();
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        stack.popPose();
    }

    private void updateAnimationState() {
        rotationSpeedTickCounter++;
        if (rotationSpeedTickCounter >= 10) {
            rotationSpeedTickCounter = 0;
            targetRotationSpeed = 3.0F + random.nextFloat() * 3.0F;
        }

        rotationDirectionTickCounter++;
        if (rotationDirectionTickCounter >= 40 + random.nextInt(41)) {
            rotationDirectionTickCounter = 0;
            targetRotationSpeed = 0;
        }

        if (Math.abs(currentRotationSpeed) < 0.1F && targetRotationSpeed == 0) {
            rotationDirection *= -1;
            targetRotationSpeed = 3.0F + random.nextFloat() * 3.0F;
        }
        currentRotationSpeed = Mth.lerp(0.1F, currentRotationSpeed, targetRotationSpeed * rotationDirection);
        rotationAngle += currentRotationSpeed;

        sizeTickCounter++;
        if (sizeTickCounter >= 10) {
            sizeTickCounter = 0;
            targetSizeMultiplier = 1.0F + random.nextFloat() * 0.5F;
        }
        currentSizeMultiplier = Mth.lerp(0.1F, currentSizeMultiplier, targetSizeMultiplier);
    }

    @Override
    public void onEnable() {
        rotation = null;
        this.index = 0;
        target = null;
        aimingTarget = null;
        targets.clear();
        this.interactTicks = 0;
        this.getTicks = 0;

        blockVisual = false;
        blockingTicks = 0;

        previousTarget = null;
        animationRunning = false;
        animationProgress = 0f;
        previousHealthMap.clear();

        rotationAngle = 0.0F;
        currentRotationSpeed = 3.0F;
        targetRotationSpeed = 3.0F;
        rotationSpeedTickCounter = 0;
        rotationDirectionTickCounter = 0;
        rotationDirection = 1;
        currentSizeMultiplier = 1.0F;
        targetSizeMultiplier = 1.0F;
        sizeTickCounter = 0;
    }

    @Override
    public void onDisable() {
        target = null;
        aimingTarget = null;
        this.interactTicks = 0;
        this.getTicks = 0;

        blockVisual = false;
        blockingTicks = 0;

        super.onDisable();
    }

    @EventTarget
    public void onRespawn(EventRespawn e) {
        target = null;
        aimingTarget = null;
        this.toggle();
    }

    @EventTarget
    public void onMotion(EventRunTicks event) {
        if (event.getType() == EventType.PRE && mc.player != null) {
            if (mc.screen instanceof AbstractContainerScreen
                    || Senrenbanka.getInstance().getModuleManager().getModule(Stuck.class).isEnabled()
                    || InventoryUtils.shouldDisableFeatures()) {
                target = null;
                aimingTarget = null;
                this.rotationData = null;
                rotation = null;
                this.lastRotationData = null;
                targets.clear();

                blockVisual = false;
                blockingTicks = 0;
                return;
            }

            boolean isSwitch = this.switchSize.getCurrentValue() > 1.0F;
            this.setSuffix(this.multi.getCurrentValue() ? "Multi" : (isSwitch ? "Switch" : "Grim"));
            this.updateAttackTargets();
            aimingTarget = this.shouldPreAim();
            this.lastRotationData = this.rotationData;
            this.rotationData = null;
            if (aimingTarget != null) {
                this.rotationData = RotationUtils.getRotationDataToEntity(aimingTarget);
                if (this.rotationData.getRotation() != null) {
                    rotation = this.rotationData.getRotation();
                } else {
                    rotation = null;
                }
            }

            if (targets.isEmpty()) {
                target = null;
                blockVisual = false;
                blockingTicks = 0;
                return;
            }

            if (this.index > targets.size() - 1) {
                this.index = 0;
            }

            if (targets.size() > 1
                    && ((float)this.attackTimes >= this.switchAttackTimes.getCurrentValue() || this.rotationData != null && this.rotationData.getDistance() > 3.0)) {
                this.attackTimes = 0;

                for (int i = 0; i < targets.size(); i++) {
                    this.index++;
                    if (this.index > targets.size() - 1) {
                        this.index = 0;
                    }

                    Entity nextTarget = targets.get(this.index);
                    RotationUtils.Data data = RotationUtils.getRotationDataToEntity(nextTarget);
                    if (data.getDistance() < 3.0) {
                        break;
                    }
                }
            }

            if (this.index > targets.size() - 1 || !isSwitch) {
                this.index = 0;
            }

            target = targets.get(this.index);
            this.attacks = this.attacks + this.aps.getCurrentValue() / 20.0F;

            if (this.autoBlock.getCurrentValue() && target != null) {
                blockVisual = true;
                blockingTicks++;
            } else {
                blockVisual = false;
                blockingTicks = 0;
            }
        }
    }

    @EventTarget
    public void onClick(EventClick e) {
        if (mc.player.getUseItem().isEmpty()
                && mc.screen == null
                && Senrenbanka.skipTasks.isEmpty()
                && !NetworkUtils.isServerLag()
                && !Senrenbanka.getInstance().getModuleManager().getModule(Blink.class).isEnabled()) {

            while (this.attacks >= 1.0F) {
                this.doAttack();
                this.attacks--;
            }
        }
    }

    public Entity shouldPreAim() {
        Entity target = Aura.target;
        if (target == null) {
            List<Entity> aimTargets = this.getTargets();
            if (!aimTargets.isEmpty()) {
                target = aimTargets.get(0);
            }
        }

        return target;
    }

    public void doAttack() {
        if (!targets.isEmpty()) {
            HitResult hitResult = mc.hitResult;
            if (hitResult.getType() == Type.ENTITY) {
                EntityHitResult result = (EntityHitResult)hitResult;
                if (AntiBots.isBot(result.getEntity())) {
                    ChatUtils.addChatMessage("Attacking Bot!");
                    return;
                }
            }

            if (this.multi.getCurrentValue()) {
                int attacked = 0;

                for (Entity entity : targets) {
                    if (RotationUtils.getDistance(entity, mc.player.getEyePosition(), RotationManager.rotations) < 3.0) {
                        this.attackEntity(entity);
                        if (++attacked >= 2) {
                            break;
                        }
                    }
                }
            } else if (hitResult.getType() == Type.ENTITY) {
                EntityHitResult result = (EntityHitResult)hitResult;
                this.attackEntity(result.getEntity());
            }
        }
    }

    public void updateAttackTargets() {
        targets = this.getTargets();
    }

    public boolean isValidTarget(Entity entity) {
        if (entity == mc.player) {
            return false;
        } else if (entity instanceof LivingEntity living) {
            if (living instanceof BlinkingPlayer) {
                return false;
            } else {
                AntiBots module = (AntiBots) Senrenbanka.getInstance().getModuleManager().getModule(AntiBots.class);
                if (module == null || !module.isEnabled() || !AntiBots.isBot(entity) && !AntiBots.isBedWarsBot(entity)) {
                    if (Teams.isSameTeam(living)) {
                        return false;
                    } else if (FriendManager.isFriend(living)) {
                        return false;
                    } else if (living.isDeadOrDying() || living.getHealth() <= 0.0F) {
                        return false;
                    } else if (entity instanceof ArmorStand) {
                        return false;
                    } else if (entity.isInvisible() && !this.attackInvisible.getCurrentValue()) {
                        return false;
                    } else if (entity instanceof Player && !this.attackPlayer.getCurrentValue()) {
                        return false;
                    } else if (!(entity instanceof Player) || !((double)entity.getBbWidth() < 0.5) && !living.isSleeping()) {
                        if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat || entity instanceof AbstractGolem)
                                && !this.attackMobs.getCurrentValue()) {
                            return false;
                        } else if ((entity instanceof Animal || entity instanceof Squid) && !this.attackAnimals.getCurrentValue()) {
                            return false;
                        } else {
                            return entity instanceof Villager && !this.attackAnimals.getCurrentValue() ? false : !(entity instanceof Player) || !entity.isSpectator();
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    public boolean isValidAttack(Entity entity) {
        if (!this.isValidTarget(entity)) {
            return false;
        } else if (entity instanceof LivingEntity && (float)((LivingEntity)entity).hurtTime > this.hurtTime.getCurrentValue()) {
            return false;
        } else {
            Vec3 closestPoint = RotationUtils.getClosestPoint(mc.player.getEyePosition(), entity.getBoundingBox());
            return closestPoint.distanceTo(mc.player.getEyePosition()) > (double)this.aimRange.getCurrentValue()
                    ? false
                    : RotationUtils.inFoV(entity, this.fov.getCurrentValue() / 2.0F);
        }
    }

    public void attackEntity(Entity entity) {
        this.attackTimes++;
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        mc.player.setYRot(RotationManager.rotations.x);
        mc.player.setXRot(RotationManager.rotations.y);
        if (entity instanceof Player && !AntiBots.isBot(entity)) {
            KillSay.attackedPlayers.add(entity.getName().getString());
        }

        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (this.moreParticles.getCurrentValue()) {
            mc.player.magicCrit(entity);
            mc.player.crit(entity);
        }

        mc.player.setYRot(currentYaw);
        mc.player.setXRot(currentPitch);
    }

    private List<Entity> getTargets() {
        Stream<Entity> stream = StreamSupport.<Entity>stream(mc.level.entitiesForRendering().spliterator(), true)
                .filter(entity -> entity instanceof Entity)
                .filter(this::isValidAttack);
        List<Entity> possibleTargets = stream.collect(Collectors.toList());
        if (this.priority.isCurrentMode("Range")) {
            possibleTargets.sort(Comparator.comparingDouble(o -> (double)o.distanceTo(mc.player)));
        } else if (this.priority.isCurrentMode("FoV")) {
            possibleTargets.sort(
                    Comparator.comparingDouble(o -> (double)RotationUtils.getDistanceBetweenAngles(RotationManager.rotations.x, RotationUtils.getRotations(o).x))
            );
        } else if (this.priority.isCurrentMode("Health")) {
            possibleTargets.sort(Comparator.comparingDouble(o -> o instanceof LivingEntity living ? (double)living.getHealth() : 0.0));
        }

        if (this.preferBaby.getCurrentValue() && possibleTargets.stream().anyMatch(entity -> entity instanceof LivingEntity && ((LivingEntity)entity).isBaby())) {
            possibleTargets.removeIf(entity -> !(entity instanceof LivingEntity) || !((LivingEntity)entity).isBaby());
        }

        possibleTargets.sort(Comparator.comparing(o -> o instanceof EndCrystal ? 0 : 1));
        return this.infSwitch.getCurrentValue()
                ? possibleTargets
                : possibleTargets.subList(0, (int)Math.min((float)possibleTargets.size(), this.switchSize.getCurrentValue()));
    }

    public boolean isBlockVisual() {
        return blockVisual && this.autoBlock.getCurrentValue();
    }

    public int getBlockingTicks() {
        return blockingTicks;
    }
}
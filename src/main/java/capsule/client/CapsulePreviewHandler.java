package capsule.client;

import capsule.CapsuleMod;
import capsule.Config;
import capsule.blocks.BlockCapsuleMarker;
import capsule.blocks.BlockEntityCapture;
import capsule.client.render.CapsuleTemplateRenderer;
import capsule.helpers.Spacial;
import capsule.items.CapsuleItem;
import capsule.structure.CapsuleTemplate;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import joptsimple.internal.Strings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.*;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

import static capsule.client.RendererUtils.*;
import static capsule.items.CapsuleItem.CapsuleState.*;
import static capsule.structure.CapsuleTemplate.recenterRotation;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;

@Mod.EventBusSubscriber(modid = CapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CapsulePreviewHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final Map<String, List<AABB>> currentPreview = new HashMap<>();
    public static final Map<String, CapsuleTemplate> currentFullPreview = new HashMap<>();
    public static final Map<String, CapsuleTemplateRenderer> cachedFullPreview = new HashMap<>();
    public static final double NS_TO_MS = 0.000001d;
    private static int lastSize = 0;
    private static int lastColor = 0;

    private static int uncompletePreviewsCount = 0;
    private static int completePreviewsCount = 0;
    private static String uncompletePreviewsCountStructure = null;

    static double time = 0;

    public CapsulePreviewHandler() {
    }

    /**
     * Render recall preview when deployed capsule in hand
     */
    @SubscribeEvent
    public static void onWorldRenderLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getInstance();
        time += 1;
        if (mc.player != null) {
            tryPreviewRecall(mc.player.getMainHandItem(), event.getMatrixStack());
            tryPreviewDeploy(mc.player, event.getPartialTicks(), mc.player.getMainHandItem(), event.getMatrixStack());
            tryPreviewLinkedInventory(mc.player, mc.player.getMainHandItem());
        }
    }

    /**
     * try to spot a templateDownload command for client
     */
    @SubscribeEvent
    public static void OnClientChatEvent(ClientChatEvent event) {
        if ("/capsule downloadTemplate".equals(event.getMessage())) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ItemStack heldItem = mc.player.getMainHandItem();
                String structureName = CapsuleItem.getStructureName(heldItem);
                if (heldItem.getItem() instanceof CapsuleItem && structureName != null) {
                    CapsuleTemplate template = currentFullPreview.get(structureName);
                    Path path = new File("capsule_exports").toPath();
                    try {
                        Files.createDirectories(Files.exists(path) ? path.toRealPath() : path);
                    } catch (IOException var19) {
                        LOGGER.error("Failed to create parent directory: {}", path);
                    }
                    try {
                        CompoundTag compoundnbt = template.save(new CompoundTag());
                        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
                        Path filePath = path.resolve(df.format(new Date()) + "-" + structureName + ".nbt");
                        NbtIo.writeCompressed(compoundnbt, new DataOutputStream(new FileOutputStream(filePath.toFile())));
                        mc.player.sendMessage(new TextComponent("→ <minecraftInstance>/" + filePath.toString().replace("\\", "/")), Util.NIL_UUID);
                    } catch (Throwable var21) {
                        LOGGER.error(var21);
                        mc.player.sendMessage(new TranslatableComponent("capsule.error.cantDownload"), Util.NIL_UUID);
                    }
                } else {
                    mc.player.sendMessage(new TranslatableComponent("capsule.error.cantDownload"), Util.NIL_UUID);
                }
            }
        }


    }

    /**
     * set captureBlock data (clientside only ) when capsule is in hand.
     */
    @SubscribeEvent
    public static void onLivingUpdateEvent(TickEvent.PlayerTickEvent event) {
        // do something to player every update tick:
        if (event.player instanceof LocalPlayer && event.phase.equals(TickEvent.Phase.START)) {
            LocalPlayer player = (LocalPlayer) event.player;
            tryPreviewCapture(player, player.getMainHandItem());
        }
    }

    private static boolean tryPreviewCapture(LocalPlayer player, ItemStack heldItem) {
        // an item is in hand
        if (!heldItem.isEmpty()) {
            Item heldItemItem = heldItem.getItem();
            // it's an empty capsule : show capture zones
            if (heldItemItem instanceof CapsuleItem && (CapsuleItem.hasState(heldItem, EMPTY) || CapsuleItem.hasState(heldItem, EMPTY_ACTIVATED))) {
                //noinspection ConstantConditions
                if (heldItem.hasTag() && heldItem.getTag().contains("size")) {
                    setCaptureTESizeColor(heldItem.getTag().getInt("size"), CapsuleItem.getBaseColor(heldItem), player.getCommandSenderWorld());
                    return true;
                }

            } else {
                setCaptureTESizeColor(0, 0, player.getCommandSenderWorld());
            }
        } else {
            setCaptureTESizeColor(0, 0, player.getCommandSenderWorld());
        }

        return false;
    }


    @SuppressWarnings("ConstantConditions")
    private static void tryPreviewDeploy(LocalPlayer thePlayer, float partialTicks, ItemStack heldItemMainhand, PoseStack matrixStack) {
        if (heldItemMainhand.getItem() instanceof CapsuleItem
                && heldItemMainhand.hasTag()
                && isDeployable(heldItemMainhand)
                && !Strings.isNullOrEmpty(heldItemMainhand.getTag().getString("structureName"))
        ) {
            int size = CapsuleItem.getSize(heldItemMainhand);
            BlockHitResult rtc = Spacial.clientRayTracePreview(thePlayer, partialTicks, size);
            if (rtc != null && rtc.getType() == HitResult.Type.BLOCK) {
                int extendSize = (size - 1) / 2;
                BlockPos destOriginPos = rtc.getBlockPos().offset(rtc.getDirection().getNormal()).offset(-extendSize, 0.01, -extendSize);
                String structureName = heldItemMainhand.getTag().getString("structureName");

                if (!structureName.equals(uncompletePreviewsCountStructure)) {
                    uncompletePreviewsCountStructure = structureName;
                    uncompletePreviewsCount = 0;
                    completePreviewsCount = 0;
                }

                AABB errorBoundingBox = new AABB(
                        0,
                        +0.01,
                        0,
                        1.01,
                        1.01,
                        1.01);

                synchronized (CapsulePreviewHandler.currentPreview) {
                    synchronized (CapsulePreviewHandler.currentFullPreview) {
                        boolean haveFullPreview = CapsulePreviewHandler.currentFullPreview.containsKey(structureName);
                        if (haveFullPreview) {
                            if (CapsulePreviewHandler.cachedFullPreview.containsKey(structureName)) {
                                DisplayFullPreview(thePlayer, heldItemMainhand, matrixStack, extendSize, destOriginPos, structureName);

                            }
                        }
                        if (CapsulePreviewHandler.currentPreview.containsKey(structureName) || size == 1) {
                            DisplayWireframePreview(thePlayer, heldItemMainhand, size, rtc, extendSize, destOriginPos, structureName, errorBoundingBox, haveFullPreview);
                        }
                    }
                }
            }
        }

    }

    private static void DisplayFullPreview(LocalPlayer thePlayer, ItemStack heldItemMainhand, PoseStack matrixStack, int extendSize, BlockPos destOriginPos, String structureName) {
        CapsuleTemplate template = CapsulePreviewHandler.currentFullPreview.get(structureName);
        CapsuleTemplateRenderer renderer = CapsulePreviewHandler.cachedFullPreview.get(structureName);
        StructurePlaceSettings placement = CapsuleItem.getPlacement(heldItemMainhand);
        renderer.changeTemplateIfDirty(
                template,
                thePlayer.getCommandSenderWorld(),
                destOriginPos,
                recenterRotation(extendSize, placement),
                placement,
                2
        );

        float glitchIntensity = (float) (Math.abs(Math.cos(time * 0.1f)) * Math.abs(Math.cos(time * 0.14f)) * Math.abs(Math.cos(time * 0.12f))) - 0.3f;
        glitchIntensity = (float) Math.min(0.05, Math.max(0, glitchIntensity));
        float glitchIntensity2 = ((float) (Math.cos(time * 0.12f) * Math.cos(time * 0.15f) * Math.cos(time * 0.14f))) * glitchIntensity;
        float glitchValue = (float) Math.min(0.12, Math.max(0, Math.tan(time * 0.5)));
        float glitchValuey = (float) Math.min(0.32, Math.max(0, Math.tan(time * 0.2)));
        float glitchValuez = (float) Math.min(0.12, Math.max(0, Math.tan(time * 0.8)));

        matrixStack.pushPose();
        matrixStack.translate(
                glitchIntensity2 * glitchValue,
                glitchIntensity * glitchValuey,
                glitchIntensity2 * glitchValuez);
        matrixStack.scale(1 + glitchIntensity2 * glitchValuez, 1 + glitchIntensity * glitchValuey, 1);

        renderer.renderTemplate(matrixStack, thePlayer, destOriginPos);

        matrixStack.popPose();
    }

    public static List<RenderType> getBlockRenderTypes() {
        return ImmutableList.of(RenderType.translucent(), RenderType.cutout(), RenderType.cutoutMipped(), RenderType.solid());
    }

    private static void DisplayWireframePreview(LocalPlayer thePlayer, ItemStack heldItemMainhand, int size, BlockHitResult rtc, int extendSize, BlockPos destOriginPos, String structureName, AABB errorBoundingBox, boolean haveFullPreview) {
        List<AABB> blockspos = new ArrayList<>();
        if (size > 1) {
            blockspos = CapsulePreviewHandler.currentPreview.get(structureName);
        } else if (CapsuleItem.hasState(heldItemMainhand, EMPTY)) {
            // (1/2) hack this renderer for specific case : capture of a 1-sized empty capsule
            BlockPos pos = rtc.getBlockPos().subtract(destOriginPos);
            blockspos.add(new AABB(pos, pos));
        }
        if (blockspos.isEmpty()) {
            BlockPos pos = new BlockPos(extendSize, 0, extendSize);
            blockspos.add(new AABB(pos, pos));
        }

        doPositionPrologue(Minecraft.getInstance().getEntityRenderDispatcher().camera);
        doWirePrologue();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuilder();

        StructurePlaceSettings placement = CapsuleItem.getPlacement(heldItemMainhand);

        for (AABB bb : blockspos) {
            BlockPos recenter = recenterRotation(extendSize, placement);
            AABB dest = CapsuleTemplate.transformedAxisAlignedBB(placement, bb)
                    .move(destOriginPos.getX(), destOriginPos.getY() + 0.01, destOriginPos.getZ())
                    .move(recenter.getX(), recenter.getY(), recenter.getZ())
                    .expandTowards(1, 1, 1);

            int color = 0xDDDDDD;
            if (CapsuleItem.hasState(heldItemMainhand, EMPTY)) {
                // (2/2) hack this renderer for specific case : capture of a 1-sized empty capsule
                GlStateManager._lineWidth(haveFullPreview ? 2.0F : 5.0F);
                color = CapsuleItem.getBaseColor(heldItemMainhand);
            } else {
                for (double j = dest.minZ; j < dest.maxZ; ++j) {
                    for (double k = dest.minY; k < dest.maxY; ++k) {
                        for (double l = dest.minX; l < dest.maxX; ++l) {
                            BlockPos pos = new BlockPos(l, k, j);
                            if (!Config.overridableBlocks.contains(thePlayer.getCommandSenderWorld().getBlockState(pos).getBlock())) {
                                GlStateManager._lineWidth(5.0F);
                                bufferBuilder.begin(1, DefaultVertexFormat.POSITION);
                                setColor(0xaa0000, 255);
                                drawCapsuleCube(errorBoundingBox.move(pos), bufferBuilder);
                                tessellator.end();
                            }
                        }
                    }
                }
            }

            if (!haveFullPreview) {
                GlStateManager._lineWidth(1);
                RenderSystem.enableBlend();
                bufferBuilder.begin(1, DefaultVertexFormat.POSITION);
                setColor(color, 160);
                drawCapsuleCube(dest, bufferBuilder);
                tessellator.end();
            }
        }

        setColor(0xFFFFFF, 255);
        doWireEpilogue();
        doPositionEpilogue();
    }

    private static boolean isDeployable(ItemStack heldItemMainhand) {
        return CapsuleItem.hasState(heldItemMainhand, ACTIVATED)
                || CapsuleItem.hasState(heldItemMainhand, ONE_USE_ACTIVATED)
                || CapsuleItem.hasState(heldItemMainhand, BLUEPRINT)
                || CapsuleItem.getSize(heldItemMainhand) == 1 && !CapsuleItem.hasState(heldItemMainhand, DEPLOYED);
    }

    private static void tryPreviewRecall(ItemStack heldItem, PoseStack matrixStack) {
        // an item is in hand
        if (heldItem != null) {
            Item heldItemItem = heldItem.getItem();
            // it's an empty capsule : show capture zones
            //noinspection ConstantConditions
            if (heldItemItem instanceof CapsuleItem
                    && (CapsuleItem.hasState(heldItem, DEPLOYED) || CapsuleItem.hasState(heldItem, BLUEPRINT))
                    && heldItem.hasTag()
                    && heldItem.getTag().contains("spawnPosition")) {
                previewRecall(heldItem, matrixStack);
            }
        }
    }

    private static void tryPreviewLinkedInventory(LocalPlayer player, ItemStack heldItem) {
        if (heldItem != null) {
            Item heldItemItem = heldItem.getItem();
            if (heldItemItem instanceof CapsuleItem
                    && CapsuleItem.isBlueprint(heldItem)
                    && CapsuleItem.hasSourceInventory(heldItem)) {
                BlockPos location = CapsuleItem.getSourceInventoryLocation(heldItem);
                ResourceKey<Level> dimension = CapsuleItem.getSourceInventoryDimension(heldItem);
                if (location != null
                        && dimension != null
                        && dimension.equals(player.getCommandSenderWorld().dimension())
                        && location.distSqr(player.getX(), player.getY(), player.getZ(), true) < 60 * 60) {
                    previewLinkedInventory(location);
                }
            }
        }
    }

    private static void previewLinkedInventory(BlockPos location) {
        doPositionPrologue(Minecraft.getInstance().getEntityRenderDispatcher().camera);
        doOverlayPrologue();

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuilder();
        bufferBuilder.begin(7, DefaultVertexFormat.POSITION);
        setColor(0x5B9CFF, 80);
        drawCube(location, 0, bufferBuilder);
        tessellator.end();

        doOverlayEpilogue();
        doPositionEpilogue();
    }

    private static void previewRecall(ItemStack capsule, PoseStack matrixStack) {
        if (capsule.getTag() == null) return;
        CompoundTag linkPos = capsule.getTag().getCompound("spawnPosition");

        int size = CapsuleItem.getSize(capsule);
        int extendSize = (size - 1) / 2;
        int color = CapsuleItem.getBaseColor(capsule);

        Camera renderInfo = Minecraft.getInstance().getEntityRenderDispatcher().camera;
        AABB boundingBox = Spacial.getBB(linkPos.getInt("x") + extendSize, linkPos.getInt("y") - 1, linkPos.getInt("z") + extendSize, size, extendSize);
        MultiBufferSource.BufferSource impl = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        VertexConsumer ivertexbuilder = impl.getBuffer(RenderType.lines());
        matrixStack.pushPose();
        matrixStack.translate(-renderInfo.getPosition().x, -renderInfo.getPosition().y, -renderInfo.getPosition().z);

        renderRecallBox(matrixStack, color, boundingBox, ivertexbuilder, time);
        impl.endBatch();

        matrixStack.pushPose();
    }

    private static void setCaptureTESizeColor(int size, int color, Level worldIn) {
        if (size == lastSize && color == lastColor) return;

        // change MinecraftNBT of all existing BlockEntityCapture in the world to make them display the preview zone
        // remember it's client side only
        for (BlockEntityCapture te : new ArrayList<>(BlockEntityCapture.instances)) {
            if (te.getLevel() == worldIn) {
                CompoundTag teData = te.getTileData();
                if (teData.getInt("size") != size || teData.getInt("color") != color) {
                    te.getTileData().putInt("size", size);
                    te.getTileData().putInt("color", color);
                    if (te.getBlockState().hasProperty(BlockCapsuleMarker.TRIGGERED)) {
                        worldIn.setBlock(te.getBlockPos(), te.getBlockState().setValue(BlockCapsuleMarker.TRIGGERED, size <= 0), 2);
                    }
                }
            }
        }
        lastSize = size;
        lastColor = color;
    }

    public static void renderRecallBox(PoseStack matrixStackIn, int color, AABB boundingBox, VertexConsumer ivertexbuilder, double time) {
        final float af = 200 / 255f;
        final float rf = ((color >> 16) & 0xFF) / 255f;
        final float gf = ((color >> 8) & 0xFF) / 255f;
        final float bf = (color & 0xFF) / 255f;
        LevelRenderer.renderLineBox(matrixStackIn, ivertexbuilder, boundingBox.minX, boundingBox.minY, boundingBox.minZ, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ, rf, gf, bf, af, rf, gf, bf);
        for (int i = 0; i < 5; i++) {
            double i1 = getMovingEffectPos(boundingBox, i, time, 0.0015f);
            double i2 = getMovingEffectPos(boundingBox, i, time, 0.0017f);
            double lowI = Math.min(i1, i2);
            double highI = Math.max(i1, i2);
            LevelRenderer.renderLineBox(matrixStackIn, ivertexbuilder,
                    boundingBox.minX, lowI, boundingBox.minZ,
                    boundingBox.maxX, highI, boundingBox.maxZ,
                    rf, gf, bf, 0.01f + i * 0.05f, rf, gf, bf);
        }
    }

    private static double getMovingEffectPos(AABB boundingBox, int t, double incTime, float offset) {
        return boundingBox.minY + (Math.cos(t * incTime * offset) * 0.5 + 0.5) * (boundingBox.maxY - boundingBox.minY);
    }
}

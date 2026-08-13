package com.cleanroommc.ivgtmb.client.gui;

import com.cleanroommc.ivgtmb.IVGTMB;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.util.BlockInfo;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.scene.ImmediateWorldSceneRenderer;
import gregtech.client.renderer.scene.WorldSceneRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.TrackedDummyWorld;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Vector3f;

/**
 * A ModularUI widget that renders a GTCEu machine in 3D, imitating the GTCEu
 * multiblock JEI preview. Multiblocks render their whole structure;
 * single-block
 * machines render only the main machine block. Supports drag-to-rotate and
 * scroll-to-zoom like the JEI multiblock preview.
 */
@SideOnly(Side.CLIENT)
public class MachinePreviewWidget extends Widget<MachinePreviewWidget> {

    private MetaTileEntity metaTileEntity;
    private Map<BlockPos, BlockInfo> customBlocks;
    private char[][][] grid;
    private Map<Character, String> letterMap;
    private ICubeRenderer baseTextureRenderer;
    private ICubeRenderer frontOverlayRenderer;
    private WorldSceneRenderer renderer;
    private boolean built = false;
    // Voltage tier used for the single-block preview; -1 means "use base texture"
    // (multiblock mode) instead of the tier-matching machine casing.
    private int tier = -1;

    private Vector3f center = new Vector3f(0f, 0f, 0f);
    private float rotationYaw = 20f;
    private float rotationPitch = 50f;
    private float zoom = 10f;
    private int lastDragX;
    private int lastDragY;
    private boolean dragging = false;

    public MachinePreviewWidget(MetaTileEntity metaTileEntity) {
        this.metaTileEntity = metaTileEntity;
        registerMouseListeners();
    }

    public MachinePreviewWidget(char[][][] grid, Map<Character, String> letterMap) {
        this.metaTileEntity = null;
        setCustomGrid(grid, letterMap, null, null);
        registerMouseListeners();
    }

    private void registerMouseListeners() {
        listenGuiAction((IGuiAction.MousePressed) mouseButton -> {
            if (mouseButton == 0) {
                dragging = true;
                lastDragX = getContext().getAbsMouseX();
                lastDragY = getContext().getAbsMouseY();
                return true;
            }
            return false;
        });
        listenGuiAction((IGuiAction.MouseReleased) mouseButton -> {
            if (mouseButton == 0) {
                dragging = false;
            }
            return false;
        });
        listenGuiAction((IGuiAction.MouseDrag) (mouseButton, timeSinceClick) -> {
            if (mouseButton == 0 && dragging) {
                int mx = getContext().getAbsMouseX();
                int my = getContext().getAbsMouseY();
                int dx = mx - lastDragX;
                int dy = my - lastDragY;
                lastDragX = mx;
                lastDragY = my;
                rotationYaw = (rotationYaw - dx) % 360f;
                rotationPitch = clamp(rotationPitch + dy, -89f, 89f);
                updateCamera();
                return true;
            }
            return false;
        });
        listenGuiAction((IGuiAction.MouseScroll) (direction, amount) -> {
            float delta = direction == UpOrDown.UP ? -0.5f : 0.5f;
            zoom = clamp(zoom + delta, 3f, 999f);
            updateCamera();
            return true;
        });
    }

    public void setMachine(MetaTileEntity metaTileEntity) {
        this.metaTileEntity = metaTileEntity;
        this.customBlocks = null;
        this.built = false;
        this.renderer = null;
    }

    public void setCustomGrid(char[][][] grid, Map<Character, String> letterMap) {
        // Keep any previously selected base texture / front overlay so the main
        // block keeps rendering with them across grid updates.
        setCustomGrid(grid, letterMap, this.baseTextureRenderer, this.frontOverlayRenderer);
    }

    /**
     * Prepares a 1x1x1 preview for a single-block machine. The main block renders
     * the machine casing matching the given voltage tier as its base texture
     * ({@link Textures#VOLTAGE_CASINGS}) plus the selected front overlay, exactly
     * like the exported SimpleMachine / SimpleGenerator.
     */
    public void setSingleBlockPreview(ICubeRenderer frontOverlay, int tier) {
        this.metaTileEntity = null;
        this.grid = new char[][][] { { { 'S' } } };
        this.letterMap = null;
        this.baseTextureRenderer = null;
        this.frontOverlayRenderer = frontOverlay;
        this.tier = tier;
        this.customBlocks = null;
        this.built = false;
        this.renderer = null;
    }

    /**
     * Sets a custom multiblock structure grid for preview. The main block (letter
     * 'S') is rendered through a temporary {@link MetaTileEntityHolder} so the
     * selected base texture and front overlay from the new-machine page are shown.
     */
    public void setCustomGrid(char[][][] grid, Map<Character, String> letterMap,
            ICubeRenderer baseTexture, ICubeRenderer frontOverlay) {
        this.metaTileEntity = null;
        this.grid = grid;
        this.letterMap = letterMap;
        this.baseTextureRenderer = baseTexture;
        this.frontOverlayRenderer = frontOverlay;
        this.tier = -1;
        this.customBlocks = null;
        this.built = false;
        this.renderer = null;
    }

    private void buildScene() {
        if (built) {
            return;
        }
        built = true;

        TrackedDummyWorld world = new TrackedDummyWorld();
        Map<BlockPos, BlockInfo> blockMap;

        if (grid != null) {
            blockMap = buildBlocksFromGrid(world, grid, letterMap, baseTextureRenderer, frontOverlayRenderer, tier);
        } else if (customBlocks != null && !customBlocks.isEmpty()) {
            blockMap = new HashMap<>(customBlocks);
        } else {
            blockMap = new HashMap<>();
            if (metaTileEntity instanceof MultiblockControllerBase) {
                List<MultiblockShapeInfo> shapes = ((MultiblockControllerBase) metaTileEntity).getMatchingShapes();
                if (!shapes.isEmpty()) {
                    BlockInfo[][][] blocks = shapes.get(0).getBlocks();
                    for (int x = 0; x < blocks.length; x++) {
                        for (int y = 0; y < blocks[x].length; y++) {
                            for (int z = 0; z < blocks[x][y].length; z++) {
                                if (blocks[x][y][z] != null) {
                                    blockMap.put(new BlockPos(x, y, z), blocks[x][y][z]);
                                }
                            }
                        }
                    }
                }
            }

            if (blockMap.isEmpty()) {
                if (metaTileEntity != null) {
                    // Single-block machine: show only the main machine block. Resolved
                    // via reflection to avoid loading BlockMachine's CTM interface.
                    int metaId = GregTechAPI.MTE_REGISTRY.getIdByObjectName(metaTileEntity.metaTileEntityId);
                    blockMap.put(BlockPos.ORIGIN, new BlockInfo(getMachineBlockState(metaId)));
                } else {
                    blockMap.put(BlockPos.ORIGIN, new BlockInfo(Blocks.STONE.getDefaultState()));
                }
            }
        }

        world.addBlocks(blockMap);

        ImmediateWorldSceneRenderer sceneRenderer = new ImmediateWorldSceneRenderer(world);
        sceneRenderer.addRenderedBlocks(world.renderedBlocks, (isTransparent, pass, layer) -> {
        });
        sceneRenderer.setClearColor(0xFF333333);

        Vector3f size = world.getSize();
        Vector3f minPos = world.getMinPos();
        this.center = new Vector3f(minPos.x + size.x / 2f, minPos.y + size.y / 2f, minPos.z + size.z / 2f);
        float max = Math.max(Math.max(Math.max(size.x, size.y), size.z), 1f);
        this.zoom = (float) (3.5 * Math.sqrt(max));

        this.renderer = sceneRenderer;
        updateCamera();
    }

    private void updateCamera() {
        if (renderer == null) {
            return;
        }
        double yawRad = Math.toRadians(rotationYaw);
        double pitchRad = Math.toRadians(rotationPitch);
        float ex = center.x + zoom * (float) Math.cos(pitchRad) * (float) Math.sin(yawRad);
        float ey = center.y + zoom * (float) Math.sin(pitchRad);
        float ez = center.z + zoom * (float) Math.cos(pitchRad) * (float) Math.cos(yawRad);
        renderer.setCameraLookAt(new Vector3f(ex, ey, ez), center, new Vector3f(0f, 1f, 0f));
    }

    private static IBlockState getMachineBlockState(int metaId) {
        try {
            Class<?> metaBlocks = Class.forName("gregtech.common.blocks.MetaBlocks");
            Object machine = metaBlocks.getField("MACHINE").get(null);
            Method method = machine.getClass().getMethod("getStateFromMeta", int.class);
            return (IBlockState) method.invoke(machine, metaId);
        } catch (Exception e) {
            return Blocks.STONE.getDefaultState();
        }
    }

    private static Map<BlockPos, BlockInfo> buildBlocksFromGrid(World world, char[][][] grid,
            Map<Character, String> letterMap,
            ICubeRenderer base, ICubeRenderer overlay, int tier) {
        Map<BlockPos, BlockInfo> map = new HashMap<>();
        for (int z = 0; z < grid.length; z++) {
            for (int y = 0; y < grid[z].length; y++) {
                for (int x = 0; x < grid[z][y].length; x++) {
                    char c = grid[z][y][x];
                    if (c == 0 || c == ' ') {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (c == 'S') {
                        map.put(pos, buildMainBlockInfo(world, pos, base, overlay, tier));
                    } else {
                        map.put(pos, new BlockInfo(resolveBlockState(c, letterMap)));
                    }
                }
            }
        }
        return map;
    }

    /**
     * Builds the main machine block. When a base texture and/or front overlay is
     * available, the block carries a {@link MetaTileEntityHolder} whose temporary
     * controller renders the selected textures via the TESR pass of the
     * {@link WorldSceneRenderer}.
     */
    private static BlockInfo buildMainBlockInfo(World world, BlockPos pos,
            ICubeRenderer base, ICubeRenderer overlay, int tier) {
        IBlockState state = getMachineBlockState(0);
        if (base == null && overlay == null && tier < 0) {
            return new BlockInfo(state);
        }
        try {
            // Place the machine block first so MetaTileEntityHolder.setMetaTileEntity()
            // (which reads e.g. the 'opaque' property of the block state at its pos)
            // does not fail because the dummy world still has air there.
            world.setBlockState(pos, state);
            // MetaTileEntityHolder is referenced reflectively because the class pulls in
            // AE2's IGridProxyable which is not on the compile classpath.
            Class<?> holderClass = Class.forName("gregtech.api.metatileentity.MetaTileEntityHolder");
            Object holder = holderClass.getConstructor().newInstance();
            holderClass.getMethod("setWorld", World.class).invoke(holder, world);
            holderClass.getMethod("setPos", BlockPos.class).invoke(holder, pos);
            MetaTileEntity preview = new PreviewMultiblock(new ResourceLocation("ivgtmb", "preview"), base, overlay,
                    tier);
            Method setMte = holderClass.getMethod("setMetaTileEntity", MetaTileEntity.class);
            setMte.invoke(holder, preview);
            return new BlockInfo(state, (TileEntity) holder);
        } catch (Exception e) {
            IVGTMB.LOGGER.warn("Failed to build preview main block, falling back to plain machine block", e);
            return new BlockInfo(state);
        }
    }

    /**
     * A temporary multiblock controller used only for rendering the preview main
     * block with the user-selected base texture and front overlay. It is never
     * registered or used in-game.
     */
    private static class PreviewMultiblock extends RecipeMapMultiblockController {
        private final ICubeRenderer base;
        private final ICubeRenderer overlay;
        private final int tier;

        PreviewMultiblock(ResourceLocation id, ICubeRenderer base, ICubeRenderer overlay, int tier) {
            super(id, null);
            this.base = base;
            this.overlay = overlay;
            this.tier = tier;
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new PreviewMultiblock(metaTileEntityId, base, overlay, tier);
        }

        @Override
        public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
            // Single-block machines use the machine casing matching their voltage
            // tier as base texture; multiblock previews use the selected base texture.
            if (tier >= 0 && tier < Textures.VOLTAGE_CASINGS.length) {
                return Textures.VOLTAGE_CASINGS[tier];
            }
            return base;
        }

        @Override
        protected ICubeRenderer getFrontOverlay() {
            return overlay;
        }

        @Override
        protected BlockPattern createStructurePattern() {
            // Never matched in-game; the preview only renders via renderMetaTileEntity.
            return null;
        }
    }

    private static IBlockState resolveBlockState(char c, Map<Character, String> letterMap) {
        if (c == 'S') {
            // Main block: render as the machine block.
            return getMachineBlockState(0);
        }
        String desc = letterMap == null ? null : letterMap.get(c);
        if (desc != null && !desc.trim().isEmpty()) {
            IBlockState state = parseBlockState(desc.trim());
            if (state != null) {
                return state;
            }
        }
        // Fallback: colour by letter hash.
        int color = (c * 31) & 15;
        return Blocks.STAINED_GLASS.getStateFromMeta(color);
    }

    /**
     * Parses a block state description of the form
     * {@code "blockName property=value property=value"} (e.g.
     * {@code "gregtech:metal_casing variant=aluminium_frostproof"}), matching how
     * the exported Groovy code resolves block states. Falls back to the block's
     * default state when no properties are given.
     */
    private static IBlockState parseBlockState(String desc) {
        String[] parts = desc.trim().split(" ");
        String blockName = parts[0];
        String[] props = new String[parts.length - 1];
        System.arraycopy(parts, 1, props, 0, props.length);

        // GTCEu blocks (e.g. gregtech:metal_casing) are not registered through
        // Block.getBlockFromName; resolve them via gregtech.common.blocks.MetaBlocks.
        if (blockName.startsWith("gregtech:")) {
            IBlockState gs = parseGregTechBlockState(blockName, props);
            if (gs != null) {
                return gs;
            }
        }

        // Resolve the block state via the block registry. ResourceLocation defaults
        // to the "minecraft" namespace when none is given, so no prefix is needed.
        Block block = null;
        try {
            block = Block.REGISTRY.getObject(new ResourceLocation(blockName));
        } catch (Exception ignored) {
            // fall through
        }
        if (block == null) {
            return null;
        }
        IBlockState state = block.getDefaultState();
        for (String propStr : props) {
            String[] kv = propStr.split("=");
            if (kv.length == 2) {
                String propName = kv[0].trim();
                String propValue = kv[1].trim();
                for (IProperty<?> prop : state.getPropertyKeys()) {
                    if (prop.getName().equals(propName)) {
                        state = applyProperty(state, prop, propValue);
                        break;
                    }
                }
            }
        }
        return state;
    }

    /**
     * Resolves a GTCEu block such as
     * {@code gregtech:metal_casing variant=coke_bricks}
     * by scanning the static fields of {@code gregtech.common.blocks.MetaBlocks}.
     */
    private static IBlockState parseGregTechBlockState(String blockName, String[] props) {
        try {
            String path = blockName.substring("gregtech:".length());
            Class<?> metaBlocks = Class.forName("gregtech.common.blocks.MetaBlocks");
            for (Field f : metaBlocks.getFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                Object blockObj = f.get(null);
                if (!(blockObj instanceof Block)) {
                    continue;
                }
                Block b = (Block) blockObj;
                ResourceLocation rl = b.getRegistryName();
                if (rl == null || !rl.getPath().equals(path)) {
                    continue;
                }
                for (String p : props) {
                    String[] kv = p.split("=");
                    if (kv.length == 2 && kv[0].trim().equalsIgnoreCase("variant")) {
                        IBlockState s = getStateByVariant(b, kv[1].trim());
                        if (s != null) {
                            return s;
                        }
                    }
                }
                return b.getDefaultState();
            }
        } catch (Exception ignored) {
            // fall through to the standard block-state resolution below
        }
        return null;
    }

    /**
     * GTCEu casing blocks expose a {@code getState(Enum)} method; call it with the
     * enum constant whose name matches the given variant (e.g.
     * {@code COKE_BRICKS}).
     */
    private static IBlockState getStateByVariant(Block block, String variantName) {
        try {
            for (Method m : block.getClass().getMethods()) {
                if (m.getName().equals("getState") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isEnum()) {
                    Class<?> enumType = m.getParameterTypes()[0];
                    for (Object constant : enumType.getEnumConstants()) {
                        if (constant.toString().equalsIgnoreCase(variantName)) {
                            return (IBlockState) m.invoke(block, constant);
                        }
                    }
                    break;
                }
            }
        } catch (Exception ignored) {
            // fall through to default state
        }
        return null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static IBlockState applyProperty(IBlockState state, IProperty prop, String value) {
        try {
            // MC 1.12.2 IProperty.parseValue returns a Guava Optional.
            com.google.common.base.Optional parsed = prop.parseValue(value);
            if (parsed.isPresent()) {
                return state.withProperty(prop, (Comparable) parsed.get());
            }
        } catch (Exception ignored) {
            // fall through to the default state
        }
        return state;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        buildScene();
        if (renderer == null) {
            return;
        }
        Area area = getArea();
        renderer.render(area.x, area.y, area.width, area.height,
                context.getAbsMouseX(), context.getAbsMouseY());
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }
}

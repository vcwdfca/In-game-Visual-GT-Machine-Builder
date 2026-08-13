/*
 * In-game Visual GT Machine Builder (ivgtmb)
 * Copyright (c) 2026 SiO-0
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.cleanroommc.ivgtmb.client.gui;

import com.cleanroommc.ivgtmb.IVGTMB;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.CustomModularScreen;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.metatileentity.SimpleGeneratorMetaTileEntity;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.metatileentity.SteamMetaTileEntity;
import gregtech.api.metatileentity.TieredMetaTileEntity;
import gregtech.api.metatileentity.WorkableTieredMetaTileEntity;
import gregtech.api.metatileentity.multiblock.FuelMultiblockController;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Main UI of the In-game Visual GT Machine Builder (300x320).
 * <p>
 * Left side: machine 3D preview + "Export Groovy file". Category buttons and
 * "Add history"/"New machine" sit left of the panel border. The right side is a
 * scrollable content area that shows the "New machine" form and the multiblock
 * structure editor.
 */
@SideOnly(Side.CLIENT)
public class BuilderUI extends CustomModularScreen {

    public static final int WIDTH = 450;
    public static final int HEIGHT = 300;

    public static final int CAT_SINGLE_MACHINE = 0;
    public static final int CAT_SINGLE_GENERATOR = 1;
    public static final int CAT_MULTI_MACHINE = 2;
    public static final int CAT_MULTI_GENERATOR = 3;

    private static final String MODE_DEFAULT = "default";
    private static final String MODE_NEW_MACHINE = "new_machine";
    private static final String MODE_STRUCTURE = "structure";
    private static final String MODE_HISTORY = "history";
    private static final String MODE_RECIPEMAP = "recipemap";

    private static final int DIVIDER_COLOR = 0xFF404040;
    private static final int PANEL_BG_COLOR = 0x22000000;
    private static final int BTN_H = 18;
    private static final int BTN_GAP = 4;
    private static final int FIELD_H = 14;

    private static final int CAT_X = -96;
    private static final int CAT_W = 88;
    private static final int CAT_H = 20;
    private static final int CAT_GAP = 6;
    private static final int CAT_START_Y = 18;

    // Persistent state across screen rebuilds.
    private static int currentCategory = CAT_SINGLE_MACHINE;
    private static String rightMode = MODE_DEFAULT;

    private static String machineId = "";
    private static String machineMeta = "32000";
    private static int structX = 3;
    private static int structY = 3;
    private static int structZ = 3;
    private static char[][][] grid = new char[3][3][3];
    private static Map<Character, String> letterMap = createDefaultLetterMap();

    private static int frontOverlayIndex = 0;
    private static int baseTextureIndex = 0;
    private static int pageIndex = 0;
    private static int letterPageIndex = 0;
    private static String selectedHistoryFile = null;
    private static final Deque<String> modeStack = new ArrayDeque<>();

    // Recipe map creation state.
    private static String rmId = "";
    private static String rmItemInputs = "1";
    private static String rmItemOutputs = "1";
    private static String rmFluidInputs = "0";
    private static String rmFluidOutputs = "0";

    // Recipe map modification state.
    private static String rmModifyTarget = "";
    private static String rmModifyItemInputs = "1";
    private static String rmModifyItemOutputs = "1";
    private static String rmModifyFluidInputs = "0";
    private static String rmModifyFluidOutputs = "0";

    // New machine page state.
    private static String machineRecipeMap = "";
    private static String machineParallel = "1";
    private static String machineTier = "1";

    private static List<MetaTileEntity> cachedMachines;
    private static boolean machinesLoaded = false;

    private MachinePreviewWidget previewWidget;
    private Flow leftColumn;
    private ScrollWidget rightScroll;
    private Flow mainRow;
    private ModularPanel mainPanel;
    private static final int SELECT_PAGE_SIZE = 8;
    private static int selectionCounter = 0;

    public BuilderUI() {
        super(IVGTMB.MODID);
        openParentOnClose(true);
    }

    public static void open() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return;
        }
        ClientGUI.open(new BuilderUI());
    }

    @Override
    public ModularPanel buildUI(ModularGuiContext context) {
        loadMachinesIfNeeded();
        RendererOptions.load();
        ModularPanel panel = new ModularPanel("builder").size(WIDTH, HEIGHT);
        this.mainPanel = panel;

        panel.child(new TextWidget<>(IKey.lang("ivgtmb.gui.title"))
                .leftRel(0.5f).top(5));
        panel.child(new Rectangle().color(DIVIDER_COLOR).asWidget()
                .left(4).right(4).height(1).top(16));

        this.previewWidget = null;
        this.leftColumn = Flow.column()
                .sizeRel(0.6f, 1f)
                .padding(6)
                .background(new Rectangle().color(PANEL_BG_COLOR));
        this.rightScroll = new ScrollWidget();
        this.rightScroll.background(new Rectangle().color(PANEL_BG_COLOR));
        this.rightScroll.sizeRel(0.4f, 1f);
        this.mainRow = Flow.row()
                .left(4).right(4).top(20)
                .height(HEIGHT - 24)
                .child(this.leftColumn)
                .child(this.rightScroll);
        panel.child(this.mainRow);
        refreshLayout();

        addCategoryButton(panel, CAT_SINGLE_MACHINE, "ivgtmb.gui.cat.single_machine");
        addCategoryButton(panel, CAT_SINGLE_GENERATOR, "ivgtmb.gui.cat.single_generator");
        addCategoryButton(panel, CAT_MULTI_MACHINE, "ivgtmb.gui.cat.multiblock_machine");
        addCategoryButton(panel, CAT_MULTI_GENERATOR, "ivgtmb.gui.cat.multiblock_generator");

        int historyY = CAT_START_Y + 4 * (CAT_H + CAT_GAP);
        addLeftButton(panel, historyY, "ivgtmb.gui.history", b -> {
            clearMachineState();
            rightMode = MODE_HISTORY;
            refreshLayout();
            return true;
        });
        addLeftButton(panel, historyY + CAT_H + CAT_GAP, "ivgtmb.gui.back", b -> {
            goBack();
            return true;
        });
        addLeftButton(panel, historyY + 2 * (CAT_H + CAT_GAP), "ivgtmb.gui.recipemap", b -> {
            rightMode = MODE_RECIPEMAP;
            refreshLayout();
            return true;
        });

        return panel;
    }

    private Widget<?> buildLeftContent() {
        if (MODE_HISTORY.equals(rightMode)) {
            return buildHistoryList();
        }
        if (MODE_RECIPEMAP.equals(rightMode)) {
            return buildRecipeMapAdd();
        }
        Flow col = Flow.column().fullWidth().expanded();
        // A fresh preview widget each rebuild, so its (constructor-registered) mouse
        // listeners survive a dispose of the previous instance.
        MachinePreviewWidget preview = new MachinePreviewWidget(getCurrentMachine());
        if (MODE_STRUCTURE.equals(rightMode)) {
            preview.setCustomGrid(grid, letterMap,
                    RendererOptions.baseTextureRendererAt(baseTextureIndex),
                    RendererOptions.overlayRendererAt(frontOverlayIndex));
        } else if (MODE_NEW_MACHINE.equals(rightMode) && isSingleBlock()) {
            // Single-block machines render only the main block, using the machine
            // casing of the entered tier as base texture plus the selected overlay.
            preview.setSingleBlockPreview(
                    RendererOptions.overlayRendererAt(frontOverlayIndex), tier());
        }
        this.previewWidget = preview;
        col.child(preview.fullWidth().expanded());
        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginTop(BTN_GAP)
                .overlay(IKey.lang("ivgtmb.gui.export"))
                .onMousePressed(b -> {
                    exportGroovyFile();
                    return true;
                }));
        return col;
    }

    private void updateRightContent() {
        if (rightScroll != null) {
            disposeChildren(rightScroll);
            rightScroll.child(MODE_HISTORY.equals(rightMode) ? buildHistoryRead() : buildRightContent());
            rightScroll.scheduleResize();
        }
    }

    private static void disposeChildren(IWidget parent) {
        List<IWidget> children = new ArrayList<>(parent.getChildren());
        for (IWidget child : children) {
            child.dispose();
        }
        parent.getChildren().clear();
    }

    /**
     * Rebuilds both the left and right halves of the main row, so mode switches
     * (history / new machine / back) take effect without reopening the screen.
     */
    private void refreshLayout() {
        if (leftColumn == null || rightScroll == null) {
            return;
        }
        disposeChildren(leftColumn);
        leftColumn.child(buildLeftContent());
        leftColumn.scheduleResize();

        disposeChildren(rightScroll);
        rightScroll.child(MODE_HISTORY.equals(rightMode) ? buildHistoryRead() : buildRightContent());
        rightScroll.scheduleResize();

        if (previewWidget != null) {
            if (MODE_STRUCTURE.equals(rightMode)) {
                previewWidget.setCustomGrid(grid, letterMap);
            } else if (MODE_NEW_MACHINE.equals(rightMode) && isSingleBlock()) {
                previewWidget.setSingleBlockPreview(
                        RendererOptions.overlayRendererAt(frontOverlayIndex), tier());
            } else if (!MODE_HISTORY.equals(rightMode)) {
                previewWidget.setMachine(getCurrentMachine());
            }
        }
    }

    private static void clearMachineState() {
        machineId = "";
        machineMeta = "32000";
        structX = 3;
        structY = 3;
        structZ = 3;
        grid = new char[3][3][3];
        letterMap = createDefaultLetterMap();
        frontOverlayIndex = 0;
        baseTextureIndex = 0;
        pageIndex = 0;
        letterPageIndex = 0;
        machineRecipeMap = "";
        machineParallel = "1";
        machineTier = "1";
    }

    private void goBack() {
        rightMode = modeStack.isEmpty() ? MODE_DEFAULT : modeStack.pop();
        refreshLayout();
    }

    private Widget<?> buildRightContent() {
        switch (rightMode) {
            case MODE_NEW_MACHINE:
                return buildNewMachineContent();
            case MODE_STRUCTURE:
                return buildStructureContent();
            case MODE_RECIPEMAP:
                return buildRecipeMapModify();
            default:
                return buildCategoryContent();
        }
    }

    private Widget<?> buildCategoryContent() {
        Flow col = Flow.column().fullWidth().padding(4);
        col.child(new TextWidget<>(IKey.lang(categoryTitleKey()))
                .fullWidth().scale(0.9f));
        col.child(new Rectangle().color(DIVIDER_COLOR).asWidget()
                .fullWidth().height(1).marginTop(2).marginBottom(2));
        // New machine is available from every category.
        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginTop(4)
                .overlay(IKey.lang("ivgtmb.gui.new_machine"))
                .onMousePressed(b -> {
                    modeStack.push(rightMode);
                    rightMode = MODE_NEW_MACHINE;
                    updateRightContent();
                    if (previewWidget != null) {
                        previewWidget.setMachine(getCurrentMachine());
                    }
                    return true;
                }));
        return col;
    }

    private Widget<?> buildRecipeMapAdd() {
        Flow col = Flow.column().fullWidth().padding(4);
        col.child(new TextWidget<>(IKey.lang("ivgtmb.gui.rm_add_title")).fullWidth().scale(0.9f));
        col.child(new Rectangle().color(DIVIDER_COLOR).asWidget()
                .fullWidth().height(1).marginTop(2).marginBottom(2));

        col.child(IKey.lang("ivgtmb.gui.rm_id").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> rmId, v -> rmId = v))
                .setMaxLength(64)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(IKey.lang("ivgtmb.gui.rm_item_inputs").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> rmItemInputs, v -> rmItemInputs = v))
                .setNumbers().setMaxLength(4)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(IKey.lang("ivgtmb.gui.rm_item_outputs").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> rmItemOutputs, v -> rmItemOutputs = v))
                .setNumbers().setMaxLength(4)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(IKey.lang("ivgtmb.gui.rm_fluid_inputs").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> rmFluidInputs, v -> rmFluidInputs = v))
                .setNumbers().setMaxLength(4)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(IKey.lang("ivgtmb.gui.rm_fluid_outputs").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> rmFluidOutputs, v -> rmFluidOutputs = v))
                .setNumbers().setMaxLength(4)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginTop(4)
                .overlay(IKey.lang("ivgtmb.gui.rm_add_confirm"))
                .onMousePressed(b -> {
                    IVGTMB.LOGGER.info("Recipe map recorded for export: {} [{},{},{},{}]",
                            rmId, rmItemInputs, rmItemOutputs, rmFluidInputs, rmFluidOutputs);
                    return true;
                }));
        return col;
    }

    private Widget<?> buildRecipeMapModify() {
        Flow col = Flow.column().fullWidth().padding(4);
        col.child(new TextWidget<>(IKey.lang("ivgtmb.gui.rm_modify_title")).fullWidth().scale(0.9f));
        col.child(new Rectangle().color(DIVIDER_COLOR).asWidget()
                .fullWidth().height(1).marginTop(2).marginBottom(2));

        col.child(IKey.lang("ivgtmb.gui.rm_select").asWidget().fullWidth().scale(0.7f));
        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).margin(0, BTN_GAP / 2)
                .overlay(IKey.str(rmModifyTarget == null || rmModifyTarget.isEmpty() ? "(none)" : rmModifyTarget))
                .onMousePressed(b -> {
                    List<String> names = new ArrayList<>();
                    for (RecipeMap<?> rm : RecipeMap.getRecipeMaps()) {
                        names.add(rm.getUnlocalizedName());
                    }
                    openSelectionPanel(IKey.lang("ivgtmb.gui.rm_select").get(), names, sel -> {
                        rmModifyTarget = sel;
                        RecipeMap<?> rm = RecipeMap.getByName(sel);
                        if (rm != null) {
                            rmModifyItemInputs = String.valueOf(rm.getMaxInputs());
                            rmModifyItemOutputs = String.valueOf(rm.getMaxOutputs());
                            rmModifyFluidInputs = String.valueOf(rm.getMaxFluidInputs());
                            rmModifyFluidOutputs = String.valueOf(rm.getMaxFluidOutputs());
                        }
                        updateRightContent();
                    });
                    return true;
                }));
        if (rmModifyTarget != null && !rmModifyTarget.isEmpty()) {
            RecipeMap<?> current = RecipeMap.getByName(rmModifyTarget);
            if (current != null) {
                col.child(IKey.str("当前: " + current.getUnlocalizedName() + "  物品输入 " + current.getMaxInputs()
                        + " 输出 " + current.getMaxOutputs() + " 流体输入 " + current.getMaxFluidInputs()
                        + " 输出 " + current.getMaxFluidOutputs()).asWidget()
                        .fullWidth().scale(0.6f).marginTop(4).marginBottom(4));
            }
        }

        col.child(IKey.lang("ivgtmb.gui.rm_item_inputs").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> rmModifyItemInputs, v -> rmModifyItemInputs = v))
                .setNumbers().setMaxLength(4)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(IKey.lang("ivgtmb.gui.rm_item_outputs").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> rmModifyItemOutputs, v -> rmModifyItemOutputs = v))
                .setNumbers().setMaxLength(4)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(IKey.lang("ivgtmb.gui.rm_fluid_inputs").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> rmModifyFluidInputs, v -> rmModifyFluidInputs = v))
                .setNumbers().setMaxLength(4)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(IKey.lang("ivgtmb.gui.rm_fluid_outputs").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> rmModifyFluidOutputs, v -> rmModifyFluidOutputs = v))
                .setNumbers().setMaxLength(4)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginTop(4)
                .overlay(IKey.lang("ivgtmb.gui.rm_modify_confirm"))
                .onMousePressed(b -> {
                    IVGTMB.LOGGER.info("Recipe map modification recorded for export: {} [{},{},{},{}]",
                            rmModifyTarget, rmModifyItemInputs, rmModifyItemOutputs,
                            rmModifyFluidInputs, rmModifyFluidOutputs);
                    return true;
                }));
        return col;
    }

    /**
     * Opens a 300x200 selection panel titled with the button's label, listing the
     * given options as buttons (paginated). Picking one applies it via
     * {@code onSelect} and closes the panel.
     */
    private void openSelectionPanel(String title, List<String> options, Consumer<String> onSelect) {
        if (mainPanel == null || options == null || options.isEmpty()) {
            return;
        }
        IPanelHandler[] handlerRef = new IPanelHandler[1];
        handlerRef[0] = IPanelHandler.simple(mainPanel, (parent, player) -> {
            int[] page = { 0 };
            // Unique name so PanelManager does not reuse a previously built panel
            // with the same name (which would show stale options).
            String panelName = "selection_" + (selectionCounter++);
            ModularPanel p = new ModularPanel(panelName).size(300, 200);
            p.child(new TextWidget<>(IKey.str(title)).leftRel(0.5f).top(5));
            p.child(new Rectangle().color(DIVIDER_COLOR).asWidget()
                    .left(4).right(4).top(16).height(1));

            Flow listCol = Flow.column().fullWidth().padding(4);
            ScrollWidget scroll = new ScrollWidget();
            scroll.background(new Rectangle().color(0x11000000));
            scroll.left(4).right(4).top(20).bottom(26);
            scroll.child(listCol);
            p.child(scroll);

            p.child(new ButtonWidget<>()
                    .left(4).bottom(4).size(60, 18)
                    .overlay(IKey.lang("ivgtmb.gui.prev_page"))
                    .onMousePressed(b -> {
                        if (page[0] > 0) {
                            page[0]--;
                            rebuildSelectionList(listCol, options, page[0], onSelect, handlerRef[0]);
                        }
                        return true;
                    }));
            p.child(new ButtonWidget<>()
                    .right(4).bottom(4).size(60, 18)
                    .overlay(IKey.lang("ivgtmb.gui.next_page"))
                    .onMousePressed(b -> {
                        if (page[0] < totalPages(options) - 1) {
                            page[0]++;
                            rebuildSelectionList(listCol, options, page[0], onSelect, handlerRef[0]);
                        }
                        return true;
                    }));
            p.child(new TextWidget<>(IKey.dynamic(() -> (page[0] + 1) + "/" + Math.max(1, totalPages(options))))
                    .leftRel(0.5f).bottom(4));

            rebuildSelectionList(listCol, options, 0, onSelect, handlerRef[0]);
            return p;
        }, true);
        handlerRef[0].openPanel();
    }

    private static int totalPages(List<String> options) {
        return (options.size() + SELECT_PAGE_SIZE - 1) / SELECT_PAGE_SIZE;
    }

    private static void rebuildSelectionList(Flow listCol, List<String> options, int page,
            Consumer<String> onSelect, IPanelHandler handler) {
        disposeChildren(listCol);
        int start = page * SELECT_PAGE_SIZE;
        int end = Math.min(start + SELECT_PAGE_SIZE, options.size());
        for (int i = start; i < end; i++) {
            final String opt = options.get(i);
            listCol.child(new ButtonWidget<>()
                    .fullWidth().height(BTN_H).margin(0, BTN_GAP / 2)
                    .overlay(IKey.str(opt))
                    .onMousePressed(b -> {
                        onSelect.accept(opt);
                        handler.closePanel();
                        return true;
                    }));
        }
    }

    private static String categoryTitleKey() {
        switch (currentCategory) {
            case CAT_SINGLE_GENERATOR:
                return "ivgtmb.gui.cat.single_generator";
            case CAT_MULTI_MACHINE:
                return "ivgtmb.gui.cat.multiblock_machine";
            case CAT_MULTI_GENERATOR:
                return "ivgtmb.gui.cat.multiblock_generator";
            case CAT_SINGLE_MACHINE:
            default:
                return "ivgtmb.gui.cat.single_machine";
        }
    }

    private static File getIVGTMBDir() {
        File minecraftHome = (File) FMLInjectionData.data()[6];
        return new File(minecraftHome, "groovy" + File.separator + "IVGTMB");
    }

    private static List<String> listHistoryMachines() {
        List<String> ids = new ArrayList<>();
        File[] files = getIVGTMBDir().listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    ids.add(f.getName());
                }
            }
        }
        return ids;
    }

    private static void saveMachineData() {
        try {
            File dir = getIVGTMBDir();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            MachineData data = new MachineData();
            data.machineId = machineId;
            data.machineMeta = machineMeta;
            data.structX = structX;
            data.structY = structY;
            data.structZ = structZ;
            String[][][] gridData = new String[structZ][structY][structX];
            for (int z = 0; z < structZ; z++) {
                for (int y = 0; y < structY; y++) {
                    for (int x = 0; x < structX; x++) {
                        gridData[z][y][x] = grid[z][y][x] == 0 ? "" : String.valueOf(grid[z][y][x]);
                    }
                }
            }
            data.grid = gridData;
            data.letterMap = MachineData.charsToMap(letterMap);
            data.frontOverlay = RendererOptions.overlayAt(frontOverlayIndex);
            data.baseTexture = RendererOptions.baseTextureAt(baseTextureIndex);
            File file = new File(dir, machineId.isEmpty() ? "custom_machine" : machineId);
            Files.write(file.toPath(), data.toJson().getBytes(StandardCharsets.UTF_8));
            IVGTMB.LOGGER.info("Saved machine data to {}", file.getAbsolutePath());
        } catch (IOException e) {
            IVGTMB.LOGGER.error("Failed to save machine data", e);
        }
    }

    private void readMachine(String id) {
        if (id == null) {
            return;
        }
        File file = new File(getIVGTMBDir(), id);
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            MachineData data = MachineData.fromJson(json);
            machineId = data.machineId;
            machineMeta = data.machineMeta;
            structX = data.structX;
            structY = data.structY;
            structZ = data.structZ;
            if (data.grid != null && data.grid.length > 0 && data.grid[0].length > 0 && data.grid[0][0].length > 0) {
                int gz = data.grid.length;
                int gy = data.grid[0].length;
                int gx = data.grid[0][0].length;
                char[][][] g = new char[gz][gy][gx];
                for (int z = 0; z < gz; z++) {
                    for (int y = 0; y < gy; y++) {
                        for (int x = 0; x < gx; x++) {
                            String s = data.grid[z][y][x];
                            g[z][y][x] = (s == null || s.isEmpty()) ? 0 : s.charAt(0);
                        }
                    }
                }
                grid = g;
            } else {
                grid = new char[structZ][structY][structX];
            }
            letterMap = MachineData.stringsToMap(data.letterMap);
            int oi = RendererOptions.overlays.indexOf(data.frontOverlay);
            frontOverlayIndex = oi >= 0 ? oi : 0;
            int bi = RendererOptions.baseTextures.indexOf(data.baseTexture);
            baseTextureIndex = bi >= 0 ? bi : 0;
            pageIndex = 0;
            letterPageIndex = 0;
            rightMode = MODE_STRUCTURE;
            refreshLayout();
        } catch (IOException e) {
            IVGTMB.LOGGER.error("Failed to read machine data from {}", file.getAbsolutePath(), e);
        }
    }

    private Widget<?> buildHistoryList() {
        ScrollWidget scroll = new ScrollWidget();
        scroll.background(new Rectangle().color(PANEL_BG_COLOR));
        scroll.fullWidth().expanded();
        Flow listCol = Flow.column().fullWidth().padding(2);
        for (String id : listHistoryMachines()) {
            listCol.child(new ButtonWidget<>()
                    .fullWidth().height(BTN_H).margin(0, BTN_GAP / 2)
                    .overlay(IKey.str(id))
                    .onMousePressed(b -> {
                        selectedHistoryFile = id;
                        updateRightContent();
                        return true;
                    }));
        }
        if (listCol.getChildren().isEmpty()) {
            listCol.child(IKey.lang("ivgtmb.gui.history_empty").asWidget()
                    .fullWidth().height(BTN_H).scale(0.7f));
        }
        scroll.child(listCol);
        return scroll;
    }

    private Widget<?> buildHistoryRead() {
        Flow col = Flow.column().fullWidth().padding(4)
                .background(new Rectangle().color(PANEL_BG_COLOR));
        col.child(new TextWidget<>(IKey.lang("ivgtmb.gui.history")).fullWidth().scale(0.9f));
        col.child(IKey.str(selectedHistoryFile == null ? "-" : selectedHistoryFile)
                .asWidget().fullWidth().scale(0.7f).marginTop(4));
        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginTop(4)
                .overlay(IKey.lang("ivgtmb.gui.read"))
                .onMousePressed(b -> {
                    readMachine(selectedHistoryFile);
                    return true;
                }));
        return col;
    }

    private Widget<?> buildNewMachineContent() {
        if (isSingleBlock()) {
            return buildSingleBlockMachineContent();
        }
        return buildMultiblockMachineContent();
    }

    /**
     * New machine form for single-block machines / generators: no structure editor,
     * no front overlay (SimpleMachine uses the base texture as its renderer).
     */
    private Widget<?> buildSingleBlockMachineContent() {
        Flow col = Flow.column().fullWidth().padding(4);
        col.child(new TextWidget<>(IKey.lang("ivgtmb.gui.new_machine"))
                .fullWidth().scale(0.9f));
        col.child(new Rectangle().color(DIVIDER_COLOR).asWidget()
                .fullWidth().height(1).marginTop(2).marginBottom(2));

        col.child(IKey.lang("ivgtmb.gui.machine_id").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> machineId, v -> machineId = v))
                .setMaxLength(64)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(IKey.lang("ivgtmb.gui.machine_meta").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> machineMeta, v -> machineMeta = v))
                .setNumbers().setMaxLength(8)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(IKey.lang("ivgtmb.gui.rm_select_for_machine").asWidget().fullWidth().scale(0.7f));
        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginBottom(4)
                .overlay(IKey.str(machineRecipeMap == null || machineRecipeMap.isEmpty() ? "(none)" : machineRecipeMap))
                .onMousePressed(b -> {
                    List<String> names = new ArrayList<>();
                    for (RecipeMap<?> rm : RecipeMap.getRecipeMaps()) {
                        names.add(rm.getUnlocalizedName());
                    }
                    if (!rmId.isEmpty() && !names.contains(rmId)) {
                        names.add(0, rmId);
                    }
                    openSelectionPanel(IKey.lang("ivgtmb.gui.rm_select_for_machine").get(),
                            names, sel -> {
                                machineRecipeMap = sel;
                                updateRightContent();
                            });
                    return true;
                }));

        col.child(IKey.lang("ivgtmb.gui.tier").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> machineTier, v -> machineTier = v))
                .setNumbers().setMaxLength(4)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(IKey.lang("ivgtmb.gui.front_overlay").asWidget().fullWidth().scale(0.7f));
        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginBottom(4)
                .overlay(IKey.str(RendererOptions.overlayAt(frontOverlayIndex)))
                .onMousePressed(b -> {
                    openSelectionPanel(IKey.lang("ivgtmb.gui.front_overlay").get(),
                            RendererOptions.overlays, sel -> {
                                int idx = RendererOptions.overlays.indexOf(sel);
                                if (idx >= 0) {
                                    frontOverlayIndex = idx;
                                }
                                updateRightContent();
                            });
                    return true;
                }));

        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginTop(4)
                .overlay(IKey.lang("ivgtmb.gui.update_render_single"))
                .onMousePressed(b -> {
                    if (previewWidget != null) {
                        previewWidget.setSingleBlockPreview(
                                RendererOptions.overlayRendererAt(frontOverlayIndex), tier());
                    }
                    return true;
                }));

        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginTop(4)
                .overlay(IKey.lang("ivgtmb.gui.clear"))
                .onMousePressed(b -> {
                    clearMachineState();
                    updateRightContent();
                    if (previewWidget != null) {
                        previewWidget.setMachine(getCurrentMachine());
                    }
                    return true;
                }));
        return col;
    }

    private Widget<?> buildMultiblockMachineContent() {
        Flow col = Flow.column().fullWidth().padding(4);

        col.child(new TextWidget<>(IKey.lang("ivgtmb.gui.new_machine"))
                .fullWidth().scale(0.9f));
        col.child(new Rectangle().color(DIVIDER_COLOR).asWidget()
                .fullWidth().height(1).marginTop(2).marginBottom(2));

        col.child(IKey.lang("ivgtmb.gui.machine_id").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> machineId, v -> machineId = v))
                .setMaxLength(64)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(IKey.lang("ivgtmb.gui.machine_meta").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> machineMeta, v -> machineMeta = v))
                .setNumbers()
                .setMaxLength(8)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(IKey.lang("ivgtmb.gui.rm_select_for_machine").asWidget().fullWidth().scale(0.7f));
        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginBottom(4)
                .overlay(IKey.str(machineRecipeMap == null || machineRecipeMap.isEmpty() ? "(none)" : machineRecipeMap))
                .onMousePressed(b -> {
                    List<String> names = new ArrayList<>();
                    for (RecipeMap<?> rm : RecipeMap.getRecipeMaps()) {
                        names.add(rm.getUnlocalizedName());
                    }
                    if (!rmId.isEmpty() && !names.contains(rmId)) {
                        names.add(0, rmId);
                    }
                    openSelectionPanel(IKey.lang("ivgtmb.gui.rm_select_for_machine").get(),
                            names, sel -> {
                                machineRecipeMap = sel;
                                updateRightContent();
                            });
                    return true;
                }));

        col.child(IKey.lang("ivgtmb.gui.parallel").asWidget().fullWidth().scale(0.7f));
        col.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> machineParallel, v -> machineParallel = v))
                .setNumbers().setMaxLength(4)
                .fullWidth().height(FIELD_H).marginBottom(4));

        col.child(IKey.lang("ivgtmb.gui.front_overlay").asWidget().fullWidth().scale(0.7f));
        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginBottom(4)
                .overlay(IKey.str(RendererOptions.overlayAt(frontOverlayIndex)))
                .onMousePressed(b -> {
                    openSelectionPanel(IKey.lang("ivgtmb.gui.front_overlay").get(),
                            RendererOptions.overlays, sel -> {
                                int idx = RendererOptions.overlays.indexOf(sel);
                                if (idx >= 0) {
                                    frontOverlayIndex = idx;
                                }
                                updateRightContent();
                            });
                    return true;
                }));

        col.child(IKey.lang("ivgtmb.gui.base_texture").asWidget().fullWidth().scale(0.7f));
        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginBottom(4)
                .overlay(IKey.str(RendererOptions.baseTextureAt(baseTextureIndex)))
                .onMousePressed(b -> {
                    openSelectionPanel(IKey.lang("ivgtmb.gui.base_texture").get(),
                            RendererOptions.baseTextures, sel -> {
                                int idx = RendererOptions.baseTextures.indexOf(sel);
                                if (idx >= 0) {
                                    baseTextureIndex = idx;
                                }
                                updateRightContent();
                            });
                    return true;
                }));

        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H)
                .overlay(IKey.lang("ivgtmb.gui.structure_button"))
                .onMousePressed(b -> {
                    modeStack.push(rightMode);
                    rightMode = MODE_STRUCTURE;
                    updateRightContent();
                    if (previewWidget != null) {
                        previewWidget.setCustomGrid(grid, letterMap,
                                RendererOptions.baseTextureRendererAt(baseTextureIndex),
                                RendererOptions.overlayRendererAt(frontOverlayIndex));
                    }
                    return true;
                }));

        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginTop(4)
                .overlay(IKey.lang("ivgtmb.gui.update_render"))
                .onMousePressed(b -> {
                    if (previewWidget != null) {
                        previewWidget.setCustomGrid(grid, letterMap,
                                RendererOptions.baseTextureRendererAt(baseTextureIndex),
                                RendererOptions.overlayRendererAt(frontOverlayIndex));
                    }
                    return true;
                }));

        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginTop(4)
                .overlay(IKey.lang("ivgtmb.gui.clear"))
                .onMousePressed(b -> {
                    clearMachineState();
                    updateRightContent();
                    if (previewWidget != null) {
                        previewWidget.setMachine(getCurrentMachine());
                    }
                    return true;
                }));
        return col;
    }

    private Widget<?> buildStructureContent() {
        Flow col = Flow.column().fullWidth().padding(4);

        col.child(new TextWidget<>(IKey.lang("ivgtmb.gui.structure_title"))
                .fullWidth().scale(0.9f));
        col.child(new Rectangle().color(DIVIDER_COLOR).asWidget()
                .fullWidth().height(1).marginTop(2).marginBottom(2));

        // 1. x/y/z inputs
        col.child(IKey.lang("ivgtmb.gui.structure_xyz").asWidget().fullWidth().scale(0.7f));
        Flow xyzRow = Flow.row().fullWidth().height(FIELD_H).marginBottom(4);
        xyzRow.child(IKey.str("x").asWidget().height(FIELD_H));
        xyzRow.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> String.valueOf(structX), v -> structX = parseInt(v, 3)))
                .setNumbers(1, 16).width(24).height(FIELD_H).marginRight(2));
        xyzRow.child(IKey.str("y").asWidget().height(FIELD_H));
        xyzRow.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> String.valueOf(structY), v -> structY = parseInt(v, 3)))
                .setNumbers(1, 16).width(24).height(FIELD_H).marginRight(2));
        xyzRow.child(IKey.str("z").asWidget().height(FIELD_H));
        xyzRow.child(new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> String.valueOf(structZ), v -> structZ = parseInt(v, 3)))
                .setNumbers(1, 16).width(24).height(FIELD_H));
        col.child(xyzRow);

        // update button: rebuild grid from x/y/z
        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginBottom(4)
                .overlay(IKey.lang("ivgtmb.gui.update"))
                .onMousePressed(b -> {
                    char[][][] old = grid;
                    grid = new char[structZ][structY][structX];
                    copyGrid(old, grid);
                    updateRightContent();
                    if (previewWidget != null) {
                        previewWidget.setCustomGrid(grid, letterMap);
                    }
                    return true;
                }));

        // 2. layered grid with letter inputs (paginated, 2 layers per page)
        int layersPerPage = 2;
        int totalPages = Math.max(1, (structZ + layersPerPage - 1) / layersPerPage);
        int startZ = pageIndex * layersPerPage;
        int endZ = Math.min(structZ, startZ + layersPerPage);
        for (int z = startZ; z < endZ; z++) {
            final int fz = z;
            col.child(IKey.str("第" + (z + 1) + "层").asWidget()
                    .fullWidth().scale(0.7f).marginTop(2));
            for (int y = 0; y < structY; y++) {
                final int fy = y;
                Flow row = Flow.row().fullWidth().height(FIELD_H);
                for (int x = 0; x < structX; x++) {
                    final int fx = x;
                    row.child(new TextFieldWidget()
                            .value(new StringValue.Dynamic(
                                    () -> String.valueOf(grid[fz][fy][fx]),
                                    v -> grid[fz][fy][fx] = v.isEmpty() ? 0 : v.charAt(0)))
                            .setMaxLength(1)
                            .width(FIELD_H).height(FIELD_H));
                }
                col.child(row);
            }
        }

        // pagination for grid layers
        Flow pageRow = Flow.row().fullWidth().height(BTN_H).marginTop(4);
        pageRow.child(new ButtonWidget<>()
                .sizeRel(0.4f, 1f).marginRight(2)
                .overlay(IKey.str("<"))
                .onMousePressed(b -> {
                    pageIndex = Math.max(0, pageIndex - 1);
                    updateRightContent();
                    return true;
                }));
        pageRow.child(IKey.str((pageIndex + 1) + "/" + totalPages).asWidget().sizeRel(0.2f, 1f));
        pageRow.child(new ButtonWidget<>()
                .sizeRel(0.4f, 1f).marginLeft(2)
                .overlay(IKey.str(">"))
                .onMousePressed(b -> {
                    pageIndex = Math.min(totalPages - 1, pageIndex + 1);
                    updateRightContent();
                    return true;
                }));
        col.child(pageRow);

        // 4. letter meaning inputs (paginated, 4 per page)
        col.child(IKey.lang("ivgtmb.gui.letter_meaning").asWidget()
                .fullWidth().scale(0.7f).marginTop(4));
        List<Map.Entry<Character, String>> letterEntries = new ArrayList<>(letterMap.entrySet());
        int lettersPerPage = 3;
        int letterTotalPages = Math.max(1, (letterEntries.size() + lettersPerPage - 1) / lettersPerPage);
        int letterStart = letterPageIndex * lettersPerPage;
        int letterEnd = Math.min(letterEntries.size(), letterStart + lettersPerPage);
        for (int i = letterStart; i < letterEnd; i++) {
            Map.Entry<Character, String> entry = letterEntries.get(i);
            Character letter = entry.getKey();
            Flow row = Flow.row().fullWidth().height(FIELD_H);
            row.child(IKey.str(String.valueOf(letter)).asWidget().width(12).height(FIELD_H));
            if (letter == 'S') {
                // Main block: fixed, no input needed.
                row.child(IKey.lang("ivgtmb.gui.main_block").asWidget()
                        .fullWidth().height(FIELD_H));
            } else {
                row.child(new TextFieldWidget()
                        .value(new StringValue.Dynamic(
                                () -> letterMap.getOrDefault(letter, ""),
                                v -> letterMap.put(letter, v)))
                        .setMaxLength(65536)
                        .fullWidth().height(FIELD_H));
            }
            col.child(row);
        }

        // letter map pagination
        Flow letterPageRow = Flow.row().fullWidth().height(BTN_H).marginTop(2);
        letterPageRow.child(new ButtonWidget<>()
                .sizeRel(0.4f, 1f).marginRight(2)
                .overlay(IKey.str("<"))
                .onMousePressed(b -> {
                    letterPageIndex = Math.max(0, letterPageIndex - 1);
                    updateRightContent();
                    return true;
                }));
        letterPageRow.child(IKey.str((letterPageIndex + 1) + "/" + letterTotalPages).asWidget().sizeRel(0.2f, 1f));
        letterPageRow.child(new ButtonWidget<>()
                .sizeRel(0.4f, 1f).marginLeft(2)
                .overlay(IKey.str(">"))
                .onMousePressed(b -> {
                    letterPageIndex = Math.min(letterTotalPages - 1, letterPageIndex + 1);
                    updateRightContent();
                    return true;
                }));
        col.child(letterPageRow);

        // new block button
        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginTop(2)
                .overlay(IKey.lang("ivgtmb.gui.new_block"))
                .onMousePressed(b -> {
                    char next = nextLetter();
                    if (next != '?') {
                        letterMap.put(next, "");
                        updateRightContent();
                        if (previewWidget != null) {
                            previewWidget.setCustomGrid(grid, letterMap);
                        }
                    }
                    return true;
                }));

        // update button: sync letter map
        col.child(new ButtonWidget<>()
                .fullWidth().height(BTN_H).marginTop(2)
                .overlay(IKey.lang("ivgtmb.gui.update"))
                .onMousePressed(b -> {
                    updateRightContent();
                    if (previewWidget != null) {
                        previewWidget.setCustomGrid(grid, letterMap);
                    }
                    return true;
                }));
        return col;
    }

    private static void copyGrid(char[][][] from, char[][][] to) {
        for (int z = 0; z < Math.min(from.length, to.length); z++) {
            for (int y = 0; y < Math.min(from[z].length, to[z].length); y++) {
                for (int x = 0; x < Math.min(from[z][y].length, to[z][y].length); x++) {
                    to[z][y][x] = from[z][y][x];
                }
            }
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Map<Character, String> createDefaultLetterMap() {
        Map<Character, String> map = new LinkedHashMap<>();
        map.put('S', "");
        map.put('R', "");
        return map;
    }

    private static char nextLetter() {
        for (char c = 'A'; c <= 'Z'; c++) {
            if (!letterMap.containsKey(c)) {
                return c;
            }
        }
        return '?';
    }

    private static String toClassName(String id) {
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : id.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') {
                upper = true;
                continue;
            }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.length() == 0 ? "CustomMachine" : sb.toString();
    }

    private void addCategoryButton(ModularPanel panel, int cat, String langKey) {
        panel.child(new ButtonWidget<>()
                .pos(CAT_X, CAT_START_Y + cat * (CAT_H + CAT_GAP))
                .size(CAT_W, CAT_H)
                .overlay(IKey.lang(langKey))
                .onMousePressed(b -> {
                    currentCategory = cat;
                    rightMode = MODE_DEFAULT;
                    refreshLayout();
                    return true;
                }));
    }

    private void addLeftButton(ModularPanel panel, int y, String langKey, IGuiAction.MousePressed action) {
        panel.child(new ButtonWidget<>()
                .pos(CAT_X, y)
                .size(CAT_W, CAT_H)
                .overlay(IKey.lang(langKey))
                .onMousePressed(action));
    }

    // ===== Machine enumeration & categorisation =====

    private static void loadMachinesIfNeeded() {
        if (machinesLoaded) {
            return;
        }
        cachedMachines = new ArrayList<>();
        for (ResourceLocation id : GregTechAPI.MTE_REGISTRY.getKeys()) {
            MetaTileEntity mte = GregTechAPI.MTE_REGISTRY.getObject(id);
            if (mte != null && getCategory(mte) != null) {
                cachedMachines.add(mte);
            }
        }
        machinesLoaded = true;
    }

    private static MetaTileEntity getCurrentMachine() {
        List<MetaTileEntity> machines = new ArrayList<>();
        for (MetaTileEntity mte : cachedMachines) {
            if (getCategory(mte).ordinal() == currentCategory) {
                machines.add(mte);
            }
        }
        return machines.isEmpty() ? null : machines.get(0);
    }

    private static Category getCategory(MetaTileEntity mte) {
        if (mte instanceof MultiblockControllerBase) {
            return (mte instanceof FuelMultiblockController)
                    ? Category.MULTI_GENERATOR
                    : Category.MULTI_MACHINE;
        }
        if (mte instanceof SimpleGeneratorMetaTileEntity) {
            return Category.SINGLE_GENERATOR;
        }
        if (mte instanceof SimpleMachineMetaTileEntity
                || mte instanceof WorkableTieredMetaTileEntity
                || mte instanceof TieredMetaTileEntity
                || mte instanceof SteamMetaTileEntity) {
            return Category.SINGLE_MACHINE;
        }
        return null;
    }

    private static String displayName(MetaTileEntity mte) {
        String name = mte.getMetaFullName();
        if (name == null || name.isEmpty()) {
            name = mte.metaTileEntityId.toString();
        }
        return name;
    }

    private enum Category {
        SINGLE_MACHINE,
        SINGLE_GENERATOR,
        MULTI_MACHINE,
        MULTI_GENERATOR
    }

    // ===== Export =====

    private static void exportGroovyFile() {
        File minecraftHome = (File) FMLInjectionData.data()[6];
        exportRecipeMapFile(minecraftHome);
        if (MODE_NEW_MACHINE.equals(rightMode) || MODE_STRUCTURE.equals(rightMode)) {
            saveMachineData();
            exportMachineClassFile(minecraftHome);
            exportMachineRegistration(minecraftHome);
        } else {
            exportCategoryNotice(minecraftHome);
        }
    }

    /**
     * Writes the machine class (package {@code classes}) into
     * {@code groovy/classes/<machineId>.groovy}, named after the machine id.
     */
    private static void exportMachineClassFile(File minecraftHome) {
        String id = machineId.isEmpty() ? "custom_machine" : machineId;
        File file = new File(minecraftHome,
                "groovy" + File.separator + "classes" + File.separator + id + ".groovy");
        writeFile(file, buildCustomMachineGroovy(), false);
    }

    /**
     * Appends the main-block registration call into
     * {@code groovy/postInit/ivgtmb_machine.groovy}.
     */
    private static void exportMachineRegistration(File minecraftHome) {
        String packId = readPackId(minecraftHome);
        String id = machineId.isEmpty() ? "custom_machine" : machineId;
        String className = "classes.MetaTileEntity" + toClassName(id);
        int meta;
        try {
            meta = Integer.parseInt(machineMeta);
        } catch (NumberFormatException e) {
            meta = 32000;
        }
        File file = new File(minecraftHome,
                "groovy" + File.separator + "postInit" + File.separator + "ivgtmb_machine.groovy");
        boolean newFile = !file.exists();
        StringBuilder sb = new StringBuilder();
        if (newFile) {
            sb.append("// Generated by In-game Visual GT Machine Builder\n");
            sb.append("import gregtech.api.GregTechAPI\n");
            sb.append("import gregtech.api.recipes.RecipeMap\n");
            sb.append("import gregtech.client.renderer.texture.Textures\n");
            sb.append("import net.minecraft.util.ResourceLocation\n");
        }
        // Each registration lives in its own block so appends never clash on
        // local variable names; classes are referenced fully-qualified.
        sb.append("{\n");
        sb.append("    def registry = GregTechAPI.mteManager.getRegistry('").append(packId).append("')\n");
        sb.append("    if (registry == null) { registry = GregTechAPI.mteManager.createRegistry('").append(packId)
                .append("') }\n");
        if (machineRecipeMap != null && !machineRecipeMap.isEmpty()) {
            sb.append("    def rm = RecipeMap.getByName('").append(machineRecipeMap).append("')\n");
        } else {
            sb.append("    def rm = null\n");
        }
        sb.append("    registry.register(").append(meta)
                .append(", new ResourceLocation('").append(packId).append("', '").append(id).append("'), new ")
                .append(className).append("(new ResourceLocation('").append(packId).append("', '").append(id)
                .append("')");
        if (isSingleBlock()) {
            sb.append(", rm, Textures.").append(RendererOptions.overlayAt(frontOverlayIndex))
                    .append(", ").append(tier());
        } else {
            sb.append(", rm");
        }
        sb.append("))\n");
        sb.append("}\n\n");
        writeFile(file, sb.toString(), !newFile);
    }

    /**
     * Appends recipe map creation / modification into
     * {@code groovy/postInit/ivgtmb_recipemap.groovy}.
     */
    private static void exportRecipeMapFile(File minecraftHome) {
        File file = new File(minecraftHome,
                "groovy" + File.separator + "postInit" + File.separator + "ivgtmb_recipemap.groovy");
        boolean newFile = !file.exists();
        StringBuilder sb = new StringBuilder();
        if (newFile) {
            sb.append("// Generated by In-game Visual GT Machine Builder\n");
            sb.append("import gregtech.api.recipes.RecipeMap\n");
            sb.append("import gregtech.api.recipes.RecipeMapBuilder\n");
            sb.append("import gregtech.api.recipes.builders.SimpleRecipeBuilder\n");
        }
        if (rmId != null && !rmId.isEmpty()) {
            sb.append("{\n");
            sb.append("    new RecipeMapBuilder<>('").append(rmId).append("', new SimpleRecipeBuilder())\n");
            sb.append("            .itemInputs(").append(parseInt(rmItemInputs, 1)).append(')')
                    .append(".itemOutputs(").append(parseInt(rmItemOutputs, 1)).append(')')
                    .append(".fluidInputs(").append(parseInt(rmFluidInputs, 0)).append(')')
                    .append(".fluidOutputs(").append(parseInt(rmFluidOutputs, 0)).append(')')
                    .append("\n            .build()\n");
            sb.append("}\n\n");
        }
        if (rmModifyTarget != null && !rmModifyTarget.isEmpty()) {
            sb.append("{\n");
            sb.append("    // Modify existing recipe map '").append(rmModifyTarget).append("'\n");
            sb.append("    new RecipeMapBuilder<>('").append(rmModifyTarget).append("', new SimpleRecipeBuilder())\n");
            sb.append("            .itemInputs(").append(parseInt(rmModifyItemInputs, 1)).append(')')
                    .append(".itemOutputs(").append(parseInt(rmModifyItemOutputs, 1)).append(')')
                    .append(".fluidInputs(").append(parseInt(rmModifyFluidInputs, 0)).append(')')
                    .append(".fluidOutputs(").append(parseInt(rmModifyFluidOutputs, 0)).append(')')
                    .append("\n            .build()\n");
            sb.append("}\n\n");
        }
        if ((rmId != null && !rmId.isEmpty()) || (rmModifyTarget != null && !rmModifyTarget.isEmpty())) {
            writeFile(file, sb.toString(), !newFile);
        }
    }

    private static void exportCategoryNotice(File minecraftHome) {
        MetaTileEntity mte = getCurrentMachine();
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated by In-game Visual GT Machine Builder\n");
        if (mte == null) {
            sb.append("// No machine selected in this category\n");
        } else {
            sb.append("// Machine: ").append(displayName(mte))
                    .append(" (").append(mte.metaTileEntityId).append(")\n");
            sb.append("// This machine is already registered by GTCEu. Use the \"New Machine\"\n");
            sb.append("// page to build and export a custom multiblock machine.\n");
        }
        sb.append('\n');
        File file = new File(minecraftHome,
                "groovy" + File.separator + "postInit" + File.separator + "ivgtmb_machine.groovy");
        writeFile(file, sb.toString(), true);
    }

    private static void writeFile(File file, String content, boolean append) {
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            if (append) {
                Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            IVGTMB.LOGGER.info("Exported file to {}", file.getAbsolutePath());
        } catch (IOException e) {
            IVGTMB.LOGGER.error("Failed to export file to {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * Reads the GroovyScript pack id from {@code groovy/runConfig.json}, falling
     * back to {@code ivgtmb} when it cannot be read.
     */
    private static String readPackId(File minecraftHome) {
        try {
            File runConfig = new File(minecraftHome, "groovy" + File.separator + "runConfig.json");
            if (runConfig.exists()) {
                JsonObject obj = new JsonParser().parse(
                        new String(Files.readAllBytes(runConfig.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
                if (obj.has("packId") && !obj.get("packId").getAsString().isEmpty()) {
                    return obj.get("packId").getAsString();
                }
            }
        } catch (Exception e) {
            IVGTMB.LOGGER.error("Failed to read pack id from runConfig", e);
        }
        return "ivgtmb";
    }

    private static int parallel() {
        try {
            int p = Integer.parseInt(machineParallel.trim());
            return p > 0 ? p : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static int tier() {
        try {
            int t = Integer.parseInt(machineTier.trim());
            return t >= 0 ? t : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Generates a complete, runnable Groovy class that extends
     * {@code RecipeMapMultiblockController} and describes the custom multiblock
     * machine (structure, base texture, front overlay, recipe map and parallel).
     */
    private static boolean isSingleBlock() {
        return currentCategory == CAT_SINGLE_MACHINE || currentCategory == CAT_SINGLE_GENERATOR;
    }

    private static String buildCustomMachineGroovy() {
        String id = machineId.isEmpty() ? "custom_machine" : machineId;
        String className = "MetaTileEntity" + toClassName(id);
        if (isSingleBlock()) {
            return buildSingleBlockMachineGroovy(id, className);
        }
        boolean hasParallel = parallel() > 1;
        StringBuilder sb = new StringBuilder();
        sb.append("package classes\n\n");
        sb.append("import gregtech.api.capability.impl.MultiblockRecipeLogic\n");
        sb.append("import gregtech.api.metatileentity.MetaTileEntity\n");
        sb.append("import gregtech.api.metatileentity.interfaces.IGregTechTileEntity\n");
        sb.append("import gregtech.api.metatileentity.multiblock.IMultiblockPart\n");
        sb.append("import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController\n");
        sb.append("import gregtech.api.pattern.BlockPattern\n");
        sb.append("import gregtech.api.pattern.FactoryBlockPattern\n");
        sb.append("import gregtech.api.pattern.TraceabilityPredicate\n");
        sb.append("import gregtech.api.recipes.RecipeMap\n");
        sb.append("import gregtech.client.renderer.ICubeRenderer\n");
        sb.append("import gregtech.client.renderer.texture.Textures\n");
        sb.append("import net.minecraft.block.state.IBlockState\n");
        sb.append("import net.minecraft.util.ResourceLocation\n\n");
        sb.append("// Machine id: ").append(id).append('\n');
        sb.append("// Machine meta: ").append(machineMeta).append('\n');
        sb.append("// Structure: ").append(structX).append('x').append(structY).append('x')
                .append(structZ).append('\n');
        sb.append("// Recipe map: ").append(machineRecipeMap.isEmpty() ? "(none)" : machineRecipeMap).append('\n');
        sb.append("// Parallel: ").append(machineParallel).append('\n');
        sb.append("// Front overlay: ").append(RendererOptions.overlayAt(frontOverlayIndex)).append('\n');
        sb.append("// Base texture: ").append(RendererOptions.baseTextureAt(baseTextureIndex)).append('\n');
        sb.append("class ").append(className).append(" extends RecipeMapMultiblockController {\n\n");
        sb.append("    ").append(className).append("(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap) {\n");
        sb.append("        super(metaTileEntityId, recipeMap)\n");
        if (hasParallel) {
            sb.append("        this.recipeMapWorkable = new ").append(className).append("RecipeLogic(this)\n");
        }
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {\n");
        sb.append("        return new ").append(className).append("(metaTileEntityId, recipeMap)\n");
        sb.append("    }\n\n");
        sb.append(buildStructurePatternGroovy());
        sb.append("    @Override\n");
        sb.append("    ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {\n");
        sb.append("        return Textures.").append(RendererOptions.baseTextureAt(baseTextureIndex)).append('\n');
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    protected ICubeRenderer getFrontOverlay() {\n");
        sb.append("        return Textures.").append(RendererOptions.overlayAt(frontOverlayIndex)).append('\n');
        sb.append("    }\n");
        if (hasParallel) {
            sb.append("\n    private class ").append(className).append("RecipeLogic extends MultiblockRecipeLogic {\n");
            sb.append("        ").append(className).append("RecipeLogic(RecipeMapMultiblockController tileEntity) {\n");
            sb.append("            super(tileEntity)\n");
            sb.append("        }\n\n");
            sb.append("        @Override\n");
            sb.append("        int getParallelLimit() {\n");
            sb.append("            return ").append(parallel()).append('\n');
            sb.append("        }\n");
            sb.append("    }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Single-block machines extend {@code SimpleMachineMetaTileEntity} (or
     * {@code SimpleGeneratorMetaTileEntity} for generators), matching the format
     * used by GroovyScript-based custom machines (see the susyhyw example files).
     */
    private static String buildSingleBlockMachineGroovy(String id, String className) {
        boolean generator = currentCategory == CAT_SINGLE_GENERATOR;
        StringBuilder sb = new StringBuilder();
        sb.append("package classes\n\n");
        sb.append("import gregtech.api.metatileentity.MetaTileEntity\n");
        sb.append("import gregtech.api.metatileentity.interfaces.IGregTechTileEntity\n");
        sb.append("import gregtech.api.recipes.RecipeMap\n");
        sb.append("import gregtech.client.renderer.ICubeRenderer\n");
        sb.append("import net.minecraft.util.ResourceLocation\n");
        if (generator) {
            sb.append("import gregtech.api.metatileentity.SimpleGeneratorMetaTileEntity\n");
            sb.append("import gregtech.api.util.GTUtility\n");
        } else {
            sb.append("import gregtech.api.metatileentity.SimpleMachineMetaTileEntity\n");
        }
        sb.append('\n');
        sb.append("// Machine id: ").append(id).append('\n');
        sb.append("// Machine meta: ").append(machineMeta).append('\n');
        sb.append("// Recipe map: ").append(machineRecipeMap.isEmpty() ? "(none)" : machineRecipeMap).append('\n');
        sb.append("// Front overlay: ").append(RendererOptions.overlayAt(frontOverlayIndex)).append('\n');
        sb.append("// Tier: ").append(machineTier).append('\n');
        sb.append("class ").append(className).append(" extends ")
                .append(generator ? "SimpleGeneratorMetaTileEntity" : "SimpleMachineMetaTileEntity").append(" {\n\n");
        sb.append("    ").append(className).append("(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap, ")
                .append("ICubeRenderer renderer, int tier) {\n");
        if (generator) {
            sb.append(
                    "        super(metaTileEntityId, recipeMap, renderer, tier, GTUtility.defaultTankSizeFunction)\n");
        } else {
            sb.append("        super(metaTileEntityId, recipeMap, renderer, tier, true)\n");
        }
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {\n");
        sb.append("        return new ").append(className)
                .append("(metaTileEntityId, workable.getRecipeMap(), renderer, getTier())\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Generates the {@code createStructurePattern()} body from the letter grid and
     * the letter→block map. Non-{@code S} letters map to predicates built with
     * {@code states(blockstate(...))} (or {@code autoAbilities()} when the
     * description is "auto"); letters used in the grid but left unset fall back to
     * {@code any()}. The {@code R} letter additionally allows any hatch/ability,
     * and spaces in the grid resolve to air.
     */
    private static String buildStructurePatternGroovy() {
        StringBuilder sb = new StringBuilder();
        sb.append("    @Override\n");
        sb.append("    protected BlockPattern createStructurePattern() {\n");
        Map<Character, String> named = new LinkedHashMap<>();
        for (Map.Entry<Character, String> e : letterMap.entrySet()) {
            char c = e.getKey();
            if (c == 'S') {
                continue;
            }
            String desc = e.getValue();
            if (desc == null || desc.trim().isEmpty()) {
                continue;
            }
            String var = "p_" + c;
            String trimmed = desc.trim();
            if ("auto".equalsIgnoreCase(trimmed)) {
                sb.append("        TraceabilityPredicate ").append(var).append(" = autoAbilities()\n");
            } else {
                // Resolve the block via its block state, e.g.
                // states(blockstate('minecraft:stone'))
                sb.append("        TraceabilityPredicate ").append(var)
                        .append(" = states(").append(trimmed).append(")\n");
            }
            if (c == 'R') {
                // R = 可替换仓室方块（可被任意输入/输出仓、能源舱替换）
                sb.append("        // ").append(var).append(" = 可替换仓室方块\n");
            }
            named.put(c, var);
        }
        // Letters used in the grid but left without a definition fall back to any().
        for (int z = 0; z < structZ; z++) {
            for (int y = 0; y < structY; y++) {
                for (int x = 0; x < structX; x++) {
                    char c = grid[z][y][x];
                    if (c != 0 && c != ' ' && c != 'S' && !named.containsKey(c)) {
                        String var = "p_" + c;
                        sb.append("        TraceabilityPredicate ").append(var).append(" = any()\n");
                        named.put(c, var);
                    }
                }
            }
        }
        sb.append("        return FactoryBlockPattern.start()\n");
        for (int z = 0; z < structZ; z++) {
            sb.append("                .aisle(");
            for (int y = 0; y < structY; y++) {
                StringBuilder row = new StringBuilder("'");
                for (int x = 0; x < structX; x++) {
                    char c = grid[z][y][x];
                    row.append(c == 0 ? ' ' : c);
                }
                row.append("'");
                sb.append(row);
                if (y < structY - 1) {
                    sb.append(", ");
                }
            }
            sb.append(")\n");
        }
        sb.append("                .where('S' as char, selfPredicate())\n");
        sb.append("                .where(' ' as char, air())\n");
        for (Map.Entry<Character, String> e : named.entrySet()) {
            char c = e.getKey();
            String pred = e.getValue();
            if (c == 'R') {
                // R 可被任意仓室/舱口替换
                sb.append("                .where('R' as char, ").append(pred).append(".or(autoAbilities()))\n");
            } else {
                sb.append("                .where('").append(c).append("' as char, ")
                        .append(pred).append(")\n");
            }
        }
        sb.append("                .build()\n");
        sb.append("    }\n\n");
        return sb.toString();
    }

    private static void refresh() {
        ModularScreen current = ModularScreen.getCurrent();
        if (current != null) {
            current.close();
        }
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (Minecraft.getMinecraft().player != null) {
                ClientGUI.open(new BuilderUI());
            }
        });
    }
}

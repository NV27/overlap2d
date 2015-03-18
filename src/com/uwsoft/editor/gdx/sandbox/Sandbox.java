package com.uwsoft.editor.gdx.sandbox;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.Align;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.uwsoft.editor.controlles.flow.FlowActionEnum;
import com.uwsoft.editor.data.manager.DataManager;
import com.uwsoft.editor.data.manager.SandboxResourceManager;
import com.uwsoft.editor.data.vo.ProjectVO;
import com.uwsoft.editor.gdx.actors.SelectionRectangle;
import com.uwsoft.editor.gdx.mediators.ItemControlMediator;
import com.uwsoft.editor.gdx.mediators.SceneControlMediator;
import com.uwsoft.editor.gdx.stage.SandboxStage;
import com.uwsoft.editor.gdx.ui.dialogs.InputDialog;
import com.uwsoft.editor.renderer.SceneLoader;
import com.uwsoft.editor.renderer.actor.CompositeItem;
import com.uwsoft.editor.renderer.actor.IBaseItem;
import com.uwsoft.editor.renderer.actor.LightActor;
import com.uwsoft.editor.renderer.data.*;
import com.uwsoft.editor.renderer.resources.IResourceRetriever;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by CyberJoe on 3/18/2015.
 */
public class Sandbox {

    public EditingMode editingMode;

    public SceneControlMediator sceneControl;
    public ItemControlMediator itemControl;

    private SandboxStage stage;

    private InputHandler inputHandler;

    HashMap<IBaseItem, SelectionRectangle> currentSelection = new HashMap<IBaseItem, SelectionRectangle>();

    /**
     * this part is to be modified
     */
    private int currTransformType = -1;
    private IBaseItem currTransformHost;
    private boolean isResizing = false;
    private boolean isUsingSelectionTool = true;
    private boolean isItemTouched = false;
    private boolean dirty = false;
    private Vector3 copedItemCameraOffset;
    private IResourceRetriever rm;

    public Sandbox(SandboxStage stage, SceneLoader sceneLoader, Essentials essentials) {

        editingMode = EditingMode.SELECTION;

        sceneControl = new SceneControlMediator(sceneLoader, essentials);
        itemControl = new ItemControlMediator(sceneControl);
        inputHandler = new InputHandler();

        this.stage = stage;
    }

    public void initData(String sceneName) {
        DataManager.getInstance().preloadSceneSpecificData(sceneControl.getEssentials().rm.getSceneVO(sceneName), DataManager.getInstance().curResolution);

        sceneControl.initScene(sceneName);
    }

    public void initSceneView(CompositeItemVO compositeItemVO) {
        sceneControl.initSceneView(compositeItemVO);
    }


    public void selectItemsByLayerName(String name) {
        ArrayList<IBaseItem> itemsArr = new ArrayList<IBaseItem>();
        for (int i = 0; i < sceneControl.getCurrentScene().getItems().size(); i++) {
            if (sceneControl.getCurrentScene().getItems().get(i).getDataVO().layerName.equals(name)) {
                itemsArr.add(sceneControl.getCurrentScene().getItems().get(i));
            }
        }

        setSelections(itemsArr, true);
    }


    public void setSelection(IBaseItem item, boolean removeOthers) {
        if (currentSelection.get(item) != null) return;

        if (removeOthers) clearSelections();

        SelectionRectangle rect = new SelectionRectangle(stage);
        rect.claim(item);
        rect.setMode(editingMode);
        currentSelection.put(item, rect);
        stage.frontUI.addActor(rect);
        rect.show();
        stage.uiStage.itemWasSelected(item);

        if (stage.dropDown != null) {
            stage.dropDown.remove();
            stage.dropDown = null;
        }

        stage.uiStage.getItemsBox().setSelected(currentSelection);

    }

    private void releaseSelection(IBaseItem item) {
        currentSelection.get(item).remove();
        currentSelection.remove(item);

        stage.uiStage.getItemsBox().setSelected(currentSelection);
    }

    private void clearSelections() {
        for (SelectionRectangle value : currentSelection.values()) {
            value.remove();
        }

        currentSelection.clear();
        stage.uiStage.getItemsBox().setSelected(currentSelection);
    }

    private void setSelections(ArrayList<IBaseItem> items, boolean alsoShow) {
        clearSelections();

        for (int i = 0; i < items.size(); i++) {
            setSelection(items.get(i), false);
            if (alsoShow) {
                currentSelection.get(items.get(i)).show();
            }
        }
    }

    private void moveSelectedItemsBy(float x, float y) {
        for (SelectionRectangle selectionRect : currentSelection.values()) {
            itemControl.moveItemBy(selectionRect.getHostAsActor(), x, y);

            selectionRect.setX(selectionRect.getX() + x);
            selectionRect.setY(selectionRect.getY() + y);
        }

        stage.saveSceneCurrentSceneData();
    }

    private void removeCurrentSelectedItems() {
        for (SelectionRectangle selectionRect : currentSelection.values()) {
            itemControl.removeItem(selectionRect.getHostAsActor());
            selectionRect.remove();
        }
        stage.uiStage.getItemsBox().initContent();
        currentSelection.clear();
    }

    public void initSceneView(CompositeItem composite) {

        composite.applyResolution(DataManager.getInstance().curResolution);

        clearSelections();

        mainBox.clear();
        currentScene = composite;
        if (uiStage.getCompositePanel().isRootScene()) {
            rootSceneVO = currentScene.dataVO;
            uiStage.getCompositePanel().updateRootScene(rootSceneVO);
        }

        for (int i = 0; i < currentScene.getItems().size(); i++) {
            inputHandler.initItemListeners(currentScene.getItems().get(i));
        }

        mainBox.addActor(currentScene);
        currentScene.setX(0);
        currentScene.setY(0);

        uiStage.getLayerPanel().initContent();

        if (currentSceneVo.ambientColor == null) {
            currentSceneVo.ambientColor = new float[4];
            currentSceneVo.ambientColor[0] = 0.5f;
            currentSceneVo.ambientColor[1] = 0.5f;
            currentSceneVo.ambientColor[2] = 0.5f;
            currentSceneVo.ambientColor[3] = 1.0f;
        }

        forceContinuousParticles(composite);
    }

    public void getIntoPrevComposite() {
        getCamera().position.set(0, 0, 0);
        uiStage.getCompositePanel().stepUp();
        uiStage.getItemsBox().initContent();
    }

    public void addCompositeToLibrary() {
        CompositeItem item = null;
        if (currentSelection.size() == 1) {
            for (SelectionRectangle value : currentSelection.values()) {
                if (value.getHost().isComposite()) {
                    item = (CompositeItem) value.getHost();
                }
            }
        }

        if (item == null) return;

        InputDialog dlg = uiStage.showInputDialog();

        dlg.setDescription("Please set unique name for your component");

        final CompositeItem itemToAdd = item;

        dlg.setListener(new InputDialog.InputDialogListener() {

            @Override
            public void onConfirm(String input) {
                currentSceneVo.libraryItems.put(input, itemToAdd.getDataVO());
                uiStage.reInitLibrary();
            }
        });


    }

    public void getIntoComposite() {
        CompositeItem item = null;
        getCurrentScene().updateDataVO();
        if (currentSelection.size() == 1) {
            for (SelectionRectangle value : currentSelection.values()) {
                if (value.getHost().isComposite()) {
                    item = (CompositeItem) value.getHost();
                }
            }
        }
        if (item == null) return;
        clearSelections();
        getIntoComposite(item.getDataVO());
    }

    public void getIntoComposite(CompositeItemVO compositeItemVO) {
        //rootSceneVO.update(new CompositeItemVO(currentSceneVo.composite));
        getCamera().position.set(0, 0, 0);
        disableAmbience(true);
        uiStage.getLightBox().disableAmbiance.setChecked(true);
        uiStage.getCompositePanel().addScene(compositeItemVO);
        initSceneView(compositeItemVO);
        uiStage.getItemsBox().initContent();
    }

    public void copyCurrentSelection() {
        ArrayList<MainItemVO> voList = new ArrayList<>();
        for (int i = 0; i < currentScene.getItems().size(); i++) {
            voList.add(currentScene.getItems().get(i).getDataVO());
        }

        //TODO: change this to real clipboard
        tempClipboard = voList;
    }

    public void pastClipBoard() {
        //TODO: duplicate item here
    }

    public CompositeItem groupItemsIntoComposite() {
        currentScene.updateDataVO();
        CompositeItemVO vo = new CompositeItemVO();

        // Calculating lower left and upper values
        float lowerX = 0, lowerY = 0, upperX = 0, upperY = 0;
        int iter = 0;
        for (SelectionRectangle value : currentSelection.values()) {
            if (iter++ == 0) {
                if (value.getScaleX() > 0 && value.getWidth() > 0) {
                    lowerX = value.getX();
                    upperX = value.getX() + value.getWidth();
                } else {
                    upperX = value.getX();
                    lowerX = value.getX() + value.getWidth();
                }

                if (value.getScaleY() > 0 && value.getHeight() > 0) {
                    lowerY = value.getY();
                    upperY = value.getY() + value.getHeight();
                } else {
                    upperY = value.getY();
                    lowerY = value.getY() + value.getHeight();
                }
            }
            if (value.getScaleX() > 0 && value.getWidth() > 0) {
                if (lowerX > value.getX()) lowerX = value.getX();
                if (upperX < value.getX() + value.getWidth()) upperX = value.getX() + value.getWidth();
            } else {
                if (upperX < value.getX()) upperX = value.getX();
                if (lowerX > value.getX() + value.getWidth()) lowerX = value.getX() + value.getWidth();
            }
            if (value.getScaleY() > 0 && value.getHeight() > 0) {
                if (lowerY > value.getY()) lowerY = value.getY();
                if (upperY < value.getY() + value.getHeight()) upperY = value.getY() + value.getHeight();
            } else {
                if (upperY < value.getY()) upperY = value.getY();
                if (lowerY > value.getY() + value.getHeight()) lowerY = value.getY() + value.getHeight();
            }
        }

        float width = upperX - lowerX;
        float height = upperY - lowerY;

        for (SelectionRectangle value : currentSelection.values()) {
            MainItemVO itemVo = value.getHost().getDataVO();
            //System.out.println("ASSSDDD " + itemVo.x + " BASDDD " + lowerX);
            itemVo.x = itemVo.x - lowerX;
            itemVo.y = itemVo.y - lowerY;
            //System.out.println("adddd " + itemVo.x );
            vo.composite.addItem(itemVo);
        }
        vo.x = lowerX;
        vo.y = lowerY;
        vo.layerName = uiStage.getCurrentSelectedLayer().layerName;

        CompositeItem item = sceneLoader.getCompositeElement(vo);

        item.setWidth(width);
        item.setHeight(height);
        //item.getItems().get(0).getDataVO();
        ///System.out.println("fddddd " + ((Actor)item.getItems().get(0)).getX());

        removeCurrentSelectedItems();

        currentScene.addItem(item);

        ///System.out.println("SSSSddddd " + ((Actor)item.getItems().get(0)).getX());
        initItemListeners(item);
        updateSceneTree();
        setSelection(item, true);

        return item;
    }


    public void alignSelectionsByY(float y, boolean ignoreSelfHeight) {
        int ratio = ignoreSelfHeight ? 0 : 1;
        for (SelectionRectangle value : currentSelection.values()) {
            Actor actor = value.getHostAsActor();
            //actor.setY(y - ratio * actor.getHeight());
            if (actor.getScaleY() < 0) {
                actor.setY(y - (ratio + actor.getScaleY()) * actor.getHeight());
            } else {
                actor.setY(y - ratio * actor.getHeight());
            }
            value.setY(actor.getY());
        }
    }

    public void alignSelectionsByX(float x, boolean ignoreSelfWidth) {
        int ratio = ignoreSelfWidth ? 0 : 1;
        for (SelectionRectangle value : currentSelection.values()) {
            Actor actor = value.getHostAsActor();
            //actor.setX(x - ratio * actor.getWidth());
            if (actor.getScaleX() < 0) {
                actor.setX(x - (ratio + actor.getScaleX()) * actor.getWidth());
            } else {
                actor.setX(x - ratio * actor.getWidth());
            }
            value.setX(actor.getX());
        }
    }

    public void loadCurrentProject(String name) {
        rm = new SandboxResourceManager();
        essentials.rm = rm;
        loadScene(name);
    }

    public void loadCurrentProject() {
        ProjectVO projectVO = DataManager.getInstance().getCurrentProjectVO();
        loadCurrentProject(projectVO.lastOpenScene.isEmpty() ? "MainScene" : projectVO.lastOpenScene);
    }

    public void loadScene(String sceneName) {
        currentLoadedSceneFileName = sceneName;
        uiStage.getCompositePanel().clearScenes();
        initData(sceneName);
        initView();
        ProjectVO projectVO = DataManager.getInstance().getCurrentProjectVO();
        projectVO.lastOpenScene = sceneName;
        DataManager.getInstance().saveCurrentProject();
        getCamera().position.set(0, 0, 0);

    }


    public int getCurrentMode() {
        return currentMode;
    }

    public void setCurrentMode(int currentMode) {
        this.currentMode = currentMode;
        for (SelectionRectangle value : currentSelection.values()) {
            value.setMode(currentMode);
        }
    }


    public SceneVO sceneVoFromItems() {
        CompositeItemVO itemVo = rootSceneVO;
        cleanComposite(itemVo.composite);
        currentSceneVo.composite = itemVo.composite;
        return currentSceneVo;
    }

    private void cleanComposite(CompositeVO compositeVO) {
        Iterator<CompositeItemVO> compositeItemVOIterator = compositeVO.sComposites.iterator();
        while (compositeItemVOIterator.hasNext()) {
            CompositeItemVO next = compositeItemVOIterator.next();
            if (isCompositeEmpty(next.composite)) {
                compositeItemVOIterator.remove();
            }
        }
    }

    private boolean isCompositeEmpty(CompositeVO composite) {
        if (composite.isEmpty()) {
            return true;
        }
        cleanComposite(composite);
        return composite.isEmpty();
    }

    public void reconstructFromSceneVo(CompositeItemVO vo) {
        initSceneView(vo);
    }



    public void undo() {
        FlowActionEnum lastFlowAction = flow.getFlowLastAction();
        CompositeItemVO compositeItemVO = flow.undo();
        switch (lastFlowAction) {
            case GET_INTO_COMPOSITE:
                getIntoPrevComposite();
                break;
            case GET_OUT_COMPOSITE:
                getIntoComposite(compositeItemVO);
                break;
            default:
                reconstructFromSceneVo(compositeItemVO);
                break;
        }
        currentScene.updateDataVO();
    }

    public void redo() {
        CompositeItemVO compositeItemVO = flow.redo();
        FlowActionEnum lastFlowAction = flow.getFlowLastAction();
        switch (lastFlowAction) {
            case GET_INTO_COMPOSITE:
                getIntoComposite(compositeItemVO);
                break;
            case GET_OUT_COMPOSITE:
                getIntoPrevComposite();
                break;
            default:
                reconstructFromSceneVo(compositeItemVO);
                break;
        }
        currentScene.updateDataVO();
    }

    public ArrayList<IBaseItem> getSelectedItems() {
        ArrayList<IBaseItem> items = new ArrayList<IBaseItem>();
        for (SelectionRectangle value : currentSelection.values()) {
            items.add(value.getHost());
        }
        return items;
    }

    public void cutAction() {
        ArrayList<IBaseItem> items = getSelectedItems();
        putItemsToClipboard(items);
        removeCurrentSelectedItems();
    }

    public void copyAction() {
        currentScene.updateDataVO();
        ArrayList<IBaseItem> items = getSelectedItems();
        putItemsToClipboard(items);
    }

    public void pasteAction(float x, float y, boolean ignoreCameraPos) {
        CompositeVO tempHolder;
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        tempHolder = json.fromJson(CompositeVO.class, fakeClipboard);

        if (tempHolder == null) return;

        CompositeItemVO fakeVO = new CompositeItemVO();

        fakeVO.composite = tempHolder;
        CompositeItem fakeItem = new CompositeItem(fakeVO, sceneLoader.essentials);

        ArrayList<IBaseItem> finalItems = new ArrayList<IBaseItem>();
        Actor firstItem = (Actor) fakeItem.getItems().get(0);
        float offsetX = firstItem.getX()*currentScene.mulX;
        float offsetY = firstItem.getY()*currentScene.mulY;
        for (int i = 1; i < fakeItem.getItems().size(); i++) {
            Actor item = (Actor) fakeItem.getItems().get(i);
            if (item.getX()*currentScene.mulX < offsetX) {
                offsetX = item.getX()*currentScene.mulX;
            }
            if (item.getY()*currentScene.mulY < offsetY) {
                offsetY = item.getY()*currentScene.mulY;
            }
        }
        Vector3 cameraPos = ignoreCameraPos ? new Vector3(0, 0, 0) : ((OrthographicCamera) getCamera()).position;
        for (int i = 0; i < fakeItem.getItems().size(); i++) {
            IBaseItem itm = fakeItem.getItems().get(i);
            itm.getDataVO().layerName = uiStage.getCurrentSelectedLayer().layerName;
            currentScene.addItem(itm);
            ((Actor) itm).setX(x + ((Actor) itm).getX() - offsetX + (cameraPos.x + copedItemCameraOffset.x));
            ((Actor) itm).setY(y + ((Actor) itm).getY() - offsetY + (cameraPos.y + copedItemCameraOffset.y));
            itm.updateDataVO();
            initItemListeners(itm);
            finalItems.add(itm);
        }

        setSelections(finalItems, true);
        updateSceneTree();
    }

    private void putItemsToClipboard(ArrayList<IBaseItem> items) {
        CompositeVO tempHolder = new CompositeVO();
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        Actor actor = (Actor) items.get(0);
        Vector3 cameraPos = ((OrthographicCamera) getCamera()).position;
        Vector3 vector3 = new Vector3(actor.getX() - cameraPos.x, actor.getY() - cameraPos.y, 0);
        for (IBaseItem item : items) {
            tempHolder.addItem(item.getDataVO());
            actor = (Actor) item;
            if (actor.getX() - cameraPos.x < vector3.x) {
                vector3.x = actor.getX() - cameraPos.x;
            }
            if (actor.getY() - cameraPos.y < vector3.y) {
                vector3.y = actor.getY() - cameraPos.y;
            }
        }
        fakeClipboard = json.toJson(tempHolder);
        copedItemCameraOffset = vector3;
    }

    public void saveSceneCurrentSceneData() {
        currentScene.updateDataVO();
        flow.setPendingHistory(getCurrentScene().dataVO);
        flow.applyPendingAction();
    }

    public void setSceneAmbientColor(Color color) {
        currentSceneVo.ambientColor[0] = color.r;
        currentSceneVo.ambientColor[1] = color.g;
        currentSceneVo.ambientColor[2] = color.b;
        currentSceneVo.ambientColor[3] = color.a;
        essentials.rayHandler.setAmbientLight(color);
    }

    public void updateSelections() {
        for (SelectionRectangle value : currentSelection.values()) {
            value.update();
        }
    }

    public void alignSelections(int align) {
        //ResolutionEntryVO resolutionEntryVO = dataManager.getCurrentProjectInfoVO().getResolution(dataManager.curResolution);
        switch (align) {
            case Align.top:
                alignSelectionsByY(getCurrentSelectionsHighestY(), false);
                break;
            case Align.left:
                alignSelectionsByX(getCurrentSelectionsLowestX(), true);
                break;
            case Align.bottom:
                alignSelectionsByY(getCurrentSelectionsLowestY(), true);
                break;
            case Align.right:
                alignSelectionsByX(getCurrentSelectionsHighestX(), false);
                break;
        }
    }


    public float getCurrentSelectionsHighestY() {
        float highestY = -Float.MAX_VALUE;
        for (SelectionRectangle value : currentSelection.values()) {
            Actor actor = value.getHostAsActor();
            float maxY = Math.max(actor.getY(), actor.getY() + actor.getHeight() * actor.getScaleY());
            if (maxY > highestY) {
                highestY = maxY;
            }
        }
        return highestY;
    }

    public float getCurrentSelectionsHighestX() {
        float highestX = -Float.MAX_VALUE;
        for (SelectionRectangle value : currentSelection.values()) {
            Actor actor = value.getHostAsActor();
            float maxX = Math.max(actor.getX(), actor.getX() + actor.getWidth() * actor.getScaleX());
            if (maxX > highestX) {
                highestX = maxX;
            }
        }
        return highestX;
    }

    public float getCurrentSelectionsLowestX() {
        float lowestX = Float.MAX_VALUE;
        for (SelectionRectangle value : currentSelection.values()) {
            Actor actor = value.getHostAsActor();
            float minX = Math.min(actor.getX(), actor.getX() + actor.getWidth() * actor.getScaleX());
            if (minX < lowestX) {
                lowestX = minX;
            }
        }
        return lowestX;
    }

    public float getCurrentSelectionsLowestY() {
        float lowestY = Float.MAX_VALUE;
        for (SelectionRectangle value : currentSelection.values()) {
            Actor actor = value.getHostAsActor();
            float minY = Math.min(actor.getY(), actor.getY() + actor.getHeight() * actor.getScaleY());
            if (minY < lowestY) {
                lowestY = minY;
            }
        }
        return lowestY;
    }

    public void disableLights(boolean disable) {

        ArrayList<LightActor> lights = getAllLights(currentScene);
//        if (disable) {
//            lights = essentials.rayHandler.lightList;
//        } else {
//            lights = essentials.rayHandler.disabledLights;
//        }
        for (int i = lights.size() - 1; i >= 0; i--) {
            LightActor lightActor = lights.get(i);
            if(lightActor.lightObject !=null){
                lightActor.lightObject.setActive(!disable);
            }

        }
    }


    private ArrayList<LightActor> getAllLights(CompositeItem curComposite){

        ArrayList<LightActor> lights = new ArrayList<LightActor>();

        if(curComposite == null){
            return lights;
        }

        ArrayList<IBaseItem> items = curComposite.getItems();

        ArrayList<CompositeItem> nestedComposites = new ArrayList<CompositeItem>();


        int i=0;
        for(i=0;i<items.size();i++){
            IBaseItem item = items.get(i);
            if(item instanceof LightActor){
                lights.add((LightActor) item);
            }

            if( item instanceof CompositeItem){
                nestedComposites.add((CompositeItem) item);
            }

        }

        for(i=0;i<nestedComposites.size();i++){
            lights.addAll(getAllLights(nestedComposites.get(i)));
        }

        return lights;
    }
}

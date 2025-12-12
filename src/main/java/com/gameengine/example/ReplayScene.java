package com.gameengine.example;

import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameObject;
import com.gameengine.graphics.Renderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;
import com.gameengine.example.EntityFactory;
import com.gameengine.recording.RecordingJson;
import com.gameengine.recording.RecordingStorage;
import com.gameengine.recording.FileRecordingStorage;

import java.io.File;
import java.util.*;

public class ReplayScene extends Scene {
    private final GameEngine engine;
    private String recordingPath;
    private Renderer renderer;
    private InputManager input;
    private float time;
    private boolean debugReplay = false;
    private float debugAccumulator = 0f;
    private Keyframe lastKeyframe;

    private static class Keyframe {
        static class EntityInfo {
            Vector2 pos, velocity;
            String rt; // RECTANGLE/CIRCLE/LINE/CUSTOM/null
            float w, h;
            float r = 0.9f, g = 0.9f, b = 0.2f, a = 1.0f; // 默认颜色
            String id;
        }

        double t;
        java.util.List<EntityInfo> entities = new ArrayList<>();
    }

    private final List<Keyframe> keyframes = new ArrayList<>();
    private final java.util.List<GameObject> objectList = new ArrayList<>();

    // 如果 path 为 null，则先展示 recordings 目录下的文件列表，供用户选择
    public ReplayScene(GameEngine engine, String path) {
        super("Replay");
        this.engine = engine;
        this.recordingPath = path;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        this.input = engine.getInputManager();
        // 重置状态，防止从列表进入后残留
        this.time = 0f;
        this.keyframes.clear();
        this.objectList.clear();
        if (recordingPath != null) {
            loadRecording(recordingPath);

        } else {
            // 仅进入文件选择模式
            this.recordingFiles = null;
            this.selectedIndex = 0;
        }
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        if (input.isKeyJustPressed(27) || input.isKeyJustPressed(8)) { // ESC/BACK
            engine.setScene(new MenuScene(engine));
            return;
        }
        // 文件选择模式
        if (recordingPath == null) {
            handleFileSelection();
            return;
        }

        if (keyframes.size() < 1)
            return;
        time += deltaTime;
        // 限制在最后关键帧处停止（也可选择循环播放）
        double lastT = keyframes.get(keyframes.size() - 1).t;
        if (time > lastT) {
            time = (float) lastT;
        }

        // 查找区间
        Keyframe a = keyframes.get(0);
        Keyframe b = keyframes.get(keyframes.size() - 1);
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe k1 = keyframes.get(i);
            Keyframe k2 = keyframes.get(i + 1);
            if (time >= k1.t && time <= k2.t) {
                a = k1;
                b = k2;
                if(lastKeyframe != k1)
                {
                    updateObjects(k1);
                    lastKeyframe = k1;
                }break;
            }
        }
        double u = time - a.t;
        // 调试输出节流

        updateInterpolatedPositions(a, b, (float) u);
    }

    @Override
    public void render() {
        renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.06f, 0.06f, 0.08f, 1.0f);
        if (recordingPath == null) {
            renderFileList();
            return;
        }
        // 基于 Transform 手动绘制（回放对象没有附带 RenderComponent）
        super.render();
        String hint = "REPLAY: ESC to return";
        renderer.drawText(renderer.getWidth() / 2.0f - 50, 30, hint, 25, 0.8f, 0.8f, 0.8f, 1.0f);
    }

    private void loadRecording(String path) {
        keyframes.clear();
        RecordingStorage storage = new FileRecordingStorage();
        try {
            for (String line : storage.readLines(path)) {
                if (!line.contains("\"type\":\"keyframe\""))
                    continue;
                Keyframe kf = new Keyframe();
                kf.t = RecordingJson
                        .parseDouble(RecordingJson.field(line, "t"));
                // 解析 entities 列表中的若干 {"id":"name","x":num,"y":num}
                int idx = line.indexOf("\"entities\":[");
                if (idx < 0)
                    continue;
                int bracket = line.indexOf('[', idx);
                String arr = bracket >= 0 ? RecordingJson.extractArray(line, bracket)
                        : "";
                String[] parts = RecordingJson.splitTopLevel(arr);
                for (String p : parts) {
                    Keyframe.EntityInfo ei = new Keyframe.EntityInfo();
                    ei.id = RecordingJson
                            .stripQuotes(RecordingJson.field(p, "id"));
                    double x = RecordingJson
                            .parseDouble(RecordingJson.field(p, "x"));
                    double y = RecordingJson
                            .parseDouble(RecordingJson.field(p, "y"));
                    ei.pos = new Vector2((float) x, (float) y);
                    String rt = RecordingJson
                            .stripQuotes(RecordingJson.field(p, "rt"));
                    ei.rt = rt;
                    ei.w = (float) RecordingJson
                            .parseDouble(RecordingJson.field(p, "w"));
                    ei.h = (float) RecordingJson
                            .parseDouble(RecordingJson.field(p, "h"));
                    String colorArr = RecordingJson.field(p, "color");
                    if (colorArr != null && colorArr.startsWith("[")) {
                        String c = colorArr.substring(1, Math.max(1, colorArr.indexOf(']', 1)));
                        String[] cs = c.split(",");
                        if (cs.length >= 3) {
                            try {
                                ei.r = Float.parseFloat(cs[0].trim());
                                ei.g = Float.parseFloat(cs[1].trim());
                                ei.b = Float.parseFloat(cs[2].trim());
                                if (cs.length >= 4)
                                    ei.a = Float.parseFloat(cs[3].trim());
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    if (RecordingJson.field(p, "vx") != null) {
                        double vx = RecordingJson
                                .parseDouble(RecordingJson.field(p, "vx"));
                        double vy = RecordingJson
                                .parseDouble(RecordingJson.field(p, "vy"));
                        ei.velocity = new Vector2((float) vx, (float) vy);
                    }

                    kf.entities.add(ei);
                }
                keyframes.add(kf);
            }
        } catch (Exception e) {

        }
        keyframes.sort(Comparator.comparingDouble(k -> k.t));
    }

    private void updateObjects(Keyframe kf) {
        for (GameObject obj : objectList) {
            removeGameObject(obj);
        }
        objectList.clear();
        for (int i = 0; i < kf.entities.size(); i++) {
            GameObject obj = buildObjectFromEntity(kf.entities.get(i), i);
            addGameObject(obj);
            objectList.add(obj);
        }
    }

    private void ensureObjectCount(int n) {
        while (objectList.size() < n) {
            GameObject obj = new GameObject("RObj#" + objectList.size());
            obj.addComponent(new TransformComponent(new Vector2(0, 0)));
            // 为回放对象添加可渲染组件（默认外观，稍后在 refreshRenderFromKeyframe 应用真实外观）
            addGameObject(obj);
            objectList.add(obj);
        }
        while (objectList.size() > n) {
            GameObject obj = objectList.remove(objectList.size() - 1);
            obj.setActive(false);
        }
    }

    private void updateInterpolatedPositions(Keyframe a, Keyframe b, float u) {
        int n = Math.min(a.entities.size(), b.entities.size());
        ensureObjectCount(n);
        for (int i = 0; i < n; i++) {
            if (a.entities.get(i).velocity == null)
                continue;
            Vector2 pa = a.entities.get(i).pos;
            float vx = a.entities.get(i).velocity.x;
            float vy = a.entities.get(i).velocity.y;

            GameObject obj = objectList.get(i);
            TransformComponent tc = obj.getComponent(TransformComponent.class);
            if (tc != null)
                tc.setPosition(new Vector2(pa.x + vx * u, pa.y + vy * u));
            
        }
    }

    private GameObject buildObjectFromEntity(Keyframe.EntityInfo ei, int index) {
        GameObject obj;
        if ("Player".equalsIgnoreCase(ei.id)) {
            obj = EntityFactory.createPlayer(renderer);
        } else if ("Enemy".equalsIgnoreCase(ei.id)) {
            float w2 = (ei.w > 0 ? ei.w : 20);
            float h2 = (ei.h > 0 ? ei.h : 20);
            obj = EntityFactory.createSimpleObject("Enemy");
            RenderComponent render = obj.addComponent(new RenderComponent(
                    RenderComponent.RenderType.RECTANGLE,
                    new Vector2(20, 20),
                    new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f) // 橙色
            ));
            render.setRenderer(renderer);
        } else {
            GameObject tmp = new GameObject(ei.id == null ? ("Obj#" + index) : ei.id);
            RenderComponent rc = tmp.addComponent(
                    new RenderComponent(
                            RenderComponent.RenderType.CIRCLE,
                            new Vector2(Math.max(1, ei.w), Math.max(1, ei.h)),
                            new RenderComponent.Color(ei.r, ei.g, ei.b, ei.a)));
            rc.setRenderer(renderer);
            obj = tmp;
        }

        obj.addComponent(new TransformComponent(ei.pos));
        return obj;
    }

    // ========== 文件列表模式 ==========
    private List<File> recordingFiles;
    private int selectedIndex = 0;

    private void ensureFilesListed() {
        if (recordingFiles != null)
            return;
        RecordingStorage storage = new FileRecordingStorage();
        recordingFiles = storage.listRecordings();
    }

    private void handleFileSelection() {
        ensureFilesListed();
        if (input.isKeyJustPressed(38) || input.isKeyJustPressed(265)) { // up (AWT 38 / GLFW 265)
            selectedIndex = (selectedIndex - 1 + Math.max(1, recordingFiles.size()))
                    % Math.max(1, recordingFiles.size());
        } else if (input.isKeyJustPressed(40) || input.isKeyJustPressed(264)) { // down (AWT 40 / GLFW 264)
            selectedIndex = (selectedIndex + 1) % Math.max(1, recordingFiles.size());
        } else if (input.isKeyJustPressed(10) || input.isKeyJustPressed(32) || input.isKeyJustPressed(257)
                || input.isKeyJustPressed(335)) { // enter/space (AWT 10/32, GLFW 257/335)
            if (recordingFiles.size() > 0) {
                String path = recordingFiles.get(selectedIndex).getAbsolutePath();
                this.recordingPath = path;
                clear();
                initialize();
            }
        } else if (input.isKeyJustPressed(27)) { // esc
            engine.setScene(new MenuScene(engine));
        }
    }

    private void renderFileList() {
        ensureFilesListed();
        int w = renderer.getWidth();
        int h = renderer.getHeight();
        String title = "SELECT RECORDING";
        renderer.drawText(w / 2f, 80, title, 40, 1f, 1f, 1f, 1f);

        if (recordingFiles.isEmpty()) {
            String none = "NO RECORDINGS FOUND";
            renderer.drawText(w / 2f, h / 2f, none, 48, 0.9f, 0.8f, 0.2f, 1f);
            String back = "ESC TO RETURN";
            renderer.drawText(w / 2f, h - 60, back, 48, 0.7f, 0.7f, 0.7f, 1f);
            return;
        }

        float startY = 140f;
        float itemH = 28f;
        for (int i = 0; i < recordingFiles.size(); i++) {
            String name = recordingFiles.get(i).getName();
            float x = 100f;
            float y = startY + i * itemH;
            if (i == selectedIndex) {
                renderer.drawRect(x - 10, y - 12, 600, 24, 0.3f, 0.3f, 0.4f, 0.8f);
            }
            renderer.drawText(x + name.length() * 6, y, name, 20, 0.9f, 0.9f, 0.9f, 1f);
        }

        String hint = "UP/DOWN SELECT, ENTER PLAY, ESC RETURN";
        renderer.drawText(w / 2f, h - 60, hint, 20, 0.7f, 0.7f, 0.7f, 1f);
    }

    // Parsing logic has been moved to RecordingJson
}

package com.gameengine.recording;

import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.math.Vector2;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameObject;
import com.gameengine.input.InputManager;
import com.gameengine.scene.Scene;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class RecordingService {
    private final RecordingConfig config;
    private final BlockingQueue<String> lineQueue;
    private volatile boolean recording;
    private Thread writerThread;
    private RecordingStorage storage = new FileRecordingStorage();
    private double elapsed;
    private double keyframeElapsed;
    private double sampleAccumulator;
    private final double warmupSeconds = 0.1; // 等待一帧让场景对象完成初始化
    private final DecimalFormat qfmt;
    private Scene lastScene;

    public RecordingService(RecordingConfig config) {
        this.config = config;
        this.lineQueue = new ArrayBlockingQueue<>(config.queueCapacity);
        this.recording = false;
        this.elapsed = 0.0;
        this.keyframeElapsed = 0.0;
        this.sampleAccumulator = 0.0;
        this.qfmt = new DecimalFormat();
        this.qfmt.setMaximumFractionDigits(Math.max(0, config.quantizeDecimals));
        this.qfmt.setGroupingUsed(false);
    }

    public boolean isRecording() {
        return recording;
    }

    public void start(Scene scene, int width, int height) throws IOException {
        if (recording)
            return;
        storage.openWriter(config.outputPath);
        writerThread = new Thread(() -> {
            try {
                while (recording || !lineQueue.isEmpty()) {
                    String s = lineQueue.poll();
                    if (s == null) {
                        try {
                            Thread.sleep(2);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }
                    storage.writeLine(s);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    storage.closeWriter();
                } catch (Exception ignored) {
                }
            }
        }, "record-writer");
        recording = true;
        writerThread.start();

        // header
        enqueue("{\"type\":\"header\",\"version\":1,\"w\":" + width + ",\"h\":" + height + "}");
        keyframeElapsed = 0.0;
    }

    public void stop() {
        if (!recording)
            return;
        try {
            if (lastScene != null) {
                writeKeyframe(lastScene);
            }
        } catch (Exception ignored) {
        }
        recording = false;
        try {
            writerThread.join(500);
        } catch (InterruptedException ignored) {
        }
    }

    public void update(double deltaTime, Scene scene, InputManager input) {
        if (!recording)
            return;
        elapsed += deltaTime;
        keyframeElapsed += deltaTime;
        sampleAccumulator += deltaTime;
        lastScene = scene;

        // input events (sampled at native frequency, but only write justPressed events)
        Set<Integer> just = input.getPressedKeysSnapshot();
        if (!just.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"input\",\"t\":").append(qfmt.format(elapsed)).append(",\"keys\":[");
            boolean first = true;
            for (Integer k : just) {
                if (!first)
                    sb.append(',');
                sb.append(k);
                first = false;
            }
            sb.append("]}");
            enqueue(sb.toString());
        }

        // sampled deltas placeholder (extensible): skip for now to maintain minimal
        // version

        // periodic keyframe (skip warmup period to avoid empty keyframes)
        if (elapsed >= warmupSeconds && keyframeElapsed >= config.keyframeIntervalSec) {
            if (writeKeyframe(scene)) {
                keyframeElapsed = 0.0;
            }
        }
    }

    private boolean writeKeyframe(Scene scene) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"keyframe\",\"t\":").append(qfmt.format(elapsed)).append(",\"entities\":[");
        List<GameObject> objs = scene.getGameObjects();
        boolean first = true;
        int count = 0;
        for (GameObject obj : objs) {
            TransformComponent tc = obj.getComponent(TransformComponent.class);
            if (tc == null)
                continue;
            float x = tc.getPosition().x;
            float y = tc.getPosition().y;
            if (!first)
                sb.append(',');
            sb.append('{')
                    .append("\"id\":\"").append(obj.getName()).append("\",")
                    .append("\"x\":").append(qfmt.format(x)).append(',')
                    .append("\"y\":").append(qfmt.format(y));

            // Optional render info (if object has RenderComponent, record shape, size,
            // color)
            RenderComponent rc = obj.getComponent(RenderComponent.class);
            if (rc != null) {
                RenderComponent.RenderType rt = rc.getRenderType();
                Vector2 sz = rc.getSize();
                RenderComponent.Color col = rc.getColor();
                sb.append(',')
                        .append("\"rt\":\"").append(rt.name()).append("\",")
                        .append("\"w\":").append(qfmt.format(sz.x)).append(',')
                        .append("\"h\":").append(qfmt.format(sz.y)).append(',')
                        .append("\"color\":[")
                        .append(qfmt.format(col.r)).append(',')
                        .append(qfmt.format(col.g)).append(',')
                        .append(qfmt.format(col.b)).append(',')
                        .append(qfmt.format(col.a)).append(']');
            } else {
                // Mark custom rendering (e.g., Player) to facilitate approximate restoration
                // during playback
                sb.append(',').append("\"rt\":\"CUSTOM\"");
            }

            PhysicsComponent pc = obj.getComponent(PhysicsComponent.class);
            if(pc != null)
            {
                sb.append(',')
                    .append("\"vx\":").append(pc.getVelocity().x).append(",")
                    .append("\"vy\":").append(pc.getVelocity().y);
            }

            sb.append('}');
            first = false;
            count++;
        }
        sb.append("]}");
        if (count == 0)
            return false;
        enqueue(sb.toString());
        return true;
    }

    private void enqueue(String line) {
        if (!lineQueue.offer(line)) {
            // Simple discard strategy: drop low-priority data when queue is full (directly
            // discard here)
        }
    }
}

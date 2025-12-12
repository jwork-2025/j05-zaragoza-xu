package com.gameengine.example;

import com.gameengine.core.GameEngine;
import com.gameengine.scene.Scene;
import com.gameengine.config.GameConfig;

/**
 * 游戏示例
 */
public class GameExample {
    public static void main(String[] args) {
        System.out.println("启动游戏引擎...");

        try {
            // 创建游戏引擎
            GameEngine engine = new GameEngine(GameConfig.WIDTH, GameConfig.HEIGHT, "My Game");

            // 创建游戏场景
            Scene menuScene = new MenuScene(engine);
            // 设置场景
            engine.setScene(menuScene);

            // 运行游戏
            engine.run();

        } catch (Exception e) {
            System.err.println("游戏运行出错: " + e.getMessage());
            e.printStackTrace();
        }

    }
}

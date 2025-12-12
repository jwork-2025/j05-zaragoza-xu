package com.gameengine.example;

import java.util.Random;

import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.config.GameConfig;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameLogic;
import com.gameengine.core.GameObject;
import com.gameengine.graphics.Renderer;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;
import com.gameengine.example.EntityFactory;

public class GameScene extends Scene {
    private GameEngine engine;
    private Renderer renderer;
    private Random random;
    private float enemyCreateTime, time;
    private int enemyCreatedPerSec;
    private GameLogic gameLogic;

    public GameScene(GameEngine engine) {
        super("GameScene");
        this.engine = engine;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        this.random = new Random();
        this.time = this.enemyCreateTime = 0;
        this.enemyCreatedPerSec = 1;
        this.gameLogic = new GameLogic(this);
        this.gameLogic.setGameEngine(engine);

        // 创建游戏对象
        createPlayer();
        createEnemies();
        createDecorations();
        createHPBar();
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        time += deltaTime;
        enemyCreateTime += deltaTime;

        // 使用游戏逻辑类处理游戏规则
        gameLogic.handlePlayerInput();
        gameLogic.handleEnemyAvoidance(deltaTime);
        gameLogic.updatePhysics();
        if (gameLogic.checkCollisions())
            gameLogic.handleCollisions();
        gameLogic.checkCollisions();

        if (gameLogic.isGameOver()) {
            return;
        }

        // 生成新敌人
        if (enemyCreateTime > 1.0 / enemyCreatedPerSec) {
            createEnemy();
            enemyCreateTime = 0;
        }
        if (time > 3.0) {
            enemyCreatedPerSec++;
            time = 0;
        }
    }

    @Override
    public void render() {
        // 绘制背景
        renderer.drawRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT, 0.1f, 0.1f, 0.2f, 1.0f);

        // 渲染所有对象
        super.render();

        if (gameLogic.isGameOver()) {
            renderer.drawRect((GameConfig.WIDTH - 400) / 2, (GameConfig.HEIGHT - 100) / 2, 400, 100, 0.0f, 0.0f, 0.0f,
                    0.7f);
            renderer.drawText((GameConfig.WIDTH) / 2, (GameConfig.HEIGHT) / 2, "GAME OVER", 48, 1.0f, 1.0f, 1.0f,
                    1.0f);
        }
    }

    private void createPlayer() {
        // 创建葫芦娃 - 所有部位都在一个GameObject中
        GameObject player = EntityFactory.createPlayer(renderer);

        // 添加变换组件
        TransformComponent transform = player.addComponent(new TransformComponent(new Vector2(400, 300)));

        // 添加物理组件
        PhysicsComponent physics = player.addComponent(new PhysicsComponent(1.0f));
        physics.setFriction(0.95f);

        addGameObject(player);
    }

    private void createEnemies() {
        for (int i = 0; i < 3; i++) {
            createEnemy();
        }
    }

    private void createEnemy() {
        GameObject enemy = EntityFactory.createSimpleObject("Enemy");

        // 随机位置
        Vector2 position = new Vector2(
                random.nextFloat() * GameConfig.WIDTH,
                0);

        // 添加变换组件
        TransformComponent transform = enemy.addComponent(new TransformComponent(position));

        // 添加渲染组件 - 改为矩形，使用橙色
        RenderComponent render = enemy.addComponent(new RenderComponent(
                RenderComponent.RenderType.RECTANGLE,
                new Vector2(20, 20),
                new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f) // 橙色
        ));
        render.setRenderer(renderer);

        // 添加物理组件
        PhysicsComponent physics = enemy.addComponent(new PhysicsComponent(0.5f));
        physics.setVelocity(new Vector2(
                (random.nextFloat() - 0.5f) * 100,
                (random.nextFloat()) * 100));
        physics.setFriction(1);
        physics.setUseGravity(true);
        // physics.setGravity(position);

        addGameObject(enemy);
    }

    private void createDecorations() {
        for (int i = 0; i < 5; i++) {
            createDecoration();
        }
    }

    private void createDecoration() {
        GameObject decoration = EntityFactory.createSimpleObject("Decoration");

        // 随机位置
        Vector2 position = new Vector2(
                random.nextFloat() * GameConfig.WIDTH,
                random.nextFloat() * GameConfig.HEIGHT);
        
        TransformComponent transform = decoration.addComponent(new TransformComponent(position));

        // 添加渲染组件
        RenderComponent render = decoration.addComponent(new RenderComponent(
                RenderComponent.RenderType.CIRCLE,
                new Vector2(5, 5),
                new RenderComponent.Color(0.5f, 0.5f, 1.0f, 0.8f)));
        render.setRenderer(renderer);

        addGameObject(decoration);
    }

    private void createHPBar() {
        GameObject decoration = EntityFactory.createHPBar(renderer, gameLogic);

        addGameObject(decoration);
    }
};

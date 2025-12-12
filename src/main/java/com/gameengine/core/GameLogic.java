package com.gameengine.core;

import com.gameengine.components.TransformComponent;
import com.gameengine.components.PhysicsComponent;
import com.gameengine.core.GameObject;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;
import com.gameengine.config.GameConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.Random;

/**
 * 游戏逻辑类，处理具体的游戏规则
 */
public class GameLogic {
    private Scene scene;
    private InputManager inputManager;
    private boolean gameOver;
    private GameEngine gameEngine;
    private ExecutorService physicsExecutor;
    private int HP;
    
    public GameLogic(Scene scene) {
        this.scene = scene;
        this.inputManager = InputManager.getInstance();
        this.gameOver = false;
        int threadCount = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        this.physicsExecutor = Executors.newFixedThreadPool(threadCount);
        this.HP = 5;
    }

    public void setGameEngine(GameEngine engine) {
        this.gameEngine = engine;
    }

    public boolean isGameOver() {
        return gameOver;
    }
    
    public int getHP() {
        return HP;
    }
    
    /**
     * 处理玩家输入
     */
    public void handlePlayerInput() {
        List<GameObject> players = scene.findGameObjectsByComponent(TransformComponent.class);
        if (players.isEmpty()) return;
        
        GameObject player = players.get(0);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        PhysicsComponent physics = player.getComponent(PhysicsComponent.class);
        
        if (transform == null || physics == null) return;
        
        Vector2 movement = new Vector2();
        
        if (inputManager.isKeyPressed(87) || inputManager.isKeyPressed(38)) { // W或上箭头
            movement.y -= 1;
        }
        if (inputManager.isKeyPressed(83) || inputManager.isKeyPressed(40)) { // S或下箭头
            movement.y += 1;
        }
        if (inputManager.isKeyPressed(65) || inputManager.isKeyPressed(37)) { // A或左箭头
            movement.x -= 1;
        }
        if (inputManager.isKeyPressed(68) || inputManager.isKeyPressed(39)) { // D或右箭头
            movement.x += 1;
        }
        
        if (movement.magnitude() > 0) {
            movement = movement.normalize().multiply(200);
            physics.setVelocity(movement);
        }
        
        // 边界检查
        Vector2 pos = transform.getPosition();
        if (pos.x < 0) pos.x = 0;
        if (pos.y < 0) pos.y = 0;
        if (pos.x > GameConfig.WIDTH - 20) pos.x = GameConfig.WIDTH - 20;
        if (pos.y > GameConfig.HEIGHT - 20) pos.y = GameConfig.HEIGHT - 20;
        transform.setPosition(pos);
    }
    
    /**
     * 更新物理系统
     */
    public void updatePhysics() {
        List<PhysicsComponent> physicsComponents = scene.getComponents(PhysicsComponent.class);
        for (PhysicsComponent physics : physicsComponents) {
            // 边界反弹
            TransformComponent transform = physics.getOwner().getComponent(TransformComponent.class);
            if (transform != null) {
                Vector2 pos = transform.getPosition();
                Vector2 velocity = physics.getVelocity();
                
                if(physics.getOwner().getName() == "Enemy" && pos.y >= GameConfig.HEIGHT - 20)
                {
                    scene.removeGameObject(physics.getOwner());
                    return ;
                }

                if (pos.x <= 0 || pos.x >= GameConfig.WIDTH - 20) {
                    velocity.x = -velocity.x;
                    physics.setVelocity(velocity);
                }
                if (pos.y <= 0 || pos.y >= GameConfig.HEIGHT - 20) {
                    velocity.y = -velocity.y;
                    physics.setVelocity(velocity);
                }
                
                // 确保在边界内
                if (pos.x < 0) pos.x = 0;
                if (pos.y < 0) pos.y = 0;
                if (pos.x > GameConfig.WIDTH - 20) pos.x = GameConfig.WIDTH - 20;
                if (pos.y > GameConfig.HEIGHT - 20) pos.y = GameConfig.HEIGHT - 20;
                transform.setPosition(pos);
            }
        }
    }

    private List<GameObject> getEnemies() {
        return scene.getGameObjects().stream()
            .filter(obj -> obj.getName().equals("Enemy"))
            .filter(obj -> obj.isActive())
            .collect(Collectors.toList());
    }

    public void handleEnemyAvoidance(float deltaTime) {
        if (gameOver) return;
        
        List<GameObject> enemies = getEnemies();
        if (enemies.isEmpty()) return;
        
        if (enemies.size() < 10) {
            handleEnemyAvoidanceSerial(enemies, deltaTime);
        } else {
            handleEnemyAvoidanceParallel(enemies, deltaTime);
        }
    }
    
    private void handleEnemyAvoidanceSerial(List<GameObject> enemies, float deltaTime) {
        for (int i = 0; i < enemies.size(); i++) {
            processAvoidance(enemies, i, deltaTime);
        }
    }
    
    private void handleEnemyAvoidanceParallel(List<GameObject> enemies, float deltaTime) {
        int threadCount = Runtime.getRuntime().availableProcessors() - 1;
        threadCount = Math.max(2, threadCount);
        int batchSize = Math.max(1, enemies.size() / threadCount + 1);
        
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < enemies.size(); i += batchSize) {
            final int start = i;
            final int end = Math.min(i + batchSize, enemies.size());
            
            Future<?> future = physicsExecutor.submit(() -> {
                for (int j = start; j < end; j++) {
                    processAvoidance(enemies, j, deltaTime);
                }
            });
            
            futures.add(future);
        }
        
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private void processAvoidance(List<GameObject> enemies, int index, float deltaTime) {
        GameObject Enemy1 = enemies.get(index);
        TransformComponent transform1 = Enemy1.getComponent(TransformComponent.class);
        PhysicsComponent physics1 = Enemy1.getComponent(PhysicsComponent.class);
        
        if (transform1 == null || physics1 == null) return;
        
        Vector2 pos1 = transform1.getPosition();
        Vector2 avoidance = new Vector2();
        
        for (int j = index + 1; j < enemies.size(); j++) {
            GameObject Enemy2 = enemies.get(j);
            TransformComponent transform2 = Enemy2.getComponent(TransformComponent.class);
            
            if (transform2 == null) continue;
            
            Vector2 pos2 = transform2.getPosition();
            float distance = pos1.distance(pos2);
            
            if (distance < 50 && distance > 0) {
                Vector2 direction = pos1.subtract(pos2).normalize();
                float strength = (50 - distance) / 80.0f;
                avoidance = avoidance.add(direction.multiply(strength * 50));
            }
        }
        
        if (avoidance.magnitude() > 0) {
            Vector2 currentVelocity = physics1.getVelocity();
            float lerpFactor = 1.0f;
            Vector2 avoidanceDirection = avoidance.normalize();
            float avoidanceStrength = Math.min(avoidance.magnitude(), 50f);
            
            Vector2 targetVelocity = currentVelocity.add(
                avoidanceDirection.multiply(avoidanceStrength * deltaTime * 10)
            );
            
            Vector2 newVelocity = new Vector2(
                currentVelocity.x + (targetVelocity.x - currentVelocity.x) * lerpFactor,
                currentVelocity.y + (targetVelocity.y - currentVelocity.y) * lerpFactor
            );
            
            float maxSpeed = 150f;
            if (newVelocity.magnitude() > maxSpeed) {
                newVelocity = newVelocity.normalize().multiply(maxSpeed);
            }
            
            physics1.setVelocity(newVelocity);
        }
    }
    /**
     * 检查碰撞
     */
    public boolean checkCollisions() {
        // 直接查找玩家对象
        GameObject player = scene.getGameObjects().get(0);
        TransformComponent playerTransform = player.getComponent(TransformComponent.class);
        if (playerTransform == null) return false;
        
        // 直接查找所有游戏对象，然后过滤出敌人
        for (GameObject obj : getEnemies()) {
            TransformComponent enemyTransform = obj.getComponent(TransformComponent.class);
            if (enemyTransform != null) {
                float distance = playerTransform.getPosition().distance(enemyTransform.getPosition());
                if (distance < 32) {
                    return true;
                }
            }
        }
        return false;
    }

    public void handleCollisions() {
        GameObject player = scene.getGameObjects().get(0);
        TransformComponent playerTransform = player.getComponent(TransformComponent.class);
        HP --;
        if(HP == 0)
        {
            gameOver = true;
            if (gameEngine != null) {
                gameEngine.stop();
            }
            System.out.println("游戏结束！玩家血量为0！");
            return;
        }
        Random random = new Random();
        int attempts = 0;
        final int MAX_POSITION_ATTEMPTS = 1000;
        while(checkCollisions() && attempts < MAX_POSITION_ATTEMPTS) {
            playerTransform.setPosition(new Vector2(random.nextInt(GameConfig.WIDTH - 20), random.nextInt(GameConfig.HEIGHT - 20)));
            attempts++;
        }
        if (attempts == MAX_POSITION_ATTEMPTS) {
            System.err.println("警告: 未能在 " + MAX_POSITION_ATTEMPTS + " 次尝试后为玩家找到无碰撞位置。");
        }
    }
}

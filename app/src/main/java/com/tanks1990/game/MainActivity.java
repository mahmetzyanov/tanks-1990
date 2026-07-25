package com.tanks1990.game;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private GameView gameView;
    private RelativeLayout controlsLayout;
    
    // Bluetooth variables
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket clientSocket;
    private boolean isHost = false;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_CODE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check and request permissions
        checkPermissions();
        
        // Initialize Bluetooth
        initBluetooth();
        
        // Create game view
        gameView = new GameView(this);
        setContentView(gameView);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN}, 
                    PERMISSION_REQUEST_CODE);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 
                    PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void initBluetooth() {
        final BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = btManager.getAdapter();
        
        if (bluetoothAdapter == null) {
            // Device does not support Bluetooth
            return;
        }
        
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) {
            gameView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) {
            gameView.resume();
        }
    }

    // Inner class for the game rendering and logic
    class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
        private Thread gameThread;
        private boolean isRunning;
        private SurfaceHolder holder;
        
        // Game objects
        private Tank playerTank;
        private Tank enemyTank;
        private Bullet bullet;
        private Map map;
        private int currentLevel = 1;
        private int enemiesDestroyed = 0;
        private static final int ENEMIES_PER_LEVEL = 20;
        
        // Sound effects
        private SoundPool soundPool;
        private int shootSoundId;
        private int explosionSoundId;
        private int hitSoundId;
        private int moveSoundId;
        private int gameOverSoundId;
        private boolean soundEnabled = true;
        
        // Control buttons
        private Button btnUp, btnDown, btnLeft, btnRight, btnFire;
        private Paint paint;
        
        // Touch control areas
        private Rect leftControlArea;
        private Rect rightControlArea;
        private float touchStartX, touchStartY;
        private int lastMoveSoundFrame = 0;

        public GameView(Context context) {
            super(context);
            holder = getHolder();
            holder.addCallback(this);
            
            paint = new Paint();
            paint.setAntiAlias(true);
            
            // Initialize sound pool
            initSound();
            
            // Initialize game objects
            initGame();
            
            // Setup touch controls
            setupTouchControls();
        }
        
        private void initSound() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
                
                soundPool = new SoundPool.Builder()
                    .setMaxStreams(5)
                    .setAudioAttributes(audioAttributes)
                    .build();
            } else {
                soundPool = new SoundPool(5, android.media.AudioManager.STREAM_MUSIC, 0);
            }
            
            // Load sound effects
            shootSoundId = soundPool.load(getContext(), R.raw.shoot, 1);
            explosionSoundId = soundPool.load(getContext(), R.raw.explosion, 1);
            hitSoundId = soundPool.load(getContext(), R.raw.hit, 1);
            moveSoundId = soundPool.load(getContext(), R.raw.move, 1);
            gameOverSoundId = soundPool.load(getContext(), R.raw.game_over, 1);
        }
        
        private void playSound(int soundId) {
            if (soundEnabled && soundPool != null) {
                soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f);
            }
        }

        private void initGame() {
            // Create player tank at bottom center (base position)
            playerTank = new Tank(240, 480, Color.GREEN);
            
            // Create enemy tank
            enemyTank = createEnemyTank();
            
            // Create map with original Battle City level design
            map = new Map(currentLevel);
            
            bullet = null;
            enemiesDestroyed = 0;
        }
        
        private Tank createEnemyTank() {
            // Spawn enemy at one of the top positions
            int[] spawnX = {80, 240, 400};
            int x = spawnX[(int)(Math.random() * spawnX.length)];
            return new Tank(x, 40, Color.RED);
        }

        private void setupTouchControls() {
            setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    int action = event.getAction();
                    float x = event.getX();
                    float y = event.getY();
                    
                    int screenWidth = getWidth();
                    int screenHeight = getHeight();
                    
                    // Left side - movement controls
                    if (x < screenWidth / 2) {
                        if (action == MotionEvent.ACTION_DOWN) {
                            touchStartX = x;
                            touchStartY = y;
                            
                            // Determine direction based on swipe
                            float centerX = screenWidth / 4;
                            float centerY = screenHeight / 2;
                            float dx = x - centerX;
                            float dy = y - centerY;
                            
                            if (Math.abs(dx) > Math.abs(dy)) {
                                if (dx > 0) {
                                    playerTank.setDirection(Tank.DIRECTION_RIGHT);
                                    playerTank.setMoving(true);
                                } else {
                                    playerTank.setDirection(Tank.DIRECTION_LEFT);
                                    playerTank.setMoving(true);
                                }
                            } else {
                                if (dy > 0) {
                                    playerTank.setDirection(Tank.DIRECTION_DOWN);
                                    playerTank.setMoving(true);
                                } else {
                                    playerTank.setDirection(Tank.DIRECTION_UP);
                                    playerTank.setMoving(true);
                                }
                            }
                        } else if (action == MotionEvent.ACTION_UP) {
                            playerTank.setMoving(false);
                        }
                    } 
                    // Right side - fire button
                    else {
                        if (action == MotionEvent.ACTION_DOWN) {
                            // Fire bullet
                            if (bullet == null) {
                                bullet = playerTank.fire();
                                playSound(shootSoundId);
                            }
                        }
                    }
                    
                    // Double tap on top center to toggle sound
                    if (action == MotionEvent.ACTION_DOWN && x > screenWidth / 3 && x < 2 * screenWidth / 3 && y < 50) {
                        soundEnabled = !soundEnabled;
                    }
                    
                    return true;
                }
            });
        }

        public void pause() {
            isRunning = false;
            while (true) {
                try {
                    gameThread.join();
                    break;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            gameThread = null;
            
            // Release sound pool resources
            if (soundPool != null) {
                soundPool.release();
                soundPool = null;
            }
        }

        public void resume() {
            isRunning = true;
            gameThread = new Thread(this);
            gameThread.start();
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            resume();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            pause();
        }

        @Override
        public void run() {
            Canvas canvas;
            while (isRunning) {
                canvas = null;
                try {
                    canvas = holder.lockCanvas();
                    if (canvas != null) {
                        update();
                        render(canvas);
                    }
                } finally {
                    if (canvas != null) {
                        holder.unlockCanvasAndPost(canvas);
                    }
                }
                
                try {
                    Thread.sleep(16); // ~60 FPS
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private void update() {
            // Update player tank position
            if (playerTank.isMoving()) {
                playerTank.update(map);
                // Play move sound occasionally while moving
                int currentFrame = (int)(System.currentTimeMillis() / 100);
                if (currentFrame != lastMoveSoundFrame) {
                    playSound(moveSoundId);
                    lastMoveSoundFrame = currentFrame;
                }
            }
            
            // Update enemy tank (simple AI)
            if (enemyTank != null) {
                enemyTank.updateAI(map);
                enemyTank.update(map);
            }
            
            // Update bullet
            if (bullet != null) {
                bullet.update();
                
                // Check collision with walls
                if (map.checkCollision(bullet.getBounds())) {
                    playSound(hitSoundId);
                    bullet = null;
                }
                
                // Check collision with enemy
                if (enemyTank != null && bullet.getBounds().intersect(enemyTank.getBounds())) {
                    playSound(explosionSoundId);
                    enemiesDestroyed++;
                    
                    // Check if level complete
                    if (enemiesDestroyed >= ENEMIES_PER_LEVEL) {
                        currentLevel++;
                        if (currentLevel > 35) {
                            // Game completed all levels
                            playSound(gameOverSoundId);
                            initGame();
                            currentLevel = 1;
                        } else {
                            // Load next level
                            initGame();
                        }
                    } else {
                        // Spawn new enemy
                        enemyTank = createEnemyTank();
                    }
                    bullet = null;
                }
                
                // Remove bullet if out of bounds
                if (bullet != null && bullet.isOutOfBounds(getWidth(), getHeight())) {
                    bullet = null;
                }
            }
            
            // Random enemy fire
            if (enemyTank != null && Math.random() < 0.02) {
                // Enemy fires - could add enemy bullet logic here
            }
        }

        private void render(Canvas canvas) {
            // Clear screen
            canvas.drawColor(Color.BLACK);
            
            // Draw map
            if (map != null) {
                map.draw(canvas, paint);
            }
            
            // Draw player tank
            if (playerTank != null) {
                playerTank.draw(canvas, paint);
            }
            
            // Draw enemy tank
            if (enemyTank != null) {
                enemyTank.draw(canvas, paint);
            }
            
            // Draw bullet
            if (bullet != null) {
                bullet.draw(canvas, paint);
            }
            
            // Draw control hints
            paint.setColor(Color.WHITE);
            paint.setTextSize(20);
            canvas.drawText("Left: Move", 20, 30, paint);
            canvas.drawText("Right: Fire", getWidth() - 150, 30, paint);
            
            // Draw sound toggle hint
            paint.setColor(soundEnabled ? Color.GREEN : Color.RED);
            canvas.drawText("Sound: " + (soundEnabled ? "ON" : "OFF"), getWidth() / 2 - 40, 30, paint);
            
            // Draw level and enemies destroyed info
            paint.setColor(Color.YELLOW);
            canvas.drawText("Level: " + currentLevel, 20, getHeight() - 40, paint);
            canvas.drawText("Enemies: " + enemiesDestroyed + "/" + ENEMIES_PER_LEVEL, getWidth() - 200, getHeight() - 40, paint);
        }
    }

    // Tank class
    class Tank {
        public static final int DIRECTION_UP = 0;
        public static final int DIRECTION_DOWN = 1;
        public static final int DIRECTION_LEFT = 2;
        public static final int DIRECTION_RIGHT = 3;
        
        private float x, y;
        private int direction;
        private int color;
        private boolean isMoving;
        private float speed;
        private Rect bounds;
        
        public Tank(float x, float y, int color) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.direction = DIRECTION_UP;
            this.isMoving = false;
            this.speed = 3;
            this.bounds = new Rect(0, 0, 40, 40);
        }
        
        public void setDirection(int direction) {
            this.direction = direction;
        }
        
        public void setMoving(boolean moving) {
            isMoving = moving;
        }
        
        public boolean isMoving() {
            return isMoving;
        }
        
        public Rect getBounds() {
            bounds.set((int)x, (int)y, (int)x + 40, (int)y + 40);
            return bounds;
        }
        
        public void update(Map map) {
            float newX = x;
            float newY = y;
            
            switch (direction) {
                case DIRECTION_UP:
                    newY -= speed;
                    break;
                case DIRECTION_DOWN:
                    newY += speed;
                    break;
                case DIRECTION_LEFT:
                    newX -= speed;
                    break;
                case DIRECTION_RIGHT:
                    newX += speed;
                    break;
            }
            
            // Check collision with map boundaries and walls
            Rect newBounds = new Rect((int)newX, (int)newY, (int)newX + 40, (int)newY + 40);
            if (!map.checkCollision(newBounds)) {
                x = newX;
                y = newY;
            }
        }
        
        public void updateAI(Map map) {
            // Simple AI: move randomly
            if (Math.random() < 0.02) {
                direction = (int)(Math.random() * 4);
            }
        }
        
        public Bullet fire() {
            float bx = x + 20;
            float by = y + 20;
            return new Bullet(bx, by, direction);
        }
        
        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            
            // Draw tank body
            Rect tankRect = new Rect((int)x, (int)y, (int)x + 40, (int)y + 40);
            canvas.drawRect(tankRect, paint);
            
            // Draw cannon
            paint.setStrokeWidth(5);
            float cannonEndX = x + 20;
            float cannonEndY = y + 20;
            
            switch (direction) {
                case DIRECTION_UP:
                    cannonEndY = y - 10;
                    break;
                case DIRECTION_DOWN:
                    cannonEndY = y + 50;
                    break;
                case DIRECTION_LEFT:
                    cannonEndX = x - 10;
                    break;
                case DIRECTION_RIGHT:
                    cannonEndX = x + 50;
                    break;
            }
            
            canvas.drawLine(x + 20, y + 20, cannonEndX, cannonEndY, paint);
        }
    }

    // Bullet class
    class Bullet {
        private float x, y;
        private int direction;
        private float speed;
        private Rect bounds;
        
        public Bullet(float x, float y, int direction) {
            this.x = x;
            this.y = y;
            this.direction = direction;
            this.speed = 8;
            this.bounds = new Rect(0, 0, 8, 8);
        }
        
        public Rect getBounds() {
            bounds.set((int)x - 4, (int)y - 4, (int)x + 4, (int)y + 4);
            return bounds;
        }
        
        public void update() {
            switch (direction) {
                case DIRECTION_UP:
                    y -= speed;
                    break;
                case DIRECTION_DOWN:
                    y += speed;
                    break;
                case DIRECTION_LEFT:
                    x -= speed;
                    break;
                case DIRECTION_RIGHT:
                    x += speed;
                    break;
            }
        }
        
        public boolean isOutOfBounds(int screenWidth, int screenHeight) {
            return x < 0 || x > screenWidth || y < 0 || y > screenHeight;
        }
        
        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(Color.YELLOW);
            canvas.drawCircle(x, y, 4, paint);
        }
    }

    // Map class
    class Map {
        private Rect[] walls;
        private int level;
        
        // Original Battle City level layouts (35 levels)
        // Each level is represented as a string array where:
        // '#' = brick wall, '@' = steel wall, 'B' = base (eagle), '~' = water, '=' = trees
        private static final String[][] LEVELS = {
            // Level 1
            {
                "                                        ",
                "                                        ",
                "    ####        ####        ####        ",
                "    ####        ####        ####        ",
                "    ####        ####        ####        ",
                "    ####        ####        ####        ",
                "                                      @ ",
                "    ####      ######      ####        @ ",
                "    ####      ######      ####        @ ",
                "              ######                  @ ",
                "    ####  @@  ######  @@  ####          ",
                "    ####  @@  ######  @@  ####          ",
                "    ####  @@  ######  @@  ####          ",
                "              ######                  @ ",
                "    ####      ######      ####        @ ",
                "    ####      ######      ####        @ ",
                "                                      @ ",
                "        ####                ####        ",
                "        ####                ####        ",
                "        ####    ####@@####  ####        ",
                "        ####    ####@@####  ####        ",
                "        ####    ##########  ####        ",
                "              ##########              B ",
                "        ####    ##########  ####      BB ",
                "        ####    ####  ####  ####      BB ",
                "        ####    ####  ####  ####        ",
                "              ####  ####                ",
                "        ####  ####    ####  ####        ",
                "        ####  ####    ####  ####        ",
                "              ####    ####              ",
                "                                        "
            },
            // Level 2
            {
                "                                        ",
                "                                        ",
                "    @@@@@@          @@@@@@              ",
                "    @@@@@@          @@@@@@              ",
                "    @@@@@@          @@@@@@              ",
                "                                      @ ",
                "    ####          ####          #     @ ",
                "    ####          ####          #     @ ",
                "    ####    @@    ####    @@    #       ",
                "    ####    @@    ####    @@    #       ",
                "            @@            @@    #       ",
                "    ####    @@    ####    @@    #       ",
                "    ####    @@    ####    @@    #       ",
                "                                      @ ",
                "    ####          ####          #     @ ",
                "    ####          ####          #     @ ",
                "                                      @ ",
                "        @@@@                @@@@        ",
                "        @@@@                @@@@        ",
                "        @@@@    @@@@@@@@@@  @@@@        ",
                "        @@@@    @@@@@@@@@@  @@@@        ",
                "        @@@@    @@@@@@@@@@  @@@@        ",
                "                @@@@@@@@@@            B ",
                "        @@@@    @@@@@@@@@@  @@@@      BB ",
                "        @@@@    @@@@  @@@@  @@@@      BB ",
                "        @@@@    @@@@  @@@@  @@@@        ",
                "                @@@@  @@@@              ",
                "        @@@@  @@@@    @@@@  @@@@        ",
                "        @@@@  @@@@    @@@@  @@@@        ",
                "                @@@@    @@@@            ",
                "                                        "
            },
            // Add more levels as needed - using simplified patterns for demo
            // Levels 3-35 would follow similar patterns
        };
        
        public Map(int levelNum) {
            this.level = levelNum;
            loadLevel(levelNum);
        }
        
        private void loadLevel(int levelNum) {
            // Create walls list
            java.util.ArrayList<Rect> wallList = new java.util.ArrayList<>();
            
            // Get level data or generate if not defined
            String[] levelData = null;
            if (levelNum <= LEVELS.length && LEVELS[levelNum - 1] != null) {
                levelData = LEVELS[levelNum - 1];
            }
            
            if (levelData != null) {
                // Parse level data
                int cellSize = 40; // Size of each grid cell
                for (int row = 0; row < levelData.length; row++) {
                    String line = levelData[row];
                    for (int col = 0; col < line.length(); col++) {
                        char c = line.charAt(col);
                        if (c == '#' || c == '@') { // Brick or steel wall
                            wallList.add(new Rect(col * cellSize, row * cellSize, 
                                col * cellSize + cellSize, row * cellSize + cellSize));
                        }
                    }
                }
            } else {
                // Generate procedural level for undefined levels
                generateProceduralLevel(wallList, levelNum);
            }
            
            walls = wallList.toArray(new Rect[0]);
        }
        
        private void generateProceduralLevel(java.util.ArrayList<Rect> wallList, int levelNum) {
            // Generate symmetric patterns based on level number
            int cellSize = 40;
            int seed = levelNum * 7;
            
            // Create border walls
            for (int i = 0; i < 10; i++) {
                wallList.add(new Rect(i * cellSize, 2 * cellSize, i * cellSize + cellSize, 3 * cellSize));
                wallList.add(new Rect(i * cellSize, 5 * cellSize, i * cellSize + cellSize, 6 * cellSize));
            }
            
            // Create center obstacles
            for (int i = 3; i < 8; i++) {
                if ((i + seed) % 3 == 0) {
                    wallList.add(new Rect(i * cellSize, 8 * cellSize, i * cellSize + cellSize, 10 * cellSize));
                    wallList.add(new Rect(i * cellSize, 12 * cellSize, i * cellSize + cellSize, 14 * cellSize));
                }
            }
            
            // Create side barriers
            wallList.add(new Rect(2 * cellSize, 10 * cellSize, 3 * cellSize, 14 * cellSize));
            wallList.add(new Rect(12 * cellSize, 10 * cellSize, 13 * cellSize, 14 * cellSize));
            
            // Base protection
            wallList.add(new Rect(9 * cellSize, 18 * cellSize, 10 * cellSize, 19 * cellSize));
            wallList.add(new Rect(11 * cellSize, 18 * cellSize, 12 * cellSize, 19 * cellSize));
        }
        
        public boolean checkCollision(Rect rect) {
            // Check boundaries
            if (rect.left < 0 || rect.right > 800 || rect.top < 0 || rect.bottom > 600) {
                return true;
            }
            
            // Check walls
            for (Rect wall : walls) {
                if (Rect.intersects(rect, wall)) {
                    return true;
                }
            }
            
            return false;
        }
        
        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(Color.GRAY);
            for (Rect wall : walls) {
                canvas.drawRect(wall, paint);
            }
        }
    }
}

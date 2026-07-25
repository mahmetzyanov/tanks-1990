package com.tanks1990.game;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
        
        // Control buttons
        private Button btnUp, btnDown, btnLeft, btnRight, btnFire;
        private Paint paint;
        
        // Touch control areas
        private Rect leftControlArea;
        private Rect rightControlArea;
        private float touchStartX, touchStartY;

        public GameView(Context context) {
            super(context);
            holder = getHolder();
            holder.addCallback(this);
            
            paint = new Paint();
            paint.setAntiAlias(true);
            
            // Initialize game objects
            initGame();
            
            // Setup touch controls
            setupTouchControls();
        }

        private void initGame() {
            // Create player tank at bottom center
            playerTank = new Tank(200, 400, Color.GREEN);
            
            // Create enemy tank
            enemyTank = new Tank(400, 100, Color.RED);
            
            // Create map
            map = new Map();
            
            bullet = null;
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
                            }
                        }
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
            }
            
            // Update enemy tank (simple AI)
            enemyTank.updateAI(map);
            enemyTank.update(map);
            
            // Update bullet
            if (bullet != null) {
                bullet.update();
                
                // Check collision with walls
                if (map.checkCollision(bullet.getBounds())) {
                    bullet = null;
                }
                
                // Check collision with enemy
                if (enemyTank != null && bullet.getBounds().intersect(enemyTank.getBounds())) {
                    enemyTank = new Tank(400, 100, Color.RED);
                    bullet = null;
                }
                
                // Remove bullet if out of bounds
                if (bullet != null && bullet.isOutOfBounds(getWidth(), getHeight())) {
                    bullet = null;
                }
            }
            
            // Random enemy fire
            if (Math.random() < 0.01 && enemyTank != null) {
                // Enemy fires
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
        
        public Map() {
            // Create some walls
            walls = new Rect[10];
            walls[0] = new Rect(100, 100, 150, 200);
            walls[1] = new Rect(300, 150, 350, 250);
            walls[2] = new Rect(500, 100, 550, 200);
            walls[3] = new Rect(200, 300, 300, 350);
            walls[4] = new Rect(400, 300, 500, 350);
            
            for (int i = 5; i < 10; i++) {
                walls[i] = new Rect(
                    (int)(Math.random() * 600),
                    (int)(Math.random() * 400),
                    (int)(Math.random() * 600) + 50,
                    (int)(Math.random() * 400) + 50
                );
            }
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

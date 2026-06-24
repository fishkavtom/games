package com.ozonwitch.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class WitchGameView extends View {
    private static final int STATE_START = 0;
    private static final int STATE_PLAYING = 1;
    private static final int STATE_GAME_OVER = 2;

    // Дистанция (в "экранах"), за которую сложность растёт от 0 до 1.
    private static final float DIFFICULTY_SCREENS = 70f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final List<Parcel> parcels = new ArrayList<>();
    private final List<LostItem> lostItems = new ArrayList<>();
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<Customer> customers = new ArrayList<>();
    private final List<Spell> spells = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final List<Cloud> clouds = new ArrayList<>();
    private final float[] starsX = new float[42];
    private final float[] starsY = new float[42];
    private final float[] starsSize = new float[42];
    private final RectF playButton = new RectF();
    private final SharedPreferences preferences;
    private final Bitmap splashArt;
    private final Bitmap witchIdleSheet;
    private final Bitmap witchWalkSheet;
    private final Bitmap witchShotSheet;
    private final Bitmap witchVictorySheet;
    private final Bitmap frogSheet;
    private final Bitmap crateSheet;
    private ToneGenerator tone;

    private int state = STATE_START;
    private int score;
    private float scoreFloat;
    private int highScore;
    private int lives = 3;
    private int packagesCollected;
    private float witchX;
    private float witchY;
    private float targetX;
    private float targetY;
    private float worldTime;
    private float parcelTimer;
    private float lostItemTimer;
    private float obstacleTimer;
    private float customerTimer;
    private float fireCooldown;
    private float invulnerableTimer;
    private float flashTimer;
    private float distance;
    private float speedFactor = 1f;
    private float hintTimer;
    private float trailTimer;
    private float shotAnimationTimer;
    private float celebrationTimer;
    private float penaltyTextTimer;
    private float jumpVelocity;
    private int nextCelebrateScore = 100;
    private boolean onGround = true;
    private long lastFrameNanos;
    private boolean loopRunning = true;

    // Мультитач: палец слева управляет полётом, палец справа стреляет.
    private int steerPointerId = -1;
    private int firePointerId = -1;
    private float steerX;
    private boolean firing;

    public WitchGameView(Context context) {
        super(context);
        preferences = context.getSharedPreferences("ozon_witch", Context.MODE_PRIVATE);
        highScore = preferences.getInt("high_score", 0);
        splashArt = BitmapFactory.decodeResource(getResources(), R.drawable.splash_art);
        witchIdleSheet = BitmapFactory.decodeResource(getResources(), R.drawable.witch_idle);
        witchWalkSheet = BitmapFactory.decodeResource(getResources(), R.drawable.witch_walk);
        witchShotSheet = BitmapFactory.decodeResource(getResources(), R.drawable.witch_shot);
        witchVictorySheet = BitmapFactory.decodeResource(getResources(), R.drawable.witch_victory);
        frogSheet = BitmapFactory.decodeResource(getResources(), R.drawable.frog_green);
        crateSheet = BitmapFactory.decodeResource(getResources(), R.drawable.warehouse_crates);
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 38);
        } catch (RuntimeException e) {
            tone = null; // На части устройств ToneGenerator недоступен — играем без звука.
        }
        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        setFocusable(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        for (int i = 0; i < 10; i++) {
            clouds.add(new Cloud(random.nextFloat(), random.nextFloat() * 0.62f,
                    0.65f + random.nextFloat() * 1.15f,
                    0.018f + random.nextFloat() * 0.025f));
        }
        Random fixed = new Random(771);
        for (int i = 0; i < starsX.length; i++) {
            starsX[i] = fixed.nextFloat();
            starsY[i] = fixed.nextFloat() * 0.55f;
            starsSize[i] = 0.6f + fixed.nextFloat() * 1.6f;
        }
    }

    private void playTone(int toneType, int durationMs) {
        if (tone != null) {
            tone.startTone(toneType, durationMs);
        }
    }

    public void resumeLoop() {
        loopRunning = true;
        lastFrameNanos = 0L;
        postInvalidateOnAnimation();
    }

    public void pauseLoop() {
        loopRunning = false;
    }

    private void startGame() {
        parcels.clear();
        lostItems.clear();
        obstacles.clear();
        customers.clear();
        spells.clear();
        sparks.clear();
        score = 0;
        scoreFloat = 0f;
        packagesCollected = 0;
        lives = 3;
        worldTime = 0f;
        parcelTimer = 0.45f;
        lostItemTimer = 3.2f;
        obstacleTimer = 3.8f;
        customerTimer = 1.8f;
        fireCooldown = 0f;
        invulnerableTimer = 0f;
        flashTimer = 0f;
        distance = 0f;
        speedFactor = 1f;
        shotAnimationTimer = 0f;
        celebrationTimer = 0f;
        penaltyTextTimer = 0f;
        jumpVelocity = 0f;
        onGround = true;
        nextCelebrateScore = 100;
        hintTimer = 4.5f;
        steerPointerId = -1;
        firePointerId = -1;
        firing = false;
        witchX = getWidth() * 0.22f;
        targetX = witchX;
        witchY = getHeight() - 55f * (getHeight() / 430f);
        targetY = witchY;
        steerX = getWidth() * 0.25f;
        state = STATE_PLAYING;
        playTone(ToneGenerator.TONE_PROP_ACK, 90);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L
                ? 0f
                : Math.min(0.033f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;

        if (loopRunning) {
            update(delta);
        }

        if (state == STATE_START) {
            drawStartScreen(canvas);
        } else {
            drawWorld(canvas);
            if (state == STATE_GAME_OVER) {
                drawGameOver(canvas);
            }
        }

        if (loopRunning) {
            postInvalidateOnAnimation();
        }
    }

    private float difficulty() {
        if (getWidth() == 0) {
            return 0f;
        }
        return Math.min(1f, distance / (getWidth() * DIFFICULTY_SCREENS));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private void update(float delta) {
        worldTime += delta;
        updateClouds(delta);
        updateSparks(delta);
        if (state != STATE_PLAYING || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        float unit = getHeight() / 430f;
        float difficulty = difficulty();

        // Скорость: база растёт со сложностью, игрок добавляет/убавляет пальцем слева.
        float targetFactor = 1f + difficulty * 0.28f;
        speedFactor += (targetFactor - speedFactor) * (1f - (float) Math.exp(-delta * 3f));
        float speed = getWidth() * (0.22f + 0.17f * difficulty) * speedFactor;
        distance += speed * delta;

        // Пассивные очки за полёт: быстрее летишь — быстрее капают.
        scoreFloat += delta * (3f + 7f * difficulty) * speedFactor;
        if (scoreFloat >= 1f) {
            int gained = (int) scoreFloat;
            score += gained;
            scoreFloat -= gained;
        }

        parcelTimer -= delta;
        lostItemTimer -= delta;
        obstacleTimer -= delta;
        customerTimer -= delta;
        hintTimer = Math.max(0f, hintTimer - delta);
        fireCooldown = Math.max(0f, fireCooldown - delta);
        invulnerableTimer = Math.max(0f, invulnerableTimer - delta);
        flashTimer = Math.max(0f, flashTimer - delta);
        shotAnimationTimer = Math.max(0f, shotAnimationTimer - delta);
        celebrationTimer = Math.max(0f, celebrationTimer - delta);
        penaltyTextTimer = Math.max(0f, penaltyTextTimer - delta);

        // Автострельба, пока палец удерживается на правой половине.
        if (firing && fireCooldown <= 0f) {
            shoot();
        }

        float follow = 1f - (float) Math.exp(-delta * 10f);
        witchX += (targetX - witchX) * follow;
        witchX = clamp(witchX, 72f * unit, getWidth() * 0.48f);
        updateJump(delta, unit);

        // Магический след за метлой.
        if (score >= nextCelebrateScore) {
            celebrationTimer = 1.25f;
            nextCelebrateScore += 100;
            playTone(ToneGenerator.TONE_PROP_ACK, 150);
            burst(witchX, witchY - 25f * unit, 0xFFFFE56B, 22, unit);
        }

        if (parcelTimer <= 0f) {
            spawnParcels(unit);
            parcelTimer = lerp(1.35f, 0.65f, difficulty) + random.nextFloat() * 0.5f;
        }
        if (lostItemTimer <= 0f) {
            spawnLostItem(unit);
            lostItemTimer = lerp(4.4f, 2.8f, difficulty) + random.nextFloat() * 1.2f;
        }
        if (obstacleTimer <= 0f) {
            spawnObstacle(unit);
            obstacleTimer = lerp(4.2f, 2.5f, difficulty) + random.nextFloat() * 1.2f;
        }
        if (customerTimer <= 0f) {
            spawnCustomer(unit, difficulty);
            customerTimer = lerp(2.3f, 0.9f, difficulty) + random.nextFloat() * 0.6f;
        }

        updateParcels(delta, speed, unit);
        updateLostItems(delta, speed, unit);
        updateObstacles(delta, speed, unit);
        updateCustomers(delta, speed, unit);
        updateSpells(delta, unit);
    }

    private void updateClouds(float delta) {
        for (Cloud cloud : clouds) {
            cloud.x -= cloud.speed * delta * speedFactor;
            if (cloud.x < -0.22f) {
                cloud.x = 1.18f;
                cloud.y = random.nextFloat() * 0.62f;
            }
        }
    }

    private void updateParcels(float delta, float speed, float unit) {
        RectF witchHit = witchHitBox(unit);
        for (int i = parcels.size() - 1; i >= 0; i--) {
            Parcel parcel = parcels.get(i);
            parcel.x -= speed * delta;
            parcel.phase += delta * 3.4f;
            RectF hit = new RectF(parcel.x - 19f * unit, parcel.y - 17f * unit,
                    parcel.x + 19f * unit, parcel.y + 17f * unit);
            if (RectF.intersects(witchHit, hit)) {
                score += 10;
                packagesCollected++;
                burst(parcel.x, parcel.y, 0xFF55E6FF, 16, unit);
                playTone(ToneGenerator.TONE_PROP_BEEP2, 55);
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                parcels.remove(i);
            } else if (parcel.x < -45f * unit) {
                parcels.remove(i);
            }
        }
    }

    private void updateJump(float delta, float unit) {
        float groundY = getHeight() - 55f * unit;
        if (!onGround) {
            jumpVelocity += 760f * unit * delta;
            witchY += jumpVelocity * delta;
            if (witchY >= groundY) {
                witchY = groundY;
                jumpVelocity = 0f;
                onGround = true;
            }
        } else {
            witchY = groundY;
        }
    }

    private void jump() {
        if (state == STATE_PLAYING && onGround) {
            float unit = getHeight() / 430f;
            jumpVelocity = -390f * unit;
            onGround = false;
            playTone(ToneGenerator.TONE_PROP_BEEP, 45);
        }
    }

    private void updateLostItems(float delta, float speed, float unit) {
        RectF witchHit = witchHitBox(unit);
        for (int i = lostItems.size() - 1; i >= 0; i--) {
            LostItem item = lostItems.get(i);
            item.x -= speed * delta * 0.58f;
            item.y += item.fallSpeed * unit * delta;
            item.rotation += delta * item.spin;
            RectF hit = new RectF(item.x - 19f * unit, item.y - 19f * unit,
                    item.x + 19f * unit, item.y + 19f * unit);
            if (RectF.intersects(witchHit, hit)) {
                score = Math.max(0, score - 20);
                scoreFloat = 0f;
                penaltyTextTimer = 1f;
                flashTimer = 0.22f;
                burst(item.x, item.y, 0xFFFF5A73, 18, unit);
                playTone(ToneGenerator.TONE_PROP_NACK, 100);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                lostItems.remove(i);
            } else if (item.y > getHeight() - 42f * unit || item.x < -45f * unit) {
                lostItems.remove(i);
            }
        }
    }

    private void updateObstacles(float delta, float speed, float unit) {
        RectF witchHit = witchHitBox(unit);
        for (int i = obstacles.size() - 1; i >= 0; i--) {
            Obstacle obstacle = obstacles.get(i);
            obstacle.x -= speed * delta;
            RectF hit = new RectF(obstacle.x - 24f * unit,
                    getHeight() - 115f * unit,
                    obstacle.x + 24f * unit,
                    getHeight() - 43f * unit);
            if (!obstacle.hit && RectF.intersects(witchHit, hit)) {
                obstacle.hit = true;
                lives--;
                invulnerableTimer = 1.2f;
                flashTimer = 0.3f;
                burst(witchX, witchY, 0xFFFF5577, 20, unit);
                playTone(ToneGenerator.TONE_PROP_NACK, 150);
                if (lives <= 0) {
                    endGame();
                }
            }
            if (obstacle.x < -60f * unit) {
                obstacles.remove(i);
            }
        }
    }

    private void updateCustomers(float delta, float speed, float unit) {
        RectF witchHit = witchHitBox(unit);
        for (int i = customers.size() - 1; i >= 0; i--) {
            Customer customer = customers.get(i);
            customer.x -= speed * delta * (customer.frog ? 0.62f : 1f);
            customer.phase += delta;
            if (customer.frog) {
                customer.y += (float) Math.sin(customer.phase * 8f) * unit * 0.55f;
                customer.frogTimer -= delta;
            } else if (customer.driftAmp > 0f) {
                // На высокой сложности клиенты дрейфуют по вертикали — сложнее уворачиваться.
                customer.y = customer.baseY
                        + (float) Math.sin(customer.phase * customer.driftSpeed) * customer.driftAmp * unit;
                customer.y = clamp(customer.y, 90f * unit, getHeight() - 78f * unit);
            }

            if (!customer.frog && invulnerableTimer <= 0f) {
                RectF hit = new RectF(customer.x - 25f * unit, customer.y - 38f * unit,
                        customer.x + 25f * unit, customer.y + 38f * unit);
                if (RectF.intersects(witchHit, hit)) {
                    lives--;
                    invulnerableTimer = 1.5f;
                    flashTimer = 0.35f;
                    customer.frog = true;
                    customer.frogTimer = 0.9f;
                    burst(witchX, witchY, 0xFFFF5577, 24, unit);
                    playTone(ToneGenerator.TONE_PROP_NACK, 170);
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    if (lives <= 0) {
                        endGame();
                    }
                }
            }

            if (customer.x < -70f * unit || customer.frogTimer < -1f) {
                customers.remove(i);
            }
        }
    }

    private void updateSpells(float delta, float unit) {
        for (int i = spells.size() - 1; i >= 0; i--) {
            Spell spell = spells.get(i);
            spell.x += getWidth() * 0.72f * delta;
            spell.rotation += delta * 480f;
            // Лёгкий искристый след за снарядом.
            if (random.nextFloat() < 0.45f) {
                sparks.add(new Spark(spell.x - 10f * unit, spell.y,
                        -60f * unit, (random.nextFloat() - 0.5f) * 40f * unit,
                        0xFFFFE56B, (1f + random.nextFloat() * 2f) * unit,
                        0.18f + random.nextFloat() * 0.15f, false));
            }
            boolean used = false;
            for (Customer customer : customers) {
                if (customer.frog) {
                    continue;
                }
                float dx = spell.x - customer.x;
                float dy = spell.y - customer.y;
                float radius = 32f * unit;
                if (dx * dx + dy * dy < radius * radius) {
                    customer.frog = true;
                    customer.frogTimer = 1.6f;
                    score += 25;
                    burst(customer.x, customer.y, 0xFFB7FF4A, 25, unit);
                    playTone(ToneGenerator.TONE_PROP_ACK, 85);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    used = true;
                    break;
                }
            }
            if (used || spell.x > getWidth() + 40f * unit) {
                spells.remove(i);
            }
        }
    }

    private void updateSparks(float delta) {
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark spark = sparks.get(i);
            spark.life -= delta;
            spark.x += spark.vx * delta;
            spark.y += spark.vy * delta;
            if (spark.gravity) {
                spark.vy += 90f * delta;
            }
            if (spark.life <= 0f) {
                sparks.remove(i);
            }
        }
    }

    private void spawnParcels(float unit) {
        // Паттерны: одиночная, вертикальная пара, дуга из трёх.
        float roll = random.nextFloat();
        float baseY = getHeight() - (76f + random.nextFloat() * 42f) * unit;
        float startX = getWidth() + 55f * unit;
        if (roll < 0.5f) {
            parcels.add(new Parcel(startX, baseY, random.nextFloat() * 6f));
        } else if (roll < 0.8f) {
            for (int i = 0; i < 2; i++) {
                parcels.add(new Parcel(startX + i * 58f * unit,
                        baseY - (i == 0 ? 0f : 28f * unit),
                        random.nextFloat() * 6f));
            }
        } else {
            for (int i = 0; i < 3; i++) {
                parcels.add(new Parcel(startX + i * 56f * unit,
                        baseY - (i == 1 ? 34f * unit : 0f),
                        random.nextFloat() * 6f));
            }
        }
    }

    private void spawnLostItem(float unit) {
        float x = getWidth() * (0.24f + random.nextFloat() * 0.72f);
        lostItems.add(new LostItem(x, -30f * unit,
                55f + random.nextFloat() * 42f,
                50f + random.nextFloat() * 80f,
                random.nextInt(3)));
    }

    private void spawnObstacle(float unit) {
        obstacles.add(new Obstacle(getWidth() + 60f * unit, random.nextInt(3)));
    }

    private void spawnCustomer(float unit, float difficulty) {
        Customer customer = new Customer(getWidth() + 70f * unit,
                getHeight() - 84f * unit, random.nextFloat() * 4f);
        customer.variant = random.nextInt(4);
        customer.baseY = customer.y;
        // С ростом сложности всё больше клиентов "плавает" по вертикали.
        customers.add(customer);
    }

    private float randomY(float unit, float top, float bottom) {
        float min = top * unit;
        float max = getHeight() - bottom * unit;
        return min + random.nextFloat() * Math.max(1f, max - min);
    }

    private void shoot() {
        if (state != STATE_PLAYING || fireCooldown > 0f) {
            return;
        }
        float unit = getHeight() / 430f;
        spells.add(new Spell(witchX + 42f * unit, witchY - 28f * unit));
        fireCooldown = 0.23f;
        shotAnimationTimer = 0.34f;
        burst(witchX + 42f * unit, witchY - 28f * unit, 0xFFFFD84D, 7, unit);
        playTone(ToneGenerator.TONE_PROP_BEEP, 45);
    }

    private void endGame() {
        state = STATE_GAME_OVER;
        firing = false;
        steerPointerId = -1;
        firePointerId = -1;
        if (score > highScore) {
            highScore = score;
            preferences.edit().putInt("high_score", highScore).apply();
        }
    }

    private RectF witchHitBox(float unit) {
        return new RectF(witchX - 27f * unit, witchY - 72f * unit,
                witchX + 29f * unit, witchY + 7f * unit);
    }

    private void burst(float x, float y, int color, int count, float unit) {
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * (float) Math.PI * 2f;
            float speed = (45f + random.nextFloat() * 155f) * unit;
            sparks.add(new Spark(x, y,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    color,
                    (2f + random.nextFloat() * 4f) * unit,
                    0.30f + random.nextFloat() * 0.50f, true));
        }
    }

    // ------------------------------------------------------------------ DRAW

    private void drawStartScreen(Canvas canvas) {
        drawCoverBitmap(canvas, splashArt);
        paint.setShader(new LinearGradient(0f, 0f, getWidth() * 0.62f, 0f,
                0xE3281050, 0x00281050, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);

        float unit = getHeight() / 430f;
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(43f * unit);
        textPaint.setShadowLayer(10f * unit, 0f, 3f * unit, 0xFF120323);
        canvas.drawText("ВЕДЬМА", 45f * unit, 108f * unit, textPaint);
        textPaint.setColor(0xFF61E8FF);
        canvas.drawText("ОЗОНА", 45f * unit, 154f * unit, textPaint);
        textPaint.clearShadowLayer();

        textPaint.setTextSize(15f * unit);
        textPaint.setColor(0xFFF5EFFF);
        canvas.drawText("Лево: высота и скорость полёта", 48f * unit, 184f * unit, textPaint);
        canvas.drawText("Право: волшебный огонь", 48f * unit, 206f * unit, textPaint);

        float pulse = 1f + 0.025f * (float) Math.sin(worldTime * 4f);
        float buttonWidth = 190f * unit * pulse;
        float buttonHeight = 58f * unit * pulse;
        float left = 48f * unit;
        float top = 235f * unit;
        playButton.set(left, top, left + buttonWidth, top + buttonHeight);
        paint.setColor(0xFFF15BB5);
        paint.setShadowLayer(13f * unit, 0f, 5f * unit, 0xAAFF3AAE);
        canvas.drawRoundRect(playButton, 25f * unit, 25f * unit, paint);
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * unit);
        paint.setColor(0xDDFFFFFF);
        canvas.drawRoundRect(playButton, 25f * unit, 25f * unit, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(23f * unit);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("ЛЕТЕТЬ!", playButton.centerX(),
                playButton.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f,
                textPaint);

        if (highScore > 0) {
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setTextSize(15f * unit);
            textPaint.setColor(0xFFFFE56B);
            canvas.drawText("РЕКОРД: " + highScore, 51f * unit, 325f * unit, textPaint);
        }
    }

    private void drawCoverBitmap(Canvas canvas, Bitmap bitmap) {
        if (bitmap == null) {
            canvas.drawColor(0xFF281050);
            return;
        }
        float sourceRatio = bitmap.getWidth() / (float) bitmap.getHeight();
        float targetRatio = getWidth() / (float) getHeight();
        Rect source = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        if (sourceRatio > targetRatio) {
            int width = Math.round(bitmap.getHeight() * targetRatio);
            source.left = (bitmap.getWidth() - width) / 2;
            source.right = source.left + width;
        } else {
            int height = Math.round(bitmap.getWidth() / targetRatio);
            source.top = (bitmap.getHeight() - height) / 2;
            source.bottom = source.top + height;
        }
        canvas.drawBitmap(bitmap, source, new RectF(0f, 0f, getWidth(), getHeight()), paint);
    }

    private void drawWorld(Canvas canvas) {
        float unit = getHeight() / 430f;
        drawWarehouseBackground(canvas, unit);
        for (Parcel parcel : parcels) {
            drawParcel(canvas, parcel, unit);
        }
        for (LostItem item : lostItems) {
            drawLostItem(canvas, item, unit);
        }
        for (Obstacle obstacle : obstacles) {
            drawObstacle(canvas, obstacle, unit);
        }
        for (Customer customer : customers) {
            if (customer.frog) {
                drawFrog(canvas, customer, unit);
            } else {
                drawCustomer(canvas, customer, unit);
            }
        }
        for (Spell spell : spells) {
            drawSpell(canvas, spell, unit);
        }
        drawWitch(canvas, unit);
        drawSparks(canvas);
        drawHud(canvas, unit);
        if (hintTimer > 0f && state == STATE_PLAYING) {
            drawControlHints(canvas, unit);
        }
        if (flashTimer > 0f) {
            paint.setColor(0x55FF204E);
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        }
        if (penaltyTextTimer > 0f) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(26f * unit);
            textPaint.setColor(0xFFFF4667);
            canvas.drawText("ПОТЕРЯШКА  -20", witchX, witchY - 95f * unit, textPaint);
        }
    }

    private void drawWarehouseBackground(Canvas canvas, float unit) {
        paint.setShader(new LinearGradient(0f, 0f, 0f, getHeight(),
                new int[]{0xFF081426, 0xFF101F36, 0xFF18283E},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);

        float warehouseTop = 58f * unit;
        float groundY = getHeight() - 48f * unit;
        paint.setColor(0xFF111D30);
        canvas.drawRect(0f, warehouseTop, getWidth(), groundY, paint);
        paint.setColor(0xFF0B63CE);
        canvas.drawRect(0f, warehouseTop, getWidth(), warehouseTop + 35f * unit, paint);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(22f * unit);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("ПУНКТ ВЫДАЧИ   •   СКЛАД", 24f * unit, warehouseTop + 25f * unit, textPaint);

        float rackWidth = 128f * unit;
        float rackOffset = -(distance * 0.18f) % rackWidth;
        for (float x = rackOffset - rackWidth; x < getWidth() + rackWidth; x += rackWidth) {
            paint.setColor(0xFF475569);
            canvas.drawRect(x + 8f * unit, warehouseTop + 54f * unit,
                    x + 13f * unit, groundY - 18f * unit, paint);
            canvas.drawRect(x + rackWidth - 13f * unit, warehouseTop + 54f * unit,
                    x + rackWidth - 8f * unit, groundY - 18f * unit, paint);
            for (int row = 0; row < 3; row++) {
                float shelfY = warehouseTop + (79f + row * 70f) * unit;
                paint.setColor(0xFF64748B);
                canvas.drawRect(x + 7f * unit, shelfY,
                        x + rackWidth - 7f * unit, shelfY + 6f * unit, paint);
                for (int col = 0; col < 3; col++) {
                    float boxLeft = x + (18f + col * 35f) * unit;
                    int color = (row + col) % 2 == 0 ? 0xFF1688F8 : 0xFF9B5DE5;
                    paint.setColor(color);
                    canvas.drawRoundRect(boxLeft, shelfY - 31f * unit,
                            boxLeft + 27f * unit, shelfY - 3f * unit,
                            4f * unit, 4f * unit, paint);
                    paint.setColor(0xCCFFFFFF);
                    canvas.drawRect(boxLeft + 11f * unit, shelfY - 31f * unit,
                            boxLeft + 16f * unit, shelfY - 3f * unit, paint);
                }
            }
        }

        float counterX = getWidth() * 0.72f - (distance * 0.05f % (getWidth() * 1.2f));
        paint.setColor(0xFF202D43);
        canvas.drawRoundRect(counterX, groundY - 105f * unit,
                counterX + 165f * unit, groundY, 9f * unit, 9f * unit, paint);
        paint.setColor(0xFF0B63CE);
        canvas.drawRect(counterX, groundY - 105f * unit,
                counterX + 165f * unit, groundY - 84f * unit, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(15f * unit);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("ВЫДАЧА ЗАКАЗОВ", counterX + 82f * unit,
                groundY - 89f * unit, textPaint);

        paint.setColor(0xFF5A351F);
        canvas.drawRect(0f, groundY, getWidth(), getHeight(), paint);
        float boardHeight = 18f * unit;
        for (float y = groundY; y < getHeight(); y += boardHeight) {
            int row = Math.round((y - groundY) / boardHeight);
            paint.setColor(row % 2 == 0 ? 0xFF6F4328 : 0xFF5E3822);
            canvas.drawRect(0f, y, getWidth(), y + boardHeight, paint);
            paint.setColor(0xFF382014);
            canvas.drawRect(0f, y, getWidth(), y + 2f * unit, paint);
            float plankWidth = 115f * unit;
            float offset = row % 2 == 0 ? 0f : plankWidth * 0.5f;
            for (float x = offset; x < getWidth(); x += plankWidth) {
                canvas.drawRect(x, y, x + 2f * unit, y + boardHeight, paint);
            }
        }
    }

    private void drawBackground(Canvas canvas, float unit) {
        // Закатное небо.
        paint.setShader(new LinearGradient(0f, 0f, 0f, getHeight(),
                new int[]{0xFF2A1052, 0xFF5B2B96, 0xFFB0509F, 0xFFFFA65B},
                new float[]{0f, 0.38f, 0.68f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);

        // Звёзды с мерцанием.
        for (int i = 0; i < starsX.length; i++) {
            float twinkle = 0.45f + 0.55f * (float) Math.abs(Math.sin(worldTime * 1.8f + i * 1.7f));
            paint.setColor(withAlpha(0xFFFFFFFF, twinkle * (1f - starsY[i] / 0.6f)));
            canvas.drawCircle(starsX[i] * getWidth(), starsY[i] * getHeight(),
                    starsSize[i] * unit, paint);
        }

        // Луна с кратерами и свечением.
        float moonX = getWidth() * 0.78f;
        float moonY = getHeight() * 0.18f;
        float moonR = 26f * unit;
        paint.setShader(new RadialGradient(moonX, moonY, moonR * 3.2f,
                0x55FFF5C2, 0x00FFF5C2, Shader.TileMode.CLAMP));
        canvas.drawCircle(moonX, moonY, moonR * 3.2f, paint);
        paint.setShader(null);
        paint.setColor(0xFFFFF3C9);
        canvas.drawCircle(moonX, moonY, moonR, paint);
        paint.setColor(0xFFEFE0A6);
        canvas.drawCircle(moonX - moonR * 0.3f, moonY - moonR * 0.15f, moonR * 0.22f, paint);
        canvas.drawCircle(moonX + moonR * 0.32f, moonY + moonR * 0.3f, moonR * 0.16f, paint);
        canvas.drawCircle(moonX + moonR * 0.1f, moonY - moonR * 0.45f, moonR * 0.12f, paint);

        // Облака (дальний слой).
        for (Cloud cloud : clouds) {
            float x = cloud.x * getWidth();
            float y = cloud.y * getHeight();
            float size = 30f * unit * cloud.scale;
            paint.setColor(0x46FFFFFF);
            canvas.drawCircle(x, y, size * 0.55f, paint);
            canvas.drawCircle(x + size * 0.55f, y - size * 0.15f, size * 0.72f, paint);
            canvas.drawCircle(x + size * 1.18f, y, size * 0.52f, paint);
            canvas.drawRoundRect(x - size * 0.25f, y, x + size * 1.45f,
                    y + size * 0.48f, size * 0.25f, size * 0.25f, paint);
        }

        float groundY = getHeight() - 46f * unit;

        // Дальние холмы (медленный параллакс).
        paint.setColor(0xFF3A2A6E);
        Path hills = new Path();
        hills.moveTo(0f, groundY);
        float hillW = 220f * unit;
        float hillOffset = -(distance * 0.06f) % hillW;
        for (float x = hillOffset - hillW; x < getWidth() + hillW; x += hillW) {
            hills.quadTo(x + hillW * 0.5f, groundY - 70f * unit, x + hillW, groundY);
        }
        hills.lineTo(getWidth(), getHeight());
        hills.lineTo(0f, getHeight());
        hills.close();
        canvas.drawPath(hills, paint);

        // Дальний ряд зданий (средний параллакс, силуэты).
        float farW = 58f * unit;
        float farOffset = -(distance * 0.14f) % farW;
        paint.setColor(0xFF34255F);
        for (float x = farOffset - farW; x < getWidth() + farW; x += farW) {
            float seed = Math.abs((x + distance * 0.14f) / farW);
            float h = (52f + (seed % 4f) * 14f) * unit;
            canvas.drawRect(x, groundY - h, x + farW - 4f * unit, groundY, paint);
        }

        // Земля.
        paint.setColor(0xFF2B2052);
        canvas.drawRect(0f, groundY, getWidth(), getHeight(), paint);

        // Ближний ряд зданий с окнами (быстрый параллакс).
        float buildingWidth = 78f * unit;
        float offset = -(distance * 0.32f) % buildingWidth;
        for (float x = offset - buildingWidth; x < getWidth() + buildingWidth; x += buildingWidth) {
            float seed = Math.abs((x + distance * 0.32f) / buildingWidth);
            int seedInt = (int) seed;
            float height = (36f + (seed % 3f) * 17f) * unit;
            paint.setColor((seedInt % 2 == 0) ? 0xFF4A3976 : 0xFF403269);
            canvas.drawRoundRect(x, groundY - height, x + buildingWidth - 6f * unit,
                    groundY + 5f * unit, 6f * unit, 6f * unit, paint);
            // Крыша.
            paint.setColor(0xFF584394);
            canvas.drawRoundRect(x - 2f * unit, groundY - height - 4f * unit,
                    x + buildingWidth - 4f * unit, groundY - height + 4f * unit,
                    3f * unit, 3f * unit, paint);
            // Окна: часть горит, часть тёмная (детерминированно от seed).
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 3; col++) {
                    boolean lit = ((seedInt * 7 + row * 3 + col * 5) % 4) != 0;
                    paint.setColor(lit ? 0xFFFFD76A : 0xFF2E2257);
                    canvas.drawRoundRect(x + (11f + col * 19f) * unit,
                            groundY - height + (10f + row * 17f) * unit,
                            x + (19f + col * 19f) * unit,
                            groundY - height + (18f + row * 17f) * unit,
                            2f * unit, 2f * unit, paint);
                }
            }
        }

        // Разметка дороги бежит по земле — усиливает ощущение скорости.
        float dashW = 34f * unit;
        float dashOffset = -(distance * 0.55f) % (dashW * 2f);
        paint.setColor(0x66FFE56B);
        float roadY = getHeight() - 22f * unit;
        for (float x = dashOffset - dashW * 2f; x < getWidth() + dashW; x += dashW * 2f) {
            canvas.drawRoundRect(x, roadY, x + dashW, roadY + 4f * unit,
                    2f * unit, 2f * unit, paint);
        }
    }

    private void drawWitch(Canvas canvas, float unit) {
        if (invulnerableTimer > 0f && ((int) (invulnerableTimer * 12f) % 2 == 0)) {
            return;
        }
        Bitmap sheet;
        int frames;
        float fps;
        if (celebrationTimer > 0f) {
            sheet = witchVictorySheet;
            frames = 8;
            fps = 10f;
        } else if (shotAnimationTimer > 0f) {
            sheet = witchShotSheet;
            frames = 6;
            fps = 15f;
        } else if (!onGround) {
            sheet = witchIdleSheet;
            frames = 5;
            fps = 6f;
        } else {
            sheet = witchWalkSheet;
            frames = 8;
            fps = 11f;
        }
        int frame = Math.min(frames - 1, (int) (worldTime * fps) % frames);
        int frameWidth = sheet.getWidth() / frames;
        Rect source = new Rect(frame * frameWidth, 0, (frame + 1) * frameWidth, sheet.getHeight());
        float width = 110f * unit;
        float height = 146f * unit;
        RectF destination = new RectF(witchX - width * 0.5f, witchY - height * 0.93f,
                witchX + width * 0.5f, witchY + height * 0.07f);
        paint.setColor(Color.WHITE);
        canvas.save();
        canvas.scale(-1f, 1f, witchX, witchY);
        canvas.drawBitmap(sheet, source, destination, paint);
        canvas.restore();
    }

    private void drawLegacyWitch(Canvas canvas, float unit) {
        if (invulnerableTimer > 0f && ((int) (invulnerableTimer * 12f) % 2 == 0)) {
            return;
        }
        float x = witchX;
        float y = witchY;
        float tilt = (targetY - witchY) * 0.035f;
        canvas.save();
        canvas.rotate(clamp(tilt, -10f, 10f), x, y);

        // Мягкое магическое свечение вокруг ведьмы.
        paint.setShader(new RadialGradient(x, y, 78f * unit,
                0x2EF15BB5, 0x00F15BB5, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, 78f * unit, paint);
        paint.setShader(null);

        // Метла: древко с градиентом.
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(7f * unit);
        paint.setShader(new LinearGradient(x - 50f * unit, y + 19f * unit,
                x + 55f * unit, y + 8f * unit,
                0xFF5E3413, 0xFF9A5C2A, Shader.TileMode.CLAMP));
        canvas.drawLine(x - 50f * unit, y + 19f * unit, x + 55f * unit, y + 8f * unit, paint);
        paint.setShader(null);

        // Прутья метлы веером, шевелятся от ветра.
        paint.setStrokeWidth(3f * unit);
        for (int i = 0; i < 9; i++) {
            float sway = (float) Math.sin(worldTime * 11f + i * 0.9f) * 2.5f;
            paint.setColor(i % 2 == 0 ? 0xFFFFC75F : 0xFFE8A93F);
            canvas.drawLine(x - 51f * unit, y + 18f * unit,
                    x - (68f + i * 2f + sway) * unit,
                    y + (4f + i * 4f) * unit, paint);
        }
        // Обвязка прутьев.
        paint.setColor(0xFFD94A8C);
        paint.setStrokeWidth(5f * unit);
        canvas.drawLine(x - 46f * unit, y + 21f * unit, x - 46f * unit, y + 15f * unit, paint);

        // Плащ — двойной слой, развевается.
        float flap1 = 5f * (float) Math.sin(worldTime * 8f);
        float flap2 = 4f * (float) Math.sin(worldTime * 8f + 1.4f);
        Path capeBack = new Path();
        capeBack.moveTo(x - 11f * unit, y - 9f * unit);
        capeBack.quadTo(x - 50f * unit, y - (16f + flap2) * unit,
                x - (70f + flap1) * unit, y + (18f + flap2) * unit);
        capeBack.quadTo(x - 30f * unit, y + 30f * unit, x + 4f * unit, y + 14f * unit);
        capeBack.close();
        paint.setColor(0xFF5A1E96);
        canvas.drawPath(capeBack, paint);

        Path cape = new Path();
        cape.moveTo(x - 13f * unit, y - 6f * unit);
        cape.quadTo(x - 48f * unit, y - (10f + flap1) * unit,
                x - (63f + flap1) * unit, y + (26f + flap2 * 0.5f) * unit);
        cape.quadTo(x - 26f * unit, y + 37f * unit, x + 8f * unit, y + 18f * unit);
        cape.close();
        paint.setColor(0xFF7B2CBF);
        canvas.drawPath(cape, paint);

        // Тело.
        paint.setShader(new LinearGradient(x, y - 10f * unit, x, y + 31f * unit,
                0xFF3A2066, 0xFF241040, Shader.TileMode.CLAMP));
        canvas.drawOval(x - 16f * unit, y - 10f * unit,
                x + 22f * unit, y + 31f * unit, paint);
        paint.setShader(null);
        // Пояс с пряжкой.
        paint.setColor(0xFFF15BB5);
        canvas.drawRoundRect(x - 14f * unit, y + 6f * unit,
                x + 20f * unit, y + 12f * unit, 3f * unit, 3f * unit, paint);
        paint.setColor(0xFFFFE56B);
        canvas.drawRoundRect(x + 0f * unit, y + 5f * unit,
                x + 8f * unit, y + 13f * unit, 2f * unit, 2f * unit, paint);

        // Голова.
        paint.setColor(0xFFFFD2A8);
        canvas.drawCircle(x + 5f * unit, y - 25f * unit, 18f * unit, paint);
        // Румянец.
        paint.setColor(0x55FF7AA8);
        canvas.drawCircle(x - 3f * unit, y - 19f * unit, 4f * unit, paint);
        canvas.drawCircle(x + 14f * unit, y - 19f * unit, 4f * unit, paint);

        // Волосы — развевающиеся пряди.
        paint.setColor(0xFFFF8F3D);
        for (int i = 0; i < 5; i++) {
            float wave = (float) Math.sin(worldTime * 9f + i * 0.8f) * 3f;
            canvas.drawCircle(x - (8f + i * 5f + wave * 0.4f) * unit,
                    y - (22f - i * 5f + wave * 0.3f) * unit,
                    (8f - i * 0.8f) * unit, paint);
        }
        paint.setColor(0xFFFFA45C);
        for (int i = 0; i < 3; i++) {
            float wave = (float) Math.sin(worldTime * 9f + 2f + i) * 3f;
            canvas.drawCircle(x - (12f + i * 7f + wave * 0.5f) * unit,
                    y - (14f - i * 6f) * unit, (5f - i) * unit, paint);
        }

        // Шляпа со звездой.
        Path hat = new Path();
        float hatTip = 4f * (float) Math.sin(worldTime * 6f);
        hat.moveTo(x - 28f * unit, y - 39f * unit);
        hat.lineTo(x + 30f * unit, y - 39f * unit);
        hat.quadTo(x + 18f * unit, y - 62f * unit, x + (7f + hatTip) * unit, y - 78f * unit);
        hat.quadTo(x - 9f * unit, y - 56f * unit, x - 28f * unit, y - 39f * unit);
        hat.close();
        paint.setColor(0xFF281050);
        canvas.drawPath(hat, paint);
        paint.setColor(0xFFF15BB5);
        canvas.drawRoundRect(x - 31f * unit, y - 44f * unit,
                x + 31f * unit, y - 36f * unit, 4f * unit, 4f * unit, paint);
        drawStar(canvas, x + 1f * unit, y - 58f * unit, 5f * unit,
                worldTime * 40f, 0xFFFFE56B);

        // Глаз (моргает) и улыбка.
        float blinkPhase = worldTime % 3.2f;
        boolean blink = blinkPhase > 3.05f;
        paint.setColor(0xFF2C174A);
        if (blink) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * unit);
            canvas.drawLine(x + 7f * unit, y - 27f * unit, x + 13f * unit, y - 27f * unit, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            canvas.drawCircle(x + 10f * unit, y - 27f * unit, 2.6f * unit, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x + 11f * unit, y - 28f * unit, 0.9f * unit, paint);
            paint.setColor(0xFF2C174A);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * unit);
        canvas.drawArc(x + 5f * unit, y - 26f * unit, x + 20f * unit, y - 15f * unit,
                10f, 130f, false, paint);
        paint.setStyle(Paint.Style.FILL);

        // Рука с волшебной палочкой.
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(4f * unit);
        paint.setColor(0xFFFFD2A8);
        canvas.drawLine(x + 18f * unit, y - 5f * unit,
                x + 39f * unit, y - 13f * unit, paint);
        paint.setStrokeWidth(3f * unit);
        paint.setColor(0xFF9A5C2A);
        canvas.drawLine(x + 37f * unit, y - 13f * unit,
                x + 62f * unit, y - 26f * unit, paint);
        // Свечение на кончике палочки.
        paint.setShader(new RadialGradient(x + 64f * unit, y - 28f * unit, 14f * unit,
                0x66FFE56B, 0x00FFE56B, Shader.TileMode.CLAMP));
        canvas.drawCircle(x + 64f * unit, y - 28f * unit, 14f * unit, paint);
        paint.setShader(null);
        drawStar(canvas, x + 64f * unit, y - 28f * unit, 7f * unit,
                worldTime * 80f, 0xFFFFE56B);
        canvas.restore();
    }

    private void drawParcel(Canvas canvas, Parcel parcel, float unit) {
        float bob = (float) Math.sin(parcel.phase) * 6f * unit;
        float x = parcel.x;
        float y = parcel.y + bob;
        canvas.save();
        canvas.rotate((float) Math.sin(parcel.phase * 0.7f) * 8f, x, y);

        // Пульсирующее свечение — посылку видно издалека.
        float glow = 1f + 0.12f * (float) Math.sin(parcel.phase * 4f);
        paint.setShader(new RadialGradient(x, y, 34f * unit * glow,
                0x3355C7FF, 0x0055C7FF, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, 34f * unit * glow, paint);
        paint.setShader(null);

        // Крылышки — посылка летит сама!
        float flap = (float) Math.sin(parcel.phase * 9f) * 22f;
        paint.setColor(0xE6FFFFFF);
        canvas.save();
        canvas.rotate(-25f + flap, x - 19f * unit, y - 10f * unit);
        canvas.drawOval(x - 39f * unit, y - 17f * unit, x - 16f * unit, y - 4f * unit, paint);
        canvas.restore();
        canvas.save();
        canvas.rotate(25f - flap, x + 19f * unit, y - 10f * unit);
        canvas.drawOval(x + 16f * unit, y - 17f * unit, x + 39f * unit, y - 4f * unit, paint);
        canvas.restore();

        // Коробка с градиентом.
        paint.setShader(new LinearGradient(x, y - 18f * unit, x, y + 18f * unit,
                0xFF2D9BFF, 0xFF0F6FD6, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(x - 21f * unit, y - 18f * unit,
                x + 21f * unit, y + 18f * unit, 6f * unit, 6f * unit, paint);
        paint.setShader(null);
        // Блик на крышке.
        paint.setColor(0xFF55C7FF);
        canvas.drawRoundRect(x - 17f * unit, y - 14f * unit,
                x + 17f * unit, y - 7f * unit, 3f * unit, 3f * unit, paint);
        // Лента крест-накрест с бантом.
        paint.setColor(Color.WHITE);
        canvas.drawRect(x - 3f * unit, y - 18f * unit, x + 3f * unit, y + 18f * unit, paint);
        canvas.drawRect(x - 21f * unit, y - 3f * unit, x + 21f * unit, y + 3f * unit, paint);
        canvas.drawCircle(x, y - 18f * unit, 4f * unit, paint);
        paint.setColor(0xFFE8F6FF);
        canvas.drawOval(x - 9f * unit, y - 23f * unit, x - 1f * unit, y - 16f * unit, paint);
        canvas.drawOval(x + 1f * unit, y - 23f * unit, x + 9f * unit, y - 16f * unit, paint);
        canvas.restore();
    }

    private void drawLostItem(Canvas canvas, LostItem item, float unit) {
        canvas.save();
        canvas.rotate(item.rotation, item.x, item.y);
        int color = item.type == 0 ? 0xFFFF8A3D : item.type == 1 ? 0xFFF15BB5 : 0xFF9B5DE5;
        paint.setColor(color);
        canvas.drawRoundRect(item.x - 19f * unit, item.y - 17f * unit,
                item.x + 19f * unit, item.y + 17f * unit, 5f * unit, 5f * unit, paint);
        paint.setColor(0xEEFFFFFF);
        canvas.drawRect(item.x - 3f * unit, item.y - 17f * unit,
                item.x + 3f * unit, item.y + 17f * unit, paint);
        paint.setColor(0xFFEF3340);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(11f * unit);
        canvas.drawText("?", item.x, item.y + 4f * unit, textPaint);
        canvas.restore();
    }

    private void drawObstacle(Canvas canvas, Obstacle obstacle, float unit) {
        float ground = getHeight() - 47f * unit;
        float width = (obstacle.type == 2 ? 56f : 48f) * unit;
        float height = (obstacle.type == 1 ? 54f : 43f) * unit;
        paint.setColor(0xFF8A572F);
        canvas.drawRect(obstacle.x - width / 2f, ground - height,
                obstacle.x + width / 2f, ground, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f * unit);
        paint.setColor(0xFF4A2B18);
        canvas.drawRect(obstacle.x - width / 2f, ground - height,
                obstacle.x + width / 2f, ground, paint);
        canvas.drawLine(obstacle.x - width / 2f, ground - height,
                obstacle.x + width / 2f, ground, paint);
        canvas.drawLine(obstacle.x + width / 2f, ground - height,
                obstacle.x - width / 2f, ground, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFC98A4C);
        canvas.drawRect(obstacle.x - width / 2f + 5f * unit, ground - height + 5f * unit,
                obstacle.x + width / 2f - 5f * unit, ground - height + 10f * unit, paint);
    }

    private void drawCustomer(Canvas canvas, Customer customer, float unit) {
        float x = customer.x;
        float y = customer.y + (float) Math.sin(customer.phase * 6f) * 3f * unit;

        int[] jackets = {0xFF3A2463, 0xFF1F4D6E, 0xFF6E2547, 0xFF2E5A2B};
        int[] hairs = {0xFF5B321F, 0xFF222222, 0xFF8A5A2B, 0xFF44351F};
        int jacket = jackets[customer.variant];
        int hair = hairs[customer.variant];

        // Связка воздушных шариков, на которых клиент летит за возвратом.
        float balloonSway = (float) Math.sin(customer.phase * 3f) * 3f * unit;
        int[] balloonColors = {0xFFFF5A5A, 0xFFFFC44D, 0xFF61E8FF};
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f * unit);
        paint.setColor(0xAAFFFFFF);
        for (int b = 0; b < 3; b++) {
            float bx = x + (b - 1) * 13f * unit + balloonSway;
            float by = y - (52f + (b == 1 ? 8f : 0f)) * unit;
            canvas.drawLine(x, y - 36f * unit, bx, by + 11f * unit, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        for (int b = 0; b < 3; b++) {
            float bx = x + (b - 1) * 13f * unit + balloonSway;
            float by = y - (52f + (b == 1 ? 8f : 0f)) * unit;
            paint.setColor(balloonColors[(customer.variant + b) % 3]);
            canvas.drawOval(bx - 8f * unit, by - 11f * unit, bx + 8f * unit, by + 11f * unit, paint);
            paint.setColor(0x66FFFFFF);
            canvas.drawCircle(bx - 3f * unit, by - 4f * unit, 2.5f * unit, paint);
        }

        // Тельце.
        paint.setShader(new LinearGradient(x, y - 5f * unit, x, y + 35f * unit,
                jacket, darken(jacket), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(x - 21f * unit, y - 5f * unit,
                x + 21f * unit, y + 35f * unit, 10f * unit, 10f * unit, paint);
        paint.setShader(null);

        // Ножки болтаются в воздухе.
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(5f * unit);
        paint.setColor(darken(jacket));
        float legSwing = (float) Math.sin(customer.phase * 7f) * 4f;
        canvas.drawLine(x - 9f * unit, y + 33f * unit,
                x - (11f + legSwing) * unit, y + 46f * unit, paint);
        canvas.drawLine(x + 9f * unit, y + 33f * unit,
                x + (11f - legSwing) * unit, y + 46f * unit, paint);

        // Голова.
        paint.setColor(0xFFFFC69D);
        canvas.drawCircle(x, y - 21f * unit, 19f * unit, paint);
        // Волосы.
        paint.setColor(hair);
        canvas.drawArc(x - 19f * unit, y - 41f * unit, x + 19f * unit, y - 5f * unit,
                185f, 175f, true, paint);

        // Сердитые брови.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * unit);
        paint.setColor(0xFF2C174A);
        canvas.drawLine(x - 12f * unit, y - 30f * unit, x - 3f * unit, y - 27f * unit, paint);
        canvas.drawLine(x + 12f * unit, y - 30f * unit, x + 3f * unit, y - 27f * unit, paint);
        paint.setStyle(Paint.Style.FILL);
        // Глаза.
        canvas.drawCircle(x - 7f * unit, y - 23f * unit, 2.5f * unit, paint);
        canvas.drawCircle(x + 7f * unit, y - 23f * unit, 2.5f * unit, paint);
        // Кричащий рот.
        paint.setColor(0xFF7A2230);
        float mouth = 4f + 2f * (float) Math.sin(customer.phase * 10f);
        canvas.drawOval(x - 5f * unit, y - 14f * unit,
                x + 5f * unit, y - (14f - mouth) * unit + mouth * unit, paint);

        // Машет кулаком.
        paint.setStrokeWidth(5f * unit);
        paint.setColor(0xFFFFC69D);
        float wave = (float) Math.sin(customer.phase * 9f) * 8f;
        canvas.drawLine(x - 17f * unit, y + 5f * unit,
                x - 30f * unit, y - (2f + wave) * unit, paint);
        canvas.drawLine(x + 17f * unit, y + 5f * unit,
                x + 31f * unit, y - 8f * unit, paint);
        paint.setColor(0xFFFFB585);
        canvas.drawCircle(x - 30f * unit, y - (2f + wave) * unit, 4.5f * unit, paint);

        // Пузырь "ВОЗВРАТ!" — пульсирует и не вылезает за верх экрана.
        float pulse = 1f + 0.06f * (float) Math.sin(customer.phase * 7f);
        float bubbleY = Math.max(y - 86f * unit, 26f * unit);
        float bw = 47f * unit * pulse;
        float bh = 17f * unit * pulse;
        paint.setColor(0xFFFDF8FF);
        canvas.drawRoundRect(x - bw, bubbleY - bh, x + bw, bubbleY + bh,
                12f * unit, 12f * unit, paint);
        Path tail = new Path();
        tail.moveTo(x - 5f * unit, bubbleY + bh - 2f * unit);
        tail.lineTo(x + 8f * unit, bubbleY + bh - 2f * unit);
        tail.lineTo(x + 2f * unit, bubbleY + bh + 10f * unit);
        tail.close();
        canvas.drawPath(tail, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(14f * unit * pulse);
        textPaint.setColor(0xFFC4264F);
        canvas.drawText("ВОЗВРАТ!", x, bubbleY + 5f * unit, textPaint);
    }

    private void drawFrog(Canvas canvas, Customer customer, float unit) {
        float x = customer.x;
        // Жаба скачет: приседает и подпрыгивает.
        float hop = Math.max(0f, (float) Math.sin(customer.phase * 8f));
        float y = customer.y + 16f * unit - hop * 9f * unit;
        float squash = 1f + 0.12f * (float) Math.cos(customer.phase * 8f);

        paint.setColor(0x55306020);
        canvas.drawOval(x - 27f * unit * (2f - squash), customer.y + 31f * unit,
                x + 27f * unit * (2f - squash), customer.y + 40f * unit, paint);

        canvas.save();
        canvas.scale(2f - squash, squash, x, y + 10f * unit);
        paint.setShader(new LinearGradient(x, y - 12f * unit, x, y + 20f * unit,
                0xFF8BF21A, 0xFF55B000, Shader.TileMode.CLAMP));
        canvas.drawOval(x - 26f * unit, y - 12f * unit,
                x + 26f * unit, y + 20f * unit, paint);
        paint.setShader(null);
        // Светлое брюшко.
        paint.setColor(0xFFC9FF8A);
        canvas.drawOval(x - 14f * unit, y - 1f * unit,
                x + 14f * unit, y + 17f * unit, paint);
        paint.setColor(0xFF70E000);
        canvas.drawCircle(x - 13f * unit, y - 14f * unit, 10f * unit, paint);
        canvas.drawCircle(x + 13f * unit, y - 14f * unit, 10f * unit, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x - 13f * unit, y - 15f * unit, 6f * unit, paint);
        canvas.drawCircle(x + 13f * unit, y - 15f * unit, 6f * unit, paint);
        paint.setColor(0xFF193600);
        canvas.drawCircle(x - 12f * unit, y - 15f * unit, 2.5f * unit, paint);
        canvas.drawCircle(x + 12f * unit, y - 15f * unit, 2.5f * unit, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * unit);
        canvas.drawArc(x - 11f * unit, y - 2f * unit, x + 11f * unit, y + 10f * unit,
                10f, 160f, false, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
        drawStar(canvas, x, y - 39f * unit, 6f * unit, worldTime * 100f, 0xFFFFE56B);
    }

    private void drawSpell(Canvas canvas, Spell spell, float unit) {
        paint.setShader(new RadialGradient(spell.x, spell.y, 22f * unit,
                0xCCFFF9A6, 0x00FFE56B, Shader.TileMode.CLAMP));
        canvas.drawCircle(spell.x, spell.y, 22f * unit, paint);
        paint.setShader(null);
        drawStar(canvas, spell.x, spell.y, 10f * unit, spell.rotation, 0xFFFFE56B);
        drawStar(canvas, spell.x, spell.y, 5f * unit, -spell.rotation * 1.4f, 0xFFFFFFFF);
    }

    private void drawStar(Canvas canvas, float x, float y, float radius, float rotation, int color) {
        Path star = new Path();
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(rotation - 90f + i * 36f);
            float r = i % 2 == 0 ? radius : radius * 0.42f;
            float px = x + (float) Math.cos(angle) * r;
            float py = y + (float) Math.sin(angle) * r;
            if (i == 0) {
                star.moveTo(px, py);
            } else {
                star.lineTo(px, py);
            }
        }
        star.close();
        paint.setColor(color);
        canvas.drawPath(star, paint);
    }

    private void drawSparks(Canvas canvas) {
        for (Spark spark : sparks) {
            paint.setColor(withAlpha(spark.color, Math.min(1f, spark.life * 2.3f)));
            canvas.drawCircle(spark.x, spark.y, spark.size * Math.min(1f, spark.life * 3f), paint);
        }
    }

    private void drawHud(Canvas canvas, float unit) {
        paint.setColor(0xE00A1222);
        canvas.drawRoundRect(13f * unit, 12f * unit, 257f * unit, 58f * unit,
                18f * unit, 18f * unit, paint);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(20f * unit);
        textPaint.setColor(Color.WHITE);
        canvas.drawText(String.format(Locale.US, "ОЧКИ %05d", score),
                28f * unit, 43f * unit, textPaint);
        for (int i = 0; i < 3; i++) {
            float beat = (i < lives && flashTimer > 0f)
                    ? 1f + 0.25f * (float) Math.sin(worldTime * 30f) : 1f;
            drawHeart(canvas, (176f + i * 25f) * unit, 35f * unit,
                    9f * unit * beat, i < lives ? 0xFFFF4D7A : 0xFF695777);
        }

        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(14f * unit);
        textPaint.setColor(0xFFFFF3B0);
        canvas.drawText("ПОСЫЛКИ: " + packagesCollected,
                getWidth() - 20f * unit, 31f * unit, textPaint);
        // Индикатор скорости.
        textPaint.setColor(0xFF7DEBFF);
        canvas.drawText(String.format(Locale.US, "СКОРОСТЬ x%.1f", speedFactor),
                getWidth() - 20f * unit, 52f * unit, textPaint);
    }

    private void drawControlHints(Canvas canvas, float unit) {
        float alpha = Math.min(1f, hintTimer / 1.2f) * 0.85f;
        float half = getWidth() * 0.5f;
        paint.setColor(withAlpha(0xFF061223, alpha * 0.42f));
        canvas.drawRect(0f, 0f, half, getHeight(), paint);
        paint.setColor(withAlpha(0xFF160C24, alpha * 0.42f));
        canvas.drawRect(half, 0f, getWidth(), getHeight(), paint);
        paint.setColor(withAlpha(0xFFFFFFFF, alpha * 0.35f));
        canvas.drawRect(half - 1f * unit, getHeight() * 0.25f,
                half + 1f * unit, getHeight() * 0.75f, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(15f * unit);
        textPaint.setColor(withAlpha(0xFFFFFFFF, alpha));
        canvas.drawText("ВЕДИ ВЛЕВО-ВПРАВО", half * 0.5f, getHeight() * 0.42f, textPaint);
        canvas.drawText("нажми сверху: прыжок", half * 0.5f, getHeight() * 0.50f, textPaint);
        canvas.drawText("уворачивайся от потеряшек", half * 0.5f, getHeight() * 0.58f, textPaint);
        canvas.drawText("ДЕРЖИ — ОГОНЬ", half * 1.5f, getHeight() * 0.48f, textPaint);
        drawStar(canvas, half * 1.5f, getHeight() * 0.58f, 9f * unit,
                worldTime * 60f, withAlpha(0xFFFFE56B, alpha));
    }

    private void drawHeart(Canvas canvas, float x, float y, float size, int color) {
        Path heart = new Path();
        heart.moveTo(x, y + size);
        heart.cubicTo(x - size * 1.8f, y - size * 0.1f,
                x - size * 0.8f, y - size * 1.4f, x, y - size * 0.55f);
        heart.cubicTo(x + size * 0.8f, y - size * 1.4f,
                x + size * 1.8f, y - size * 0.1f, x, y + size);
        paint.setColor(color);
        canvas.drawPath(heart, paint);
    }

    private void drawGameOver(Canvas canvas) {
        float unit = getHeight() / 430f;
        paint.setColor(0xB518082E);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        RectF card = new RectF(getWidth() * 0.25f, 65f * unit,
                getWidth() * 0.75f, getHeight() - 55f * unit);
        paint.setColor(0xF03B1B68);
        canvas.drawRoundRect(card, 28f * unit, 28f * unit, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f * unit);
        paint.setColor(0x99FFFFFF);
        canvas.drawRoundRect(card, 28f * unit, 28f * unit, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFFFE56B);
        textPaint.setTextSize(34f * unit);
        canvas.drawText("МЕТЛА ПРИЗЕМЛИЛАСЬ!", getWidth() / 2f, 128f * unit, textPaint);
        textPaint.setTextSize(22f * unit);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("Очки: " + score, getWidth() / 2f, 177f * unit, textPaint);
        canvas.drawText("Посылки: " + packagesCollected, getWidth() / 2f, 209f * unit, textPaint);
        textPaint.setTextSize(17f * unit);
        textPaint.setColor(0xFF7DEBFF);
        canvas.drawText("Рекорд: " + highScore, getWidth() / 2f, 245f * unit, textPaint);
        textPaint.setTextSize(18f * unit);
        textPaint.setColor(0xFFFFB9E3);
        canvas.drawText("Нажми экран, чтобы лететь снова",
                getWidth() / 2f, 305f * unit, textPaint);
    }

    // ------------------------------------------------------------- TOUCH

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (state == STATE_START) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                startGame();
            }
            return true;
        }
        if (state == STATE_GAME_OVER) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                startGame();
            }
            return true;
        }

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                handlePointerDown(event.getPointerId(index),
                        event.getX(index), event.getY(index));
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    if (id == steerPointerId) {
                        targetX = event.getX(i);
                        steerX = event.getX(i);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int index = event.getActionIndex();
                handlePointerUp(event.getPointerId(index));
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                steerPointerId = -1;
                firePointerId = -1;
                firing = false;
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private void handlePointerDown(int pointerId, float x, float y) {
        if (x < getWidth() * 0.5f) {
            // Левая половина: высота (вертикаль пальца) и скорость (горизонталь).
            if (steerPointerId == -1) {
                steerPointerId = pointerId;
                targetX = x;
                steerX = x;
                if (y < getHeight() * 0.55f) {
                    jump();
                }
            }
        } else {
            // Правая половина: огонь, при удержании — автострельба.
            if (firePointerId == -1) {
                firePointerId = pointerId;
                firing = true;
                shoot();
            }
        }
    }

    private void handlePointerUp(int pointerId) {
        if (pointerId == steerPointerId) {
            steerPointerId = -1;
        }
        if (pointerId == firePointerId) {
            firePointerId = -1;
            firing = false;
        }
    }

    // ------------------------------------------------------------- UTILS

    private int withAlpha(int color, float alpha) {
        int value = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (value << 24) | (color & 0x00FFFFFF);
    }

    private int darken(int color) {
        int r = (int) (((color >> 16) & 0xFF) * 0.72f);
        int g = (int) (((color >> 8) & 0xFF) * 0.72f);
        int b = (int) ((color & 0xFF) * 0.72f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void onDetachedFromWindow() {
        if (tone != null) {
            tone.release();
        }
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------- MODELS

    private static final class Parcel {
        float x;
        final float y;
        float phase;

        Parcel(float x, float y, float phase) {
            this.x = x;
            this.y = y;
            this.phase = phase;
        }
    }

    private static final class LostItem {
        float x;
        float y;
        final float fallSpeed;
        final float spin;
        final int type;
        float rotation;

        LostItem(float x, float y, float fallSpeed, float spin, int type) {
            this.x = x;
            this.y = y;
            this.fallSpeed = fallSpeed;
            this.spin = spin;
            this.type = type;
        }
    }

    private static final class Obstacle {
        float x;
        final int type;
        boolean hit;

        Obstacle(float x, int type) {
            this.x = x;
            this.type = type;
        }
    }

    private static final class Customer {
        float x;
        float y;
        float baseY;
        float phase;
        boolean frog;
        float frogTimer = 10f;
        int variant;
        float driftAmp;
        float driftSpeed = 2f;

        Customer(float x, float y, float phase) {
            this.x = x;
            this.y = y;
            this.baseY = y;
            this.phase = phase;
        }
    }

    private static final class Spell {
        float x;
        final float y;
        float rotation;

        Spell(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Spark {
        float x;
        float y;
        float vx;
        float vy;
        final int color;
        final float size;
        float life;
        final boolean gravity;

        Spark(float x, float y, float vx, float vy, int color, float size, float life, boolean gravity) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.size = size;
            this.life = life;
            this.gravity = gravity;
        }
    }

    private static final class Cloud {
        float x;
        float y;
        final float scale;
        final float speed;

        Cloud(float x, float y, float scale, float speed) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.speed = speed;
        }
    }
}

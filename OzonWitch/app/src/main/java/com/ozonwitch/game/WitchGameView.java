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
    private static final int STATE_PAUSED = 3;
    private static final int STATE_SHOP = 4;

    // Дистанция (в "экранах"), за которую сложность растёт от 0 до 1.
    private static final float DIFFICULTY_SCREENS = 70f;

    // Здоровье считаем в половинках сердец: 3 сердца = 6 половин.
    private static final int MAX_HEALTH = 6;

    // Длина одной локации в «экранах» (смена фона). Отдельно от сложности.
    private static final float ZONE_SCREENS = 8f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final List<Parcel> parcels = new ArrayList<>();
    private final List<LostItem> lostItems = new ArrayList<>();
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<Food> foodItems = new ArrayList<>();
    private final List<Drone> drones = new ArrayList<>();
    private final List<ReturnBox> returns = new ArrayList<>();
    private final List<PowerUp> powerups = new ArrayList<>();
    private final List<Customer> customers = new ArrayList<>();
    private final List<Spell> spells = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final List<FloatingText> popups = new ArrayList<>();
    private final List<Cloud> clouds = new ArrayList<>();
    private final float[] starsX = new float[42];
    private final float[] starsY = new float[42];
    private final float[] starsSize = new float[42];
    private final RectF playButton = new RectF();
    private final RectF pauseButton = new RectF();          // иконка паузы в HUD
    private final RectF restartButton = new RectF();        // иконка рестарта в HUD
    private final RectF resumeButton = new RectF();         // «Продолжить» на оверлее паузы
    private final RectF overlayRestartButton = new RectF(); // «Заново» на оверлее паузы
    private final RectF soundButton = new RectF();          // вкл/выкл звук в HUD
    private final SharedPreferences preferences;
    private final Bitmap splashArt;
    private final Bitmap witchIdleSheet;
    private final Bitmap witchWalkSheet;
    private final Bitmap witchShotSheet;
    private final Bitmap witchVictorySheet;
    private final Bitmap frogSheet;
    private final Bitmap crateSheet;
    private final Bitmap foodSheet;
    private final Bitmap customerSheet;
    private final Bitmap[] bonusIcons; // 0=щит, 1=магнит, 2=усилитель
    private ToneGenerator tone;

    private int state = STATE_START;
    private int score;
    private float scoreFloat;
    private int highScore;
    private int health = MAX_HEALTH;
    private float nextFoodDistance;
    private int packagesCollected;
    private float witchX;
    private float witchY;
    private float targetX;
    private float targetY;
    private float worldTime;
    private float parcelTimer;
    private float lostItemTimer;
    private float obstacleTimer;
    private float droneTimer;
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
    private int jumpCount;
    private int speedStage;       // ступень ускорения: +1 каждые 3% карты
    private int combo;            // подряд пойманные посылки → множитель очков
    private float shakeTimer;     // тряска экрана при уроне
    private boolean soundOn = true;

    // Бонусы-усилители.
    private float shieldTimer;
    private float magnetTimer;
    private float doubleTimer;
    private float powerupDistance;

    // Локации (зоны) по мере прохождения карты.
    private int currentZone;
    private float zoneBannerTimer;

    // Босс — курьер Озона: приезжает раз в круг локаций за возвратами.
    private boolean bossActive;
    private int bossState;        // 0 въезд, 1 бой, 2 уезжает
    private float bossX;
    private float bossY;
    private float bossPhase;
    private float bossTimer;      // до следующего броска
    private float bossStateT;
    private int bossDelivered;    // сдано возвратов
    private int bossDeliverGoal;  // сколько надо сдать
    private float bossBannerTimer;
    private int nextBossZone = 5;
    private int bossesDefeated;
    private boolean lostToBoss;   // проиграл во время боя с курьером

    // Статистика забега и рекорды.
    private int dronesDowned;
    private int returnsDeflected;
    private int runMaxCombo;
    private int comboRecord;
    private boolean newHighScore;

    // Монеты и магазин.
    private int coins;
    private int coinsEarned;     // начислено за последний забег
    private int ownShield;       // куплено зарядов
    private int ownMagnet;
    private int ownBoostX2;
    private int ownBoostX3;
    private int ownHeal;
    // Все купленные бонусы применяются вручную кнопками-инвентарём.
    private float boostTimer;    // таймер активированного усилителя очков
    private int boostMul = 2;    // x2 или x3
    private final RectF shopButton = new RectF();
    private final RectF backButton = new RectF();
    private final RectF gameOverShopButton = new RectF();
    private final RectF gameOverRestartButton = new RectF();
    private final RectF[] buyButtons = {
            new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
    // Ряд кнопок-инвентаря (показываются только купленные предметы).
    private final RectF[] invButtons = {
            new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
    private final int[] invSlotItem = new int[5];
    private int invCount;
    private int nextCelebrateScore = 100;
    private boolean onGround = true;
    private long lastFrameNanos;
    private boolean loopRunning = true;

    // Мультитач: палец слева управляет полётом, палец справа стреляет.
    private int steerPointerId = -1;
    private int firePointerId = -1;
    private float steerLastX;   // предыдущая позиция пальца — для относительного ведения
    private float steerDownX;   // точка касания — отличаем тап (прыжок) от ведения
    private boolean steerMoved; // палец заметно сдвинулся → это ведение, а не прыжок
    private boolean firing;

    public WitchGameView(Context context) {
        super(context);
        preferences = context.getSharedPreferences("ozon_witch", Context.MODE_PRIVATE);
        highScore = preferences.getInt("high_score", 0);
        comboRecord = preferences.getInt("combo_record", 0);
        coins = preferences.getInt("coins", 0);
        ownShield = preferences.getInt("own_shield", 0);
        ownMagnet = preferences.getInt("own_magnet", 0);
        ownBoostX2 = preferences.getInt("own_boost2", 0);
        ownBoostX3 = preferences.getInt("own_boost3", 0);
        ownHeal = preferences.getInt("own_heal", 0);
        soundOn = preferences.getBoolean("sound_on", true);
        splashArt = BitmapFactory.decodeResource(getResources(), R.drawable.splash_art);
        witchIdleSheet = BitmapFactory.decodeResource(getResources(), R.drawable.witch_idle);
        witchWalkSheet = BitmapFactory.decodeResource(getResources(), R.drawable.witch_walk);
        witchShotSheet = BitmapFactory.decodeResource(getResources(), R.drawable.witch_shot);
        witchVictorySheet = BitmapFactory.decodeResource(getResources(), R.drawable.witch_victory);
        frogSheet = BitmapFactory.decodeResource(getResources(), R.drawable.frog_green);
        crateSheet = BitmapFactory.decodeResource(getResources(), R.drawable.warehouse_crates);
        foodSheet = BitmapFactory.decodeResource(getResources(), R.drawable.food_sheet);
        customerSheet = BitmapFactory.decodeResource(getResources(), R.drawable.customer_walk);
        bonusIcons = new Bitmap[]{
                BitmapFactory.decodeResource(getResources(), R.drawable.bonus_shield),
                BitmapFactory.decodeResource(getResources(), R.drawable.bonus_magnet),
                BitmapFactory.decodeResource(getResources(), R.drawable.bonus_boost),
        };
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
        if (tone != null && soundOn) {
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
        // При сворачивании приложения сразу уходим на паузу — вернёшься к меню паузы.
        if (state == STATE_PLAYING) {
            state = STATE_PAUSED;
            firing = false;
            steerPointerId = -1;
            firePointerId = -1;
        }
    }

    private void startGame() {
        parcels.clear();
        lostItems.clear();
        obstacles.clear();
        foodItems.clear();
        drones.clear();
        returns.clear();
        powerups.clear();
        customers.clear();
        spells.clear();
        sparks.clear();
        popups.clear();
        score = 0;
        scoreFloat = 0f;
        packagesCollected = 0;
        combo = 0;
        shakeTimer = 0f;
        shieldTimer = 0f;
        magnetTimer = 0f;
        doubleTimer = 0f;
        boostTimer = 0f;
        // Купленные бонусы НЕ тратятся на старте — применяй их кнопками в любой момент.
        bossActive = false;
        bossBannerTimer = 0f;
        bossDelivered = 0;
        bossesDefeated = 0;
        lostToBoss = false;
        currentZone = 0;
        zoneBannerTimer = 0f;
        dronesDowned = 0;
        returnsDeflected = 0;
        runMaxCombo = 0;
        newHighScore = false;
        health = MAX_HEALTH;
        worldTime = 0f;
        parcelTimer = 0.45f;
        lostItemTimer = 3.2f;
        obstacleTimer = 3.8f;
        droneTimer = 9f;
        customerTimer = 1.8f;
        fireCooldown = 0f;
        invulnerableTimer = 0f;
        flashTimer = 0f;
        distance = 0f;
        speedFactor = 1f;
        speedStage = 0;
        powerupDistance = getWidth() * 9f;
        shotAnimationTimer = 0f;
        celebrationTimer = 0f;
        penaltyTextTimer = 0f;
        jumpVelocity = 0f;
        jumpCount = 0;
        onGround = true;
        nextCelebrateScore = 100;
        hintTimer = 4.5f;
        steerPointerId = -1;
        firePointerId = -1;
        steerMoved = false;
        firing = false;
        nextFoodDistance = getWidth() * 8f;
        witchX = getWidth() * 0.22f;
        targetX = witchX;
        witchY = getHeight() - 55f * (getHeight() / 430f);
        targetY = witchY;
        state = STATE_PLAYING;
        playTone(ToneGenerator.TONE_PROP_ACK, 90);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void pauseGame() {
        if (state == STATE_PLAYING) {
            state = STATE_PAUSED;
            firing = false;
            steerPointerId = -1;
            firePointerId = -1;
            playTone(ToneGenerator.TONE_PROP_BEEP, 40);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    private void resumeGame() {
        if (state == STATE_PAUSED) {
            state = STATE_PLAYING;
            lastFrameNanos = 0L; // следующий кадр без скачка времени
        }
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
        } else if (state == STATE_SHOP) {
            drawShop(canvas);
        } else {
            drawWorld(canvas);
            if (state == STATE_GAME_OVER) {
                drawGameOver(canvas);
            } else if (state == STATE_PAUSED) {
                drawPauseOverlay(canvas);
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
        if (state == STATE_PAUSED) {
            return; // мир заморожен, рисуем оверлей паузы
        }
        worldTime += delta;
        updateClouds(delta);
        updateSparks(delta);
        if (state != STATE_PLAYING || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        float unit = getHeight() / 430f;
        float difficulty = difficulty();

        float speed;
        if (bossActive) {
            // Во время боя мир замирает — ведьма останавливается и сдаёт возвраты.
            speed = 0f;
            updateBoss(delta, unit);
        } else {
            // Локации чередуются: ПВЗ (чётные) ↔ УЛИЦА (нечётные).
            int zone = (int) (distance / (getWidth() * ZONE_SCREENS));
            if (zone != currentZone) {
                currentZone = zone;
                zoneBannerTimer = 2.6f;
                playTone(ToneGenerator.TONE_PROP_ACK, 120);
                // После каждой улицы (вход в чётную зону) приезжает курьер.
                if (currentZone % 2 == 0 && currentZone > 0) {
                    startBoss(unit);
                }
            }
            // Постоянная скорость — без «бонуса к скорости». Сложность даёт плотность спавна.
            speed = getWidth() * 0.24f;
            distance += speed * delta;
        }

        bossBannerTimer = Math.max(0f, bossBannerTimer - delta);
        hintTimer = Math.max(0f, hintTimer - delta);
        fireCooldown = Math.max(0f, fireCooldown - delta);
        invulnerableTimer = Math.max(0f, invulnerableTimer - delta);
        flashTimer = Math.max(0f, flashTimer - delta);
        shotAnimationTimer = Math.max(0f, shotAnimationTimer - delta);
        celebrationTimer = Math.max(0f, celebrationTimer - delta);
        penaltyTextTimer = Math.max(0f, penaltyTextTimer - delta);
        shakeTimer = Math.max(0f, shakeTimer - delta);
        shieldTimer = Math.max(0f, shieldTimer - delta);
        magnetTimer = Math.max(0f, magnetTimer - delta);
        doubleTimer = Math.max(0f, doubleTimer - delta);
        boostTimer = Math.max(0f, boostTimer - delta);
        zoneBannerTimer = Math.max(0f, zoneBannerTimer - delta);
        updatePopups(delta);

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

        // Обычные спавны — только вне боя с боссом.
        if (!bossActive) {
            parcelTimer -= delta;
            lostItemTimer -= delta;
            obstacleTimer -= delta;
            droneTimer -= delta;
            customerTimer -= delta;
            if (parcelTimer <= 0f) {
                spawnParcels(unit);
                parcelTimer = lerp(1.35f, 0.65f, difficulty) + random.nextFloat() * 0.5f;
            }
            if (lostItemTimer <= 0f) {
                spawnLostItem(unit);
                lostItemTimer = lerp(5.6f, 3.8f, difficulty) + random.nextFloat() * 1.4f;
                obstacleTimer = Math.max(obstacleTimer, 1.8f);
            }
            if (obstacleTimer <= 0f) {
                spawnObstacle(unit);
                obstacleTimer = lerp(4.8f, 3.0f, difficulty) + random.nextFloat() * 1.3f;
                lostItemTimer = Math.max(lostItemTimer, 1.8f);
                droneTimer = Math.max(droneTimer, 1.8f);
                customerTimer = Math.max(customerTimer, 1.8f);
            }
            if (droneTimer <= 0f) {
                spawnDrone(unit);
                droneTimer = lerp(11f, 6.5f, difficulty) + random.nextFloat() * 3f;
                obstacleTimer = Math.max(obstacleTimer, 1.8f);
                lostItemTimer = Math.max(lostItemTimer, 1.8f);
            }
            if (customerTimer <= 0f) {
                spawnCustomer(unit, difficulty);
                customerTimer = lerp(2.1f, 0.85f, difficulty) + random.nextFloat() * 0.6f;
            }
            if (distance >= nextFoodDistance) {
                spawnFood(unit);
                nextFoodDistance = distance + getWidth() * (8f + random.nextFloat() * 4f);
            }
            if (distance >= powerupDistance) {
                spawnPowerup(unit);
                powerupDistance = distance + getWidth() * (10f + random.nextFloat() * 5f);
            }
        }

        updateParcels(delta, speed, unit);
        updateLostItems(delta, speed, unit);
        updateObstacles(delta, speed, unit);
        updateFood(delta, speed, unit);
        updateDrones(delta, speed, unit);
        updateCustomers(delta, speed, unit);
        updateReturns(delta, unit);
        updatePowerups(delta, speed, unit);
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
            // Магнит: посылки сами летят к ведьме.
            if (magnetTimer > 0f) {
                float pull = 1f - (float) Math.exp(-delta * 7f);
                parcel.x += (witchX - parcel.x) * pull;
                parcel.y += (witchY - 30f * unit - parcel.y) * pull;
            }
            RectF hit = new RectF(parcel.x - 19f * unit, parcel.y - 17f * unit,
                    parcel.x + 19f * unit, parcel.y + 17f * unit);
            if (RectF.intersects(witchHit, hit)) {
                combo++;
                runMaxCombo = Math.max(runMaxCombo, combo);
                int mult = comboMultiplier();
                int gained = 10 * mult * scoreMul();
                score += gained;
                packagesCollected++;
                burst(parcel.x, parcel.y, 0xFF55E6FF, 16, unit);
                addPopup(parcel.x, parcel.y, "+" + gained, 0xFF7DEBFF, unit);
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
            // Гравитация мягче на взлёте и сильнее на спуске — дуга плавная, без рывка,
            // и ведьма приятно «зависает» в верхней точке.
            float gravity = (jumpVelocity < 0f ? 720f : 1080f) * unit;
            jumpVelocity += gravity * delta;
            witchY += jumpVelocity * delta;
            if (witchY >= groundY) {
                witchY = groundY;
                jumpVelocity = 0f;
                onGround = true;
                jumpCount = 0;
            }
        } else {
            witchY = groundY;
        }
    }

    // Двойной прыжок: тап — прыжок, ещё один тап в воздухе — мягкий доскок.
    private void jump() {
        if (state == STATE_PLAYING && jumpCount < 2) {
            float unit = getHeight() / 430f;
            // Первый прыжок сильнее, второй мягче — без резкой смены скорости.
            jumpVelocity = (jumpCount == 0 ? -360f : -300f) * unit;
            onGround = false;
            jumpCount++;
            playTone(ToneGenerator.TONE_PROP_BEEP, 45);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    private void updateLostItems(float delta, float speed, float unit) {
        RectF witchHit = witchHitBox(unit);
        float groundLine = getHeight() - 56f * unit; // здесь потеряшка лежит на полу
        for (int i = lostItems.size() - 1; i >= 0; i--) {
            LostItem item = lostItems.get(i);
            item.x -= speed * delta;            // катится навстречу с полной скоростью мира
            item.vy += 320f * delta;            // гравитация для дуги влёта
            item.y += item.vy * unit * delta;
            item.rotation += delta * item.spin;
            // Приземление и пара коротких затухающих отскоков — потеряшка остаётся низкой.
            if (item.y >= groundLine) {
                item.y = groundLine;
                if (item.bounces < 2) {
                    item.vy = -90f + item.bounces * 45f;
                    item.bounces++;
                } else {
                    item.vy = 0f;
                    item.spin *= 0.5f;
                }
            }
            // Компактный хитбокс у самого пола: грудью бежишь — заденешь, прыгнул — пролетел сверху.
            RectF hit = new RectF(item.x - 16f * unit, item.y - 16f * unit,
                    item.x + 16f * unit, item.y + 16f * unit);
            if (shieldTimer <= 0f && RectF.intersects(witchHit, hit)) {
                score = Math.max(0, score - 20);
                scoreFloat = 0f;
                combo = 0;
                penaltyTextTimer = 1f;
                flashTimer = 0.22f;
                shakeTimer = 0.3f;
                burst(item.x, item.y, 0xFFFF5A73, 18, unit);
                addPopup(item.x, item.y - 18f * unit, "-20", 0xFFFF5A73, unit);
                playTone(ToneGenerator.TONE_PROP_NACK, 100);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                lostItems.remove(i);
            } else if (item.x < -45f * unit) {
                lostItems.remove(i);
            }
        }
    }

    private void updateObstacles(float delta, float speed, float unit) {
        RectF witchHit = witchHitBox(unit);
        for (int i = obstacles.size() - 1; i >= 0; i--) {
            Obstacle obstacle = obstacles.get(i);
            obstacle.x -= speed * delta;
            RectF crate = crateRect(obstacle, unit);
            // Хитбокс чуть уже самого ящика — так перепрыгивать честнее.
            RectF hit = new RectF(crate.left + crate.width() * 0.12f,
                    crate.top + crate.height() * 0.12f,
                    crate.right - crate.width() * 0.12f,
                    crate.bottom);
            if (!obstacle.hit && !isInvulnerable() && RectF.intersects(witchHit, hit)) {
                obstacle.hit = true;
                health -= 1; // ящик снимает половину сердечка
                combo = 0;
                invulnerableTimer = 1.2f;
                flashTimer = 0.3f;
                shakeTimer = 0.35f;
                burst(witchX, witchY, 0xFFFF5577, 20, unit);
                addPopup(witchX, witchY - 80f * unit, "-½", 0xFFFF5577, unit);
                playTone(ToneGenerator.TONE_PROP_NACK, 150);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (health <= 0) {
                    endGame();
                }
            }
            if (obstacle.x < -60f * unit) {
                obstacles.remove(i);
            }
        }
    }

    private void updateFood(float delta, float speed, float unit) {
        RectF witchHit = witchHitBox(unit);
        for (int i = foodItems.size() - 1; i >= 0; i--) {
            Food food = foodItems.get(i);
            food.x -= speed * delta;
            food.phase += delta * 3f;
            RectF hit = new RectF(food.x - 21f * unit, food.y - 21f * unit,
                    food.x + 21f * unit, food.y + 21f * unit);
            if (RectF.intersects(witchHit, hit)) {
                // Восстанавливаем целое сердечко; заполнит и недостающую половину.
                if (health < MAX_HEALTH) {
                    health = Math.min(MAX_HEALTH, health + 1); // лечит ПОЛОВИНУ сердца
                    addPopup(food.x, food.y - 18f * unit, "+½", 0xFF8CFF6B, unit);
                } else {
                    score += 5 * scoreMul(); // здоровье полное — немного очков
                    addPopup(food.x, food.y - 18f * unit, "+5", 0xFF8CFF6B, unit);
                }
                burst(food.x, food.y, 0xFF8CFF6B, 18, unit);
                playTone(ToneGenerator.TONE_PROP_ACK, 90);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                foodItems.remove(i);
            } else if (food.x < -55f * unit) {
                foodItems.remove(i);
            }
        }
    }

    private void updateDrones(float delta, float speed, float unit) {
        RectF witchHit = witchHitBox(unit);
        for (int i = drones.size() - 1; i >= 0; i--) {
            Drone drone = drones.get(i);
            drone.x -= speed * delta; // ровно со скоростью мира — не нагоняет ящики
            drone.phase += delta;
            RectF hit = new RectF(drone.x - 22f * unit, drone.y - 14f * unit,
                    drone.x + 22f * unit, drone.y + 18f * unit);
            // Опасен только если подпрыгнуть в него; стоя на земле — пролетает над головой.
            if (!drone.hit && !isInvulnerable() && RectF.intersects(witchHit, hit)) {
                drone.hit = true;
                health -= 1;
                combo = 0;
                invulnerableTimer = 1.2f;
                flashTimer = 0.3f;
                shakeTimer = 0.35f;
                burst(witchX, witchY - 40f * unit, 0xFFFF5577, 20, unit);
                addPopup(witchX, witchY - 90f * unit, "-½", 0xFFFF5577, unit);
                playTone(ToneGenerator.TONE_PROP_NACK, 150);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (health <= 0) {
                    endGame();
                }
            }
            if (drone.x < -60f * unit) {
                drones.remove(i);
            }
        }
    }

    private void updateReturns(float delta, float unit) {
        RectF witchHit = witchHitBox(unit);
        for (int i = returns.size() - 1; i >= 0; i--) {
            ReturnBox box = returns.get(i);
            box.x += box.vx * delta; // летит влево, к игроку
            box.rotation += box.spin * delta;
            RectF hit = new RectF(box.x - 17f * unit, box.y - 17f * unit,
                    box.x + 17f * unit, box.y + 17f * unit);
            if (!isInvulnerable() && RectF.intersects(witchHit, hit)) {
                health -= 1;
                combo = 0;
                invulnerableTimer = 1.2f;
                flashTimer = 0.3f;
                shakeTimer = 0.35f;
                burst(box.x, box.y, 0xFFFF5577, 18, unit);
                addPopup(witchX, witchY - 90f * unit, "-½", 0xFFFF5577, unit);
                playTone(ToneGenerator.TONE_PROP_NACK, 150);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                returns.remove(i);
                if (health <= 0) {
                    endGame();
                }
            } else if (box.x < -40f * unit) {
                returns.remove(i);
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

            // Клиент в кадре и впереди игрока кидает возврат-коробку — её надо сбить или перепрыгнуть.
            if (!customer.frog && customer.x < getWidth() - 20f * unit
                    && customer.x > witchX + getWidth() * 0.18f) {
                customer.throwTimer -= delta;
                if (customer.throwTimer <= 0f && returns.size() < 4) {
                    throwReturn(customer, unit);
                    customer.throwTimer = 1.5f + random.nextFloat() * 1.1f;
                }
            }

            if (!customer.frog && !isInvulnerable()) {
                RectF hit = new RectF(customer.x - 25f * unit, customer.y - 38f * unit,
                        customer.x + 25f * unit, customer.y + 38f * unit);
                if (RectF.intersects(witchHit, hit)) {
                    health -= 2; // злой клиент — целое сердечко
                    combo = 0;
                    invulnerableTimer = 1.5f;
                    flashTimer = 0.35f;
                    shakeTimer = 0.4f;
                    customer.frog = true;
                    customer.frogTimer = 0.9f;
                    burst(witchX, witchY, 0xFFFF5577, 24, unit);
                    addPopup(witchX, witchY - 80f * unit, "-СЕРДЦЕ", 0xFFFF5577, unit);
                    playTone(ToneGenerator.TONE_PROP_NACK, 170);
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    if (health <= 0) {
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
                    int g = 25 * scoreMul();
                    score += g;
                    burst(customer.x, customer.y, 0xFFB7FF4A, 25, unit);
                    addPopup(customer.x, customer.y - 40f * unit, "+" + g, 0xFFB7FF4A, unit);
                    playTone(ToneGenerator.TONE_PROP_ACK, 85);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    used = true;
                    break;
                }
            }
            // Сбиваем брошенный клиентом возврат — за это очки.
            if (!used) {
                for (int r = returns.size() - 1; r >= 0; r--) {
                    ReturnBox box = returns.get(r);
                    float dx = spell.x - box.x;
                    float dy = spell.y - box.y;
                    float radius = 30f * unit;
                    if (dx * dx + dy * dy < radius * radius) {
                        int g = 15 * scoreMul();
                        score += g;
                        returnsDeflected++;
                        burst(box.x, box.y, 0xFFFFD84D, 18, unit);
                        // В бою с боссом сбитый возврат = сданный курьеру.
                        if (bossActive) {
                            bossDelivered++;
                            addPopup(box.x, box.y - 14f * unit, "СДАЛ!", 0xFF8CFF6B, unit);
                        } else {
                            addPopup(box.x, box.y - 14f * unit, "+" + g, 0xFFFFE56B, unit);
                        }
                        playTone(ToneGenerator.TONE_PROP_ACK, 70);
                        returns.remove(r);
                        used = true;
                        break;
                    }
                }
            }
            // Сбиваем дрон: он летит высоко, поэтому попасть можно только выстрелом В ПРЫЖКЕ,
            // когда снаряд поднимается до его уровня.
            if (!used) {
                for (int d = drones.size() - 1; d >= 0; d--) {
                    Drone drone = drones.get(d);
                    float dx = spell.x - drone.x;
                    float dy = spell.y - drone.y;
                    if (Math.abs(dx) < 30f * unit && Math.abs(dy) < 32f * unit) {
                        int g = 20 * scoreMul();
                        score += g;
                        dronesDowned++;
                        burst(drone.x, drone.y, 0xFFFFD84D, 22, unit);
                        addPopup(drone.x, drone.y - 14f * unit, "+" + g, 0xFFFFE56B, unit);
                        playTone(ToneGenerator.TONE_PROP_ACK, 80);
                        drones.remove(d);
                        used = true;
                        break;
                    }
                }
            }
            if (used || spell.x > getWidth() + 40f * unit) {
                spells.remove(i);
            }
        }
    }

    private void throwReturn(Customer customer, float unit) {
        float y = clamp(customer.y - 22f * unit, 90f * unit, getHeight() - 60f * unit);
        float vx = -(getWidth() * 0.34f + getWidth() * 0.10f * difficulty());
        float spin = (random.nextBoolean() ? 1f : -1f) * (180f + random.nextFloat() * 160f);
        returns.add(new ReturnBox(customer.x - 18f * unit, y, vx, spin, random.nextInt(9)));
        addPopup(customer.x, customer.y - 46f * unit, "ВОЗВРАТ!", 0xFFFF8A3D, unit);
        playTone(ToneGenerator.TONE_PROP_BEEP, 45);
    }

    // ----------------------------------------------------------------- БОСС
    private void startBoss(float unit) {
        bossActive = true;
        bossState = 0;
        bossStateT = 0f;
        bossPhase = 0f;
        bossDelivered = 0;
        bossDeliverGoal = 12 + bossesDefeated * 4;
        bossX = getWidth() + 110f * unit;
        bossY = getHeight() - 120f * unit;
        bossTimer = 1.2f;
        bossBannerTimer = 2.8f;
        // Чистим арену — остаются только ведьма, её выстрелы и возвраты курьера.
        obstacles.clear();
        lostItems.clear();
        drones.clear();
        returns.clear();
        customers.clear();
        powerups.clear();
        parcels.clear();
        foodItems.clear();
        playTone(ToneGenerator.TONE_PROP_BEEP2, 200);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    private void updateBoss(float delta, float unit) {
        bossPhase += delta;
        float targetX = getWidth() * 0.80f;
        bossY = getHeight() - 120f * unit + (float) Math.sin(bossPhase * 2f) * 6f * unit;
        if (bossState == 0) {
            bossX += (targetX - bossX) * (1f - (float) Math.exp(-delta * 4f));
            if (Math.abs(bossX - targetX) < 6f * unit) {
                bossX = targetX;
                bossState = 1;
                bossTimer = 1.0f;
            }
        } else if (bossState == 1) {
            bossTimer -= delta;
            if (bossTimer <= 0f) {
                throwBossReturn(unit);
                float t = bossDelivered / (float) bossDeliverGoal;
                bossTimer = lerp(1.25f, 0.7f, t) + random.nextFloat() * 0.4f;
            }
            if (bossDelivered >= bossDeliverGoal) {
                bossState = 2;
                bossStateT = 0f;
                addPopup(bossX, bossY - 70f * unit, "ПРИНЯТО!", 0xFF8CFF6B, unit);
                playTone(ToneGenerator.TONE_PROP_ACK, 180);
            }
        } else {
            // Курьер уезжает.
            bossStateT += delta;
            bossX += getWidth() * 0.5f * delta;
            if (bossStateT > 1.2f) {
                finishBoss(unit);
            }
        }
    }

    private void throwBossReturn(float unit) {
        float y = clamp(bossY - 10f * unit + (random.nextBoolean() ? -28f * unit : 18f * unit),
                90f * unit, getHeight() - 60f * unit);
        float vx = -(getWidth() * (0.30f + 0.04f * bossesDefeated));
        float spin = (random.nextBoolean() ? 1f : -1f) * (180f + random.nextFloat() * 160f);
        returns.add(new ReturnBox(bossX - 40f * unit, y, vx, spin, random.nextInt(9)));
        playTone(ToneGenerator.TONE_PROP_BEEP, 35);
    }

    private void finishBoss(float unit) {
        bossActive = false;
        bossesDefeated++;
        score += 300 * scoreMul();
        coins += 8;
        preferences.edit().putInt("coins", coins).apply();
        addPopup(witchX, witchY - 96f * unit, "+8 монет", 0xFFFFD84D, unit);
        burst(witchX, witchY - 30f * unit, 0xFFFFE56B, 26, unit);
        playTone(ToneGenerator.TONE_PROP_ACK, 220);
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

    // Множитель очков: каждые 4 пойманные подряд посылки добавляют +1 (до x5).
    private int comboMultiplier() {
        return Math.min(5, 1 + combo / 4);
    }

    // Неуязвимость: после удара (i-frames) или активный щит-бонус.
    private boolean isInvulnerable() {
        return invulnerableTimer > 0f || shieldTimer > 0f;
    }

    // Итоговый множитель очков: максимум из активированного усилителя и подобранного x2-бонуса.
    private int scoreMul() {
        int m = 1;
        if (doubleTimer > 0f) {
            m = 2;
        }
        if (boostTimer > 0f && boostMul > m) {
            m = boostMul;
        }
        return m;
    }

    private void saveShopState() {
        preferences.edit()
                .putInt("coins", coins)
                .putInt("own_shield", ownShield)
                .putInt("own_magnet", ownMagnet)
                .putInt("own_boost2", ownBoostX2)
                .putInt("own_boost3", ownBoostX3)
                .putInt("own_heal", ownHeal)
                .apply();
    }

    private void addPopup(float x, float y, String text, int color, float unit) {
        popups.add(new FloatingText(x, y, text, color, 17f * unit));
    }

    private void updatePopups(float delta) {
        for (int i = popups.size() - 1; i >= 0; i--) {
            FloatingText p = popups.get(i);
            p.life -= delta;
            p.y -= 46f * delta * (getHeight() / 430f); // плывёт вверх
            if (p.life <= 0f) {
                popups.remove(i);
            }
        }
    }

    private void drawPopups(Canvas canvas) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (FloatingText p : popups) {
            float a = Math.min(1f, p.life / 0.35f);
            float rise = (1f - p.life / p.maxLife);
            textPaint.setTextSize(p.size * (1f + 0.25f * rise));
            textPaint.setColor(withAlpha(0xFF000000, a * 0.5f));
            canvas.drawText(p.text, p.x + 1.5f, p.y + 1.5f, textPaint);
            textPaint.setColor(withAlpha(p.color, a));
            canvas.drawText(p.text, p.x, p.y, textPaint);
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
        // Потеряшка влетает сверху-справа и по дуге приземляется ВПЕРЕДИ игрока, а затем
        // катится навстречу по полу — её заранее видно и можно перепрыгнуть (в т.ч. двойным).
        float x = getWidth() + 50f * unit;
        if (!xClearOfObstacles(x)) {
            return; // Рядом уже входит ящик — не ставим два хазарда в одну точку.
        }
        float y = (95f + random.nextFloat() * 45f) * unit;
        float vy = 35f + random.nextFloat() * 25f;
        lostItems.add(new LostItem(x, y, vy,
                50f + random.nextFloat() * 80f,
                random.nextInt(3)));
    }

    private void spawnObstacle(float unit) {
        float x = getWidth() + 60f * unit;
        // Если у точки входа уже висит потеряшка — не плодим их в одном месте.
        if (!xClearOfLostItems(x)) {
            return;
        }
        // Клиент недавно вышел справа — не ставим ящик: после прыжка приземлишься прямо на него.
        float custThreshold = getWidth() * 0.42f;
        for (Customer c : customers) {
            if (!c.frog && Math.abs(c.x - x) < custThreshold) {
                return;
            }
        }
        obstacles.add(new Obstacle(x, random.nextInt(9)));
    }

    // Свободна ли вертикаль x от препятствий/потеряшек (чтобы не было двух хазардов в точке).
    private boolean xClearOfObstacles(float x) {
        float threshold = getWidth() * 0.17f;
        for (Obstacle obstacle : obstacles) {
            if (Math.abs(obstacle.x - x) < threshold) {
                return false;
            }
        }
        return true;
    }

    private boolean xClearOfLostItems(float x) {
        float threshold = getWidth() * 0.17f;
        for (LostItem item : lostItems) {
            if (Math.abs(item.x - x) < threshold) {
                return false;
            }
        }
        return true;
    }

    private void spawnDrone(float unit) {
        // Дрон летит над головой: стоя — безопасно, прыгнешь — собьёшься.
        float y = getHeight() - 158f * unit;
        drones.add(new Drone(getWidth() + 50f * unit, y, random.nextFloat() * 6f));
    }

    private void spawnFood(float unit) {
        float y = getHeight() - (92f + random.nextFloat() * 64f) * unit;
        int cells = (foodSheet.getWidth() / 16) * (foodSheet.getHeight() / 16);
        foodItems.add(new Food(getWidth() + 55f * unit, y,
                random.nextInt(cells), random.nextFloat() * 6f));
    }

    private void spawnPowerup(float unit) {
        float y = getHeight() - (96f + random.nextFloat() * 56f) * unit;
        powerups.add(new PowerUp(getWidth() + 55f * unit, y,
                random.nextInt(3), random.nextFloat() * 6f));
    }

    private void updatePowerups(float delta, float speed, float unit) {
        RectF witchHit = witchHitBox(unit);
        for (int i = powerups.size() - 1; i >= 0; i--) {
            PowerUp p = powerups.get(i);
            p.x -= speed * delta;
            p.phase += delta * 3f;
            RectF hit = new RectF(p.x - 22f * unit, p.y - 22f * unit,
                    p.x + 22f * unit, p.y + 22f * unit);
            if (RectF.intersects(witchHit, hit)) {
                String label;
                int color;
                if (p.type == 0) {
                    shieldTimer = 5.5f;
                    label = "ЩИТ!";
                    color = 0xFF61E8FF;
                } else if (p.type == 1) {
                    magnetTimer = 6.5f;
                    label = "МАГНИТ!";
                    color = 0xFFFF8AE0;
                } else {
                    doubleTimer = 7f;
                    label = "x2 ОЧКИ!";
                    color = 0xFFFFE56B;
                }
                burst(p.x, p.y, color, 24, unit);
                addPopup(witchX, witchY - 96f * unit, label, color, unit);
                playTone(ToneGenerator.TONE_PROP_ACK, 110);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                powerups.remove(i);
            } else if (p.x < -55f * unit) {
                powerups.remove(i);
            }
        }
    }

    private void spawnCustomer(float unit, float difficulty) {
        // Клиент идёт по полу навстречу (на уровне ведьмы).
        Customer customer = new Customer(getWidth() + 70f * unit,
                getHeight() - 76f * unit, random.nextFloat() * 4f);
        customer.variant = random.nextInt(4);
        customer.baseY = customer.y;
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
        lostToBoss = bossActive; // проиграл курьеру?
        bossActive = false;
        firing = false;
        steerPointerId = -1;
        firePointerId = -1;
        newHighScore = score > highScore;
        if (newHighScore) {
            highScore = score;
        }
        if (runMaxCombo > comboRecord) {
            comboRecord = runMaxCombo;
        }
        // Монеты: 1 за каждые 200 очков.
        coinsEarned = score / 200;
        coins += coinsEarned;
        preferences.edit()
                .putInt("high_score", highScore)
                .putInt("combo_record", comboRecord)
                .putInt("coins", coins)
                .apply();
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

        textPaint.setTextSize(14f * unit);
        textPaint.setColor(0xFFF5EFFF);
        canvas.drawText("Развози посылки и уворачивайся!", 48f * unit, 184f * unit, textPaint);

        drawLegend(canvas, unit);

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
        canvas.drawText("ВПЕРЁД!", playButton.centerX(),
                playButton.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f,
                textPaint);

        // Кнопка магазина рядом с «ВПЕРЁД!».
        float shopW = 150f * unit;
        shopButton.set(left, top + buttonHeight + 14f * unit,
                left + shopW, top + buttonHeight + 14f * unit + 46f * unit);
        paint.setColor(0xFF2BA6C7);
        canvas.drawRoundRect(shopButton, 20f * unit, 20f * unit, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * unit);
        paint.setColor(0xDDFFFFFF);
        canvas.drawRoundRect(shopButton, 20f * unit, 20f * unit, paint);
        paint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(18f * unit);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("МАГАЗИН", shopButton.centerX(),
                shopButton.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);

        // Баланс монет и рекорд — над кнопками, чтобы не наезжать на «МАГАЗИН».
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(16f * unit);
        drawCoin(canvas, 56f * unit, 210f * unit, 9f * unit);
        textPaint.setColor(0xFFFFD84D);
        canvas.drawText("" + coins, 70f * unit, 215f * unit, textPaint);

        if (highScore > 0) {
            textPaint.setTextSize(15f * unit);
            textPaint.setColor(0xFFFFE56B);
            canvas.drawText("РЕКОРД: " + highScore, 140f * unit, 215f * unit, textPaint);
        }
    }

    // Простая монетка-иконка.
    private void drawCoin(Canvas canvas, float x, float y, float r) {
        paint.setColor(0xFFB87A12);
        canvas.drawCircle(x, y, r, paint);
        paint.setColor(0xFFFFC83D);
        canvas.drawCircle(x, y, r * 0.82f, paint);
        paint.setColor(0xFFFFE99A);
        canvas.drawCircle(x - r * 0.2f, y - r * 0.2f, r * 0.35f, paint);
    }

    private static final String[] SHOP_NAMES = {"ЩИТ", "МАГНИТ", "УСИЛИТЕЛЬ x2", "УСИЛИТЕЛЬ x3", "ХИЛКА"};
    private static final String[] SHOP_DESCS = {
        "кнопкой в бою: неуязвимость ~6с", "кнопкой в бою: магнит посылок ~7с",
        "кнопкой в бою: очки x2 ~12с", "кнопкой в бою: очки x3 ~12с",
        "кнопкой в бою: +1,5 сердца"};
    private static final int[] SHOP_PRICES = {50, 25, 100, 200, 200};
    private static final int[] SHOP_ICON = {0, 1, 2, 2, -1}; // -1 = аптечка (рисуется кодом)

    private int ownedFor(int i) {
        switch (i) {
            case 0: return ownShield;
            case 1: return ownMagnet;
            case 2: return ownBoostX2;
            case 3: return ownBoostX3;
            default: return ownHeal;
        }
    }

    // Аптечка/хилка — зелёный значок с белым крестом.
    private void drawMedkit(Canvas canvas, float cx, float cy, float size) {
        float h = size * 0.5f;
        paint.setColor(0xFF2BD17E);
        canvas.drawRoundRect(cx - h, cy - h, cx + h, cy + h, size * 0.16f, size * 0.16f, paint);
        paint.setColor(Color.WHITE);
        float arm = size * 0.13f;
        float len = size * 0.32f;
        canvas.drawRect(cx - arm, cy - len, cx + arm, cy + len, paint);
        canvas.drawRect(cx - len, cy - arm, cx + len, cy + arm, paint);
    }

    private void drawShop(Canvas canvas) {
        drawCoverBitmap(canvas, splashArt);
        paint.setColor(0xDC140A28);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        float unit = getHeight() / 430f;

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFFFE56B);
        textPaint.setTextSize(32f * unit);
        textPaint.setShadowLayer(8f * unit, 0f, 3f * unit, 0xFF120323);
        canvas.drawText("МАГАЗИН", getWidth() / 2f, 52f * unit, textPaint);
        textPaint.clearShadowLayer();
        // Баланс монет.
        drawCoin(canvas, getWidth() / 2f - 34f * unit, 74f * unit, 10f * unit);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(0xFFFFD84D);
        textPaint.setTextSize(20f * unit);
        canvas.drawText("" + coins, getWidth() / 2f - 18f * unit, 81f * unit, textPaint);

        float panelLeft = getWidth() * 0.16f;
        float panelRight = getWidth() * 0.84f;
        float rowsTop = 96f * unit;
        float rowH = 50f * unit;
        for (int i = 0; i < SHOP_NAMES.length; i++) {
            float top = rowsTop + i * rowH;
            float cy = top + (rowH - 8f * unit) * 0.5f;
            paint.setColor(0x30FFFFFF);
            canvas.drawRoundRect(panelLeft, top, panelRight, top + rowH - 8f * unit,
                    12f * unit, 12f * unit, paint);

            float s = 32f * unit;
            float ix = panelLeft + 32f * unit;
            if (SHOP_ICON[i] < 0) {
                drawMedkit(canvas, ix, cy, s);
            } else {
                Bitmap icon = bonusIcons[SHOP_ICON[i]];
                paint.setColor(Color.WHITE);
                paint.setFilterBitmap(false);
                canvas.drawBitmap(icon, new Rect(0, 0, icon.getWidth(), icon.getHeight()),
                        new RectF(ix - s / 2f, cy - s / 2f, ix + s / 2f, cy + s / 2f), paint);
                paint.setFilterBitmap(true);
            }

            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(16f * unit);
            canvas.drawText(SHOP_NAMES[i] + "   x" + ownedFor(i), panelLeft + 60f * unit,
                    cy - 2f * unit, textPaint);
            textPaint.setColor(0xFFB9A9E0);
            textPaint.setTextSize(11.5f * unit);
            canvas.drawText(SHOP_DESCS[i], panelLeft + 60f * unit, cy + 15f * unit, textPaint);

            RectF buy = buyButtons[i];
            float bw = 118f * unit;
            buy.set(panelRight - bw - 12f * unit, cy - 18f * unit, panelRight - 12f * unit, cy + 18f * unit);
            boolean afford = coins >= SHOP_PRICES[i];
            paint.setColor(afford ? 0xFF2BD17E : 0xFF4A4A5A);
            canvas.drawRoundRect(buy, 14f * unit, 14f * unit, paint);
            drawCoin(canvas, buy.centerX() - 22f * unit, buy.centerY(), 8f * unit);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(16f * unit);
            canvas.drawText("" + SHOP_PRICES[i], buy.centerX() - 8f * unit,
                    buy.centerY() + 5f * unit, textPaint);
        }

        float bw2 = 150f * unit;
        float bh2 = 44f * unit;
        backButton.set(getWidth() / 2f - bw2 / 2f, getHeight() - bh2 - 12f * unit,
                getWidth() / 2f + bw2 / 2f, getHeight() - 12f * unit);
        paint.setColor(0xFFF15BB5);
        canvas.drawRoundRect(backButton, 20f * unit, 20f * unit, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(18f * unit);
        canvas.drawText("НАЗАД", backButton.centerX(),
                backButton.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
    }

    private void buyItem(int i) {
        if (coins < SHOP_PRICES[i]) {
            playTone(ToneGenerator.TONE_PROP_NACK, 90);
            return;
        }
        coins -= SHOP_PRICES[i];
        switch (i) {
            case 0: ownShield++; break;
            case 1: ownMagnet++; break;
            case 2: ownBoostX2++; break;
            case 3: ownBoostX3++; break;
            default: ownHeal++; break;
        }
        saveShopState();
        playTone(ToneGenerator.TONE_PROP_ACK, 90);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // Легенда на стартовом экране: спрайты + описания, чтобы игрок понимал, что есть что.
    private void drawLegend(Canvas canvas, float unit) {
        float panelLeft = getWidth() * 0.40f;
        float panelRight = getWidth() - 24f * unit;
        float panelTop = 58f * unit;
        float panelBottom = getHeight() - 22f * unit;

        paint.setColor(0xCC120A28);
        canvas.drawRoundRect(panelLeft, panelTop, panelRight, panelBottom,
                18f * unit, 18f * unit, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * unit);
        paint.setColor(0x55FFFFFF);
        canvas.drawRoundRect(panelLeft, panelTop, panelRight, panelBottom,
                18f * unit, 18f * unit, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(17f * unit);
        textPaint.setColor(0xFF61E8FF);
        canvas.drawText("ЧТО ЕСТЬ ЧТО", panelLeft + 18f * unit, panelTop + 27f * unit, textPaint);

        String[][] rows = {
            {"witch", "ВЕДЬМА — ЭТО ТЫ", "веди пальцем • тап — прыжок"},
            {"parcel", "ПОСЫЛКА   +10", "лови — это очки"},
            {"food", "ЕДА   +½ сердца", "лечит немного и редко"},
            {"crate", "ЯЩИК   −полсердца", "перепрыгивай препятствие"},
            {"lost", "ПОТЕРЯШКА   −20", "перепрыгни, не задень"},
            {"drone", "ДРОН СВЕРХУ", "сбей выстрелом в прыжке"},
            {"customer", "КЛИЕНТ", "стреляй • сбивай возвраты"},
        };

        float rowTop = panelTop + 40f * unit;
        float rowH = (panelBottom - rowTop) / rows.length;
        float iconCx = panelLeft + 36f * unit;
        float textX = panelLeft + 68f * unit;
        for (int i = 0; i < rows.length; i++) {
            float cy = rowTop + rowH * (i + 0.5f);
            drawLegendIcon(canvas, rows[i][0], iconCx, cy, 30f * unit, unit);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setTextSize(14.5f * unit);
            textPaint.setColor(Color.WHITE);
            canvas.drawText(rows[i][1], textX, cy - 2f * unit, textPaint);
            textPaint.setTextSize(11.5f * unit);
            textPaint.setColor(0xFFB9A9E0);
            canvas.drawText(rows[i][2], textX, cy + 14f * unit, textPaint);
        }
    }

    private void drawLegendIcon(Canvas canvas, String kind, float cx, float cy, float s, float unit) {
        switch (kind) {
            case "witch": {
                int fw = witchIdleSheet.getWidth() / 5;
                Rect src = new Rect(0, 0, fw, witchIdleSheet.getHeight());
                float h = s * 1.35f;
                float w = h * fw / witchIdleSheet.getHeight();
                paint.setColor(Color.WHITE);
                canvas.drawBitmap(witchIdleSheet, src,
                        new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), paint);
                break;
            }
            case "food": {
                int cell = foodSheet.getWidth() / 8;
                float d = s;
                paint.setColor(Color.WHITE);
                paint.setFilterBitmap(false);
                canvas.drawBitmap(foodSheet, new Rect(0, 0, cell, cell),
                        new RectF(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f), paint);
                paint.setFilterBitmap(true);
                break;
            }
            case "crate": {
                int cw = crateSheet.getWidth() / 3;
                int ch = crateSheet.getHeight() / 3;
                float w = s * 1.2f;
                float h = w * ch / cw;
                paint.setColor(Color.WHITE);
                paint.setFilterBitmap(false);
                canvas.drawBitmap(crateSheet, new Rect(0, 0, cw, ch),
                        new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), paint);
                paint.setFilterBitmap(true);
                break;
            }
            case "parcel": {
                float r = s * 0.42f;
                paint.setShader(new LinearGradient(cx, cy - r, cx, cy + r,
                        0xFF2D9BFF, 0xFF0F6FD6, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(cx - r, cy - r, cx + r, cy + r, 4f * unit, 4f * unit, paint);
                paint.setShader(null);
                paint.setColor(Color.WHITE);
                canvas.drawRect(cx - 2f * unit, cy - r, cx + 2f * unit, cy + r, paint);
                canvas.drawRect(cx - r, cy - 2f * unit, cx + r, cy + 2f * unit, paint);
                canvas.drawCircle(cx, cy - r, 3f * unit, paint);
                break;
            }
            case "lost": {
                canvas.save();
                canvas.rotate(12f, cx, cy);
                float r = s * 0.42f;
                paint.setColor(0xFFF15BB5);
                canvas.drawRoundRect(cx - r, cy - r * 0.9f, cx + r, cy + r * 0.9f,
                        4f * unit, 4f * unit, paint);
                paint.setColor(0xEEFFFFFF);
                canvas.drawRect(cx - 2f * unit, cy - r * 0.9f, cx + 2f * unit, cy + r * 0.9f, paint);
                canvas.restore();
                paint.setColor(0xFFEF3340);
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setTextSize(13f * unit);
                canvas.drawText("?", cx, cy + 4f * unit, textPaint);
                break;
            }
            case "customer": {
                int cell = customerSheet.getHeight();
                float d = s * 1.25f;
                paint.setColor(Color.WHITE);
                paint.setFilterBitmap(false);
                canvas.drawBitmap(customerSheet, new Rect(0, 0, cell, cell),
                        new RectF(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f), paint);
                paint.setFilterBitmap(true);
                break;
            }
            case "drone": {
                paint.setColor(0xFF2B3A52);
                paint.setStrokeWidth(2.5f * unit);
                paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(cx - s * 0.55f, cy - s * 0.2f, cx + s * 0.55f, cy - s * 0.2f, paint);
                paint.setColor(0x88BFD4FF);
                canvas.drawOval(cx - s * 0.75f, cy - s * 0.35f, cx - s * 0.35f, cy - s * 0.1f, paint);
                canvas.drawOval(cx + s * 0.35f, cy - s * 0.35f, cx + s * 0.75f, cy - s * 0.1f, paint);
                paint.setColor(0xFF1C2740);
                canvas.drawRoundRect(cx - s * 0.4f, cy - s * 0.2f, cx + s * 0.4f, cy + s * 0.2f,
                        3f * unit, 3f * unit, paint);
                paint.setColor(0xFFFF4D4D);
                canvas.drawCircle(cx, cy + s * 0.1f, 2f * unit, paint);
                paint.setColor(0xFF8A572F);
                canvas.drawRect(cx - s * 0.2f, cy + s * 0.2f, cx + s * 0.2f, cy + s * 0.55f, paint);
                break;
            }
            default:
                break;
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
        // Тряска экрана при уроне — двигаем весь мир, но не HUD.
        int shakeSave = canvas.save();
        if (shakeTimer > 0f) {
            float mag = 8f * unit * Math.min(1f, shakeTimer / 0.4f);
            canvas.translate((random.nextFloat() - 0.5f) * mag,
                    (random.nextFloat() - 0.5f) * mag);
        }
        drawZoneScene(canvas, unit);
        if (bossActive) {
            drawBoss(canvas, unit);
        }
        for (Parcel parcel : parcels) {
            drawParcel(canvas, parcel, unit);
        }
        for (LostItem item : lostItems) {
            drawLostItem(canvas, item, unit);
        }
        for (Obstacle obstacle : obstacles) {
            drawObstacle(canvas, obstacle, unit);
        }
        for (Food food : foodItems) {
            drawFood(canvas, food, unit);
        }
        for (PowerUp p : powerups) {
            drawPowerup(canvas, p, unit);
        }
        for (Customer customer : customers) {
            if (customer.frog) {
                drawFrog(canvas, customer, unit);
            } else {
                drawCustomer(canvas, customer, unit);
            }
        }
        for (ReturnBox box : returns) {
            drawReturn(canvas, box, unit);
        }
        for (Spell spell : spells) {
            drawSpell(canvas, spell, unit);
        }
        drawWitch(canvas, unit);
        if (shieldTimer > 0f) {
            drawShieldBubble(canvas, unit);
        }
        for (Drone drone : drones) {
            drawDrone(canvas, drone, unit);
        }
        drawSparks(canvas);
        canvas.restoreToCount(shakeSave); // дальше — HUD без тряски
        drawPopups(canvas);
        drawHud(canvas, unit);
        if (state == STATE_PLAYING) {
            drawHudButtons(canvas, unit);
            drawInventory(canvas, unit);
        }
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
        if (zoneBannerTimer > 0f) {
            float a = Math.min(1f, zoneBannerTimer / 0.6f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(withAlpha(0xFFFFFFFF, a * 0.85f));
            textPaint.setTextSize(15f * unit);
            canvas.drawText("ЛОКАЦИЯ", getWidth() / 2f, getHeight() * 0.30f, textPaint);
            textPaint.setColor(withAlpha(0xFFFFE56B, a));
            textPaint.setTextSize(32f * unit);
            textPaint.setShadowLayer(8f * unit, 0f, 3f * unit, withAlpha(0xFF120323, a));
            canvas.drawText(currentZone % 2 == 1 ? "УЛИЦА" : "ПВЗ", getWidth() / 2f,
                    getHeight() * 0.30f + 34f * unit, textPaint);
            textPaint.clearShadowLayer();
        }
        if (bossActive) {
            drawBossBar(canvas, unit);
        }
        if (bossBannerTimer > 0f) {
            float a = Math.min(1f, bossBannerTimer / 0.6f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(withAlpha(0xFFFF6B6B, a));
            textPaint.setTextSize(30f * unit);
            textPaint.setShadowLayer(8f * unit, 0f, 3f * unit, withAlpha(0xFF120323, a));
            canvas.drawText("КУРЬЕР ОЗОНА!", getWidth() / 2f, getHeight() * 0.46f, textPaint);
            textPaint.setTextSize(16f * unit);
            textPaint.setColor(withAlpha(0xFFFFFFFF, a));
            canvas.drawText("сдай ему возвраты — отбивай их выстрелом!",
                    getWidth() / 2f, getHeight() * 0.46f + 26f * unit, textPaint);
            textPaint.clearShadowLayer();
        }
    }

    private void drawBoss(Canvas canvas, float unit) {
        float x = bossX;
        float y = bossY;
        float groundY = getHeight() - 46f * unit;

        // ГАЗель доставки Ozon за спиной курьера (кабина слева, кузов справа).
        float vBottom = groundY - 14f * unit;
        // Колёса.
        paint.setColor(0xFF15151A);
        canvas.drawCircle(x + 36f * unit, vBottom, 15f * unit, paint);
        canvas.drawCircle(x + 124f * unit, vBottom, 15f * unit, paint);
        paint.setColor(0xFF6A6A74);
        canvas.drawCircle(x + 36f * unit, vBottom, 6f * unit, paint);
        canvas.drawCircle(x + 124f * unit, vBottom, 6f * unit, paint);
        // Кузов (белый фургон).
        paint.setColor(0xFFF1F1F4);
        canvas.drawRoundRect(x + 44f * unit, y - 62f * unit, x + 154f * unit, vBottom,
                6f * unit, 6f * unit, paint);
        // Кабина.
        paint.setColor(0xFFE6E8EC);
        canvas.drawRoundRect(x + 6f * unit, y - 24f * unit, x + 48f * unit, vBottom,
                8f * unit, 8f * unit, paint);
        // Лобовое и боковое стекло.
        paint.setColor(0xFF2A3A4A);
        Path glass = new Path();
        glass.moveTo(x + 10f * unit, y - 18f * unit);
        glass.lineTo(x + 26f * unit, y - 18f * unit);
        glass.lineTo(x + 26f * unit, y + 2f * unit);
        glass.lineTo(x + 8f * unit, y + 2f * unit);
        glass.close();
        canvas.drawPath(glass, paint);
        canvas.drawRect(x + 30f * unit, y - 18f * unit, x + 44f * unit, y + 2f * unit, paint);
        // Фара.
        paint.setColor(0xFFFFE26A);
        canvas.drawRect(x + 6f * unit, y + 6f * unit, x + 12f * unit, y + 14f * unit, paint);
        // Фирменная синяя полоса Ozon + надпись.
        paint.setColor(0xFF005BFF);
        canvas.drawRect(x + 44f * unit, y - 28f * unit, x + 154f * unit, y - 12f * unit, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(13f * unit);
        canvas.drawText("OZON", x + 99f * unit, y - 15f * unit, textPaint);
        // Задние створки + ручка (правый край кузова).
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * unit);
        paint.setColor(0xFFB9B9C2);
        canvas.drawLine(x + 150f * unit, y - 58f * unit, x + 150f * unit, vBottom - 4f * unit, paint);
        paint.setStyle(Paint.Style.FILL);
        // Тело — синяя форма Ozon.
        paint.setColor(0xFF0B63CE);
        canvas.drawRoundRect(x - 36f * unit, y - 28f * unit, x + 24f * unit, y + 58f * unit,
                16f * unit, 16f * unit, paint);
        paint.setColor(0xFFFFE56B); // светоотражающая полоса
        canvas.drawRect(x - 36f * unit, y + 8f * unit, x + 24f * unit, y + 16f * unit, paint);
        // Ноги.
        paint.setColor(0xFF14233A);
        canvas.drawRect(x - 26f * unit, y + 56f * unit, x - 8f * unit, groundY, paint);
        canvas.drawRect(x + 4f * unit, y + 56f * unit, x + 22f * unit, groundY, paint);
        // Голова + кепка.
        paint.setColor(0xFFFFC69D);
        canvas.drawCircle(x - 10f * unit, y - 46f * unit, 22f * unit, paint);
        paint.setColor(0xFF084B9E);
        canvas.drawArc(x - 32f * unit, y - 72f * unit, x + 14f * unit, y - 26f * unit, 180f, 180f, true, paint);
        canvas.drawRect(x - 40f * unit, y - 50f * unit, x - 8f * unit, y - 44f * unit, paint);
        // Сердитые глаза.
        paint.setColor(0xFF2C174A);
        canvas.drawCircle(x - 18f * unit, y - 44f * unit, 2.6f * unit, paint);
        canvas.drawCircle(x - 4f * unit, y - 44f * unit, 2.6f * unit, paint);
        paint.setStrokeWidth(2.5f * unit);
        paint.setColor(0xFF2C174A);
        canvas.drawLine(x - 24f * unit, y - 50f * unit, x - 13f * unit, y - 47f * unit, paint);
        canvas.drawLine(x + 2f * unit, y - 50f * unit, x - 9f * unit, y - 47f * unit, paint);
        // Рука замахивается (швыряет возврат).
        float sw = (float) Math.sin(bossPhase * 6f) * 12f;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(9f * unit);
        paint.setColor(0xFF0B63CE);
        canvas.drawLine(x - 32f * unit, y - 6f * unit, x - 50f * unit, y - (18f + sw) * unit, paint);
        paint.setColor(0xFFFFC69D);
        canvas.drawCircle(x - 50f * unit, y - (18f + sw) * unit, 7f * unit, paint);
    }

    private void drawBossBar(Canvas canvas, float unit) {
        float w = getWidth() * 0.46f;
        float h = 16f * unit;
        float left = getWidth() / 2f - w / 2f;
        float top = 30f * unit;
        paint.setColor(0xD00A1222);
        canvas.drawRoundRect(left - 10f * unit, top - 22f * unit, left + w + 10f * unit,
                top + h + 8f * unit, 12f * unit, 12f * unit, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFFF8A3D);
        textPaint.setTextSize(13f * unit);
        canvas.drawText("СДАЙ ВОЗВРАТЫ КУРЬЕРУ", getWidth() / 2f, top - 8f * unit, textPaint);
        paint.setColor(0x55FFFFFF);
        canvas.drawRoundRect(left, top, left + w, top + h, h / 2f, h / 2f, paint);
        float frac = bossDeliverGoal > 0 ? bossDelivered / (float) bossDeliverGoal : 0f;
        paint.setColor(0xFF2BD17E);
        canvas.drawRoundRect(left, top, left + w * Math.min(1f, frac), top + h, h / 2f, h / 2f, paint);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(11f * unit);
        canvas.drawText(bossDelivered + " / " + bossDeliverGoal, getWidth() / 2f, top + h - 3f * unit, textPaint);
    }

    // Палитры локаций (зон): небо(3), стена, акцент-полоса, ambient-наложение (с альфой).
    private static final int[][] ZONE_SKY = {
        {0xFF081426, 0xFF101F36, 0xFF18283E}, // СКЛАД
        {0xFF153A5E, 0xFF1E4E72, 0xFF2A5E80}, // УЛИЦА
        {0xFF3A1E50, 0xFF7A3A5A, 0xFFD06A38}, // ЗАКАТ
        {0xFF05091A, 0xFF0A1228, 0xFF121C36}, // НОЧЬ
        {0xFF2A2452, 0xFF6A4A82, 0xFFE89AB8}, // РАССВЕТ
    };
    private static final int[] ZONE_WALL = {0xFF111D30, 0xFF1A3048, 0xFF3A2438, 0xFF0A1426, 0xFF2E2A50};
    private static final int[] ZONE_ACCENT = {0xFF0B63CE, 0xFF1E8BF0, 0xFFFF7A3A, 0xFF3A5AC8, 0xFF9B5DE5};
    private static final int[] ZONE_AMBIENT = {0x00000000, 0x4866C0F0, 0x58FF7A24, 0x6E04102C, 0x4CE886C0};
    private static final String[] ZONE_NAMES = {"СКЛАД", "УЛИЦА", "ЗАКАТ", "НОЧЬ", "РАССВЕТ"};

    private int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24) + (int) (((b >>> 24) - (a >>> 24)) * t);
        int ar = ((a >> 16) & 0xFF) + (int) ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int ag = ((a >> 8) & 0xFF) + (int) ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int ab = (a & 0xFF) + (int) (((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    // Две локации: ПВЗ (чётные зоны) и УЛИЦА (нечётные). Бой с курьером — всегда на улице.
    private boolean isStreetZone() {
        return bossActive || (currentZone % 2 == 1);
    }

    private void drawZoneScene(Canvas canvas, float unit) {
        if (isStreetZone()) {
            drawOutdoor(canvas, unit, 1); // улица (день)
        } else {
            drawWarehouseBackground(canvas, unit); // ПВЗ (пункт выдачи)
        }
    }

    private void drawWarehouseBackground(Canvas canvas, float unit) {
        int wallCol = 0xFF111D30;
        int accentCol = 0xFF0B63CE;
        paint.setShader(new LinearGradient(0f, 0f, 0f, getHeight(),
                new int[]{0xFF081426, 0xFF101F36, 0xFF18283E},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);

        float warehouseTop = 58f * unit;
        float groundY = getHeight() - 48f * unit;
        paint.setColor(wallCol);
        canvas.drawRect(0f, warehouseTop, getWidth(), groundY, paint);
        paint.setColor(accentCol);
        canvas.drawRect(0f, warehouseTop, getWidth(), warehouseTop + 35f * unit, paint);

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

        drawEntrance(canvas, unit, warehouseTop, groundY);

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

    // Уличная/природная сцена для зон 1-4: небо, светило, здания, деревья, земля.
    private void drawOutdoor(Canvas canvas, float unit, int kind) {
        // kind: 1 улица(день), 2 закат, 3 ночь, 4 рассвет.
        int idx = kind - 1;
        int[][] sky = {
            {0xFF6EB7E8, 0xFFA7D8F0, 0xFFDCEFFb}, // день
            {0xFF3A2466, 0xFFB0506E, 0xFFFF9A4A}, // закат
            {0xFF070B22, 0xFF111634, 0xFF1E2348}, // ночь
            {0xFF2B2A5C, 0xFF8A5E92, 0xFFF2B6C6}, // рассвет
        };
        int[] groundCol = {0xFF6Fae54, 0xFF4A3A2E, 0xFF14203A, 0xFF3E5A46};
        int[] buildingCol = {0xFF8FA0B8, 0xFF2C1F3C, 0xFF161B36, 0xFF5A4A6E};
        int[] treeFoliage = {0xFF3E9E4B, 0xFF241B38, 0xFF12203A, 0xFF3A6A4A};
        int[] trunkCol = {0xFF6B4A2A, 0xFF1E1630, 0xFF0E1A2E, 0xFF4A3A50};

        float groundY = getHeight() - 48f * unit;

        // Небо.
        paint.setShader(new LinearGradient(0f, 0f, 0f, groundY,
                new int[]{sky[idx][0], sky[idx][1], sky[idx][2]},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, getWidth(), groundY, paint);
        paint.setShader(null);

        // Звёзды (ночь и чуть на рассвете).
        if (kind == 3 || kind == 4) {
            float starA = kind == 3 ? 1f : 0.4f;
            for (int i = 0; i < starsX.length; i++) {
                float tw = 0.5f + 0.5f * (float) Math.abs(Math.sin(worldTime * 1.6f + i));
                paint.setColor(withAlpha(0xFFFFFFFF, tw * starA * (1f - starsY[i] / 0.6f)));
                canvas.drawCircle(starsX[i] * getWidth(), starsY[i] * (groundY * 0.7f),
                        starsSize[i] * unit, paint);
            }
        }

        // Солнце или луна.
        float cx = getWidth() * 0.74f;
        float cy = (kind == 2) ? groundY * 0.62f : getHeight() * 0.2f;
        float cr = (kind == 2) ? 40f * unit : 28f * unit;
        int sunCol = kind == 1 ? 0xFFFFE680 : kind == 2 ? 0xFFFFC247
                : kind == 3 ? 0xFFFFF3C9 : 0xFFFFD9B0;
        paint.setShader(new RadialGradient(cx, cy, cr * 3f,
                withAlpha(sunCol, 0.35f), withAlpha(sunCol, 0f), Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, cr * 3f, paint);
        paint.setShader(null);
        paint.setColor(sunCol);
        canvas.drawCircle(cx, cy, cr, paint);
        if (kind == 3) { // кратеры луны
            paint.setColor(0x33000000);
            canvas.drawCircle(cx - cr * 0.3f, cy - cr * 0.15f, cr * 0.2f, paint);
            canvas.drawCircle(cx + cr * 0.3f, cy + cr * 0.25f, cr * 0.15f, paint);
        }

        // Облака (используем тот же список, светлые днём / тёмные ночью).
        int cloudCol = (kind == 3) ? 0x33203050 : (kind == 2) ? 0x66FFC9A0 : 0x88FFFFFF;
        for (Cloud cloud : clouds) {
            float x = cloud.x * getWidth();
            float y = cloud.y * (groundY * 0.55f);
            float size = 28f * unit * cloud.scale;
            paint.setColor(cloudCol);
            canvas.drawCircle(x, y, size * 0.55f, paint);
            canvas.drawCircle(x + size * 0.55f, y - size * 0.15f, size * 0.72f, paint);
            canvas.drawCircle(x + size * 1.18f, y, size * 0.52f, paint);
        }

        // Дальние холмы (медленный параллакс).
        paint.setColor(darken(groundCol[idx]));
        Path hills = new Path();
        hills.moveTo(0f, groundY);
        float hillW = 240f * unit;
        float hillOff = -(distance * 0.05f) % hillW;
        for (float x = hillOff - hillW; x < getWidth() + hillW; x += hillW) {
            hills.quadTo(x + hillW * 0.5f, groundY - 60f * unit, x + hillW, groundY);
        }
        hills.lineTo(getWidth(), getHeight());
        hills.lineTo(0f, getHeight());
        hills.close();
        canvas.drawPath(hills, paint);

        // Здания (средний параллакс).
        float bW = 90f * unit;
        float bOff = -(distance * 0.13f) % bW;
        for (float x = bOff - bW; x < getWidth() + bW; x += bW) {
            int seed = (int) Math.abs((x + distance * 0.13f) / bW);
            float h = (70f + (seed % 4) * 22f) * unit;
            paint.setColor(buildingCol[idx]);
            canvas.drawRect(x, groundY - h, x + bW - 10f * unit, groundY, paint);
            // окна (горят ночью/в закат)
            boolean lights = kind == 3 || kind == 2;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    boolean lit = lights && ((seed * 7 + row * 3 + col) % 3 != 0);
                    paint.setColor(lit ? 0xFFFFD76A : withAlpha(0xFF000000, 0.18f));
                    canvas.drawRect(x + (10f + col * 22f) * unit, groundY - h + (12f + row * 22f) * unit,
                            x + (20f + col * 22f) * unit, groundY - h + (20f + row * 22f) * unit, paint);
                }
            }
        }

        // Деревья (быстрый параллакс, ближе к игроку).
        float tW = 150f * unit;
        float tOff = -(distance * 0.28f) % tW;
        for (float x = tOff - tW; x < getWidth() + tW; x += tW) {
            float tx = x + 40f * unit;
            float th = 70f * unit;
            paint.setColor(trunkCol[idx]);
            canvas.drawRect(tx - 6f * unit, groundY - th, tx + 6f * unit, groundY, paint);
            paint.setColor(treeFoliage[idx]);
            canvas.drawCircle(tx, groundY - th - 6f * unit, 28f * unit, paint);
            canvas.drawCircle(tx - 22f * unit, groundY - th + 8f * unit, 20f * unit, paint);
            canvas.drawCircle(tx + 22f * unit, groundY - th + 8f * unit, 20f * unit, paint);
        }

        // Земля.
        paint.setColor(groundCol[idx]);
        canvas.drawRect(0f, groundY, getWidth(), getHeight(), paint);
        paint.setColor(darken(groundCol[idx]));
        canvas.drawRect(0f, groundY, getWidth(), groundY + 3f * unit, paint);
        // Бегущая разметка/трава-штрихи — ощущение скорости.
        float dashW = 40f * unit;
        float dashOff = -(distance * 0.5f) % (dashW * 2f);
        paint.setColor(kind == 1 ? 0x55FFFFFF : 0x33FFFFFF);
        float roadY = getHeight() - 20f * unit;
        for (float x = dashOff - dashW * 2f; x < getWidth() + dashW; x += dashW * 2f) {
            canvas.drawRoundRect(x, roadY, x + dashW, roadY + 4f * unit, 2f * unit, 2f * unit, paint);
        }
    }

    // Вход в пункт выдачи — рисуется только в самом начале и уезжает влево вместе с миром.
    private void drawEntrance(Canvas canvas, float unit, float warehouseTop, float groundY) {
        float doorWidth = 150f * unit;
        float doorX = getWidth() * 0.18f - distance; // движется 1:1 с миром → виден только на старте
        if (doorX + doorWidth + 12f * unit < 0f || doorX > getWidth()) {
            return;
        }
        float doorTop = warehouseTop + 44f * unit;

        // Рама входа.
        paint.setColor(0xFF1B2A40);
        canvas.drawRect(doorX - 9f * unit, doorTop - 6f * unit,
                doorX + doorWidth + 9f * unit, groundY, paint);
        // Тёмный проём с подсветкой «улицы» снаружи.
        paint.setShader(new LinearGradient(doorX, doorTop, doorX, groundY,
                0xFF9AD0FF, 0xFF14233A, Shader.TileMode.CLAMP));
        canvas.drawRect(doorX, doorTop, doorX + doorWidth, groundY, paint);
        paint.setShader(null);
        // Створки-стойки по краям проёма.
        paint.setColor(0xFF24344E);
        canvas.drawRect(doorX, doorTop, doorX + 8f * unit, groundY, paint);
        canvas.drawRect(doorX + doorWidth - 8f * unit, doorTop, doorX + doorWidth, groundY, paint);
        canvas.drawRect(doorX + doorWidth * 0.5f - 3f * unit, doorTop,
                doorX + doorWidth * 0.5f + 3f * unit, groundY, paint);

        // Вывеска «ПУНКТ ВЫДАЧИ» над входом (фирменный синий).
        float signTop = warehouseTop + 4f * unit;
        float signBottom = doorTop - 4f * unit;
        paint.setColor(0xFF005BFF);
        canvas.drawRoundRect(doorX - 12f * unit, signTop,
                doorX + doorWidth + 12f * unit, signBottom, 6f * unit, 6f * unit, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(15f * unit);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("ПУНКТ ВЫДАЧИ", doorX + doorWidth * 0.5f,
                (signTop + signBottom) * 0.5f + 5f * unit, textPaint);
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
        // Тень на полу — видно заранее, куда катится потеряшка и когда прыгать.
        float groundLine = getHeight() - 48f * unit;
        float height = Math.max(0f, groundLine - item.y);
        float shrink = clamp(1f - height / (180f * unit), 0.25f, 1f);
        paint.setColor(0x44101820);
        canvas.drawOval(item.x - 17f * unit * shrink, groundLine - 3f * unit,
                item.x + 17f * unit * shrink, groundLine + 6f * unit, paint);

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

    // Геометрия ящика-препятствия — общая для отрисовки и столкновений.
    private RectF crateRect(Obstacle obstacle, float unit) {
        float ground = getHeight() - 47f * unit;
        float width = 70f * unit;
        int cellW = crateSheet.getWidth() / 3;
        int cellH = crateSheet.getHeight() / 3;
        float height = width * cellH / cellW;
        return new RectF(obstacle.x - width / 2f, ground - height,
                obstacle.x + width / 2f, ground);
    }

    private void drawObstacle(Canvas canvas, Obstacle obstacle, float unit) {
        // Спрайт-лист 3x3: выбираем ячейку по type (0..8).
        int cellW = crateSheet.getWidth() / 3;
        int cellH = crateSheet.getHeight() / 3;
        int col = obstacle.type % 3;
        int row = obstacle.type / 3;
        Rect source = new Rect(col * cellW, row * cellH,
                (col + 1) * cellW, (row + 1) * cellH);
        // Мягкая тень под ящиком.
        float ground = getHeight() - 47f * unit;
        paint.setColor(0x44101820);
        canvas.drawOval(obstacle.x - 36f * unit, ground - 6f * unit,
                obstacle.x + 36f * unit, ground + 5f * unit, paint);
        paint.setColor(Color.WHITE);
        paint.setFilterBitmap(false); // Чёткие пиксели для пиксель-арта.
        canvas.drawBitmap(crateSheet, source, crateRect(obstacle, unit), paint);
        paint.setFilterBitmap(true);
    }

    private void drawFood(Canvas canvas, Food food, float unit) {
        float bob = (float) Math.sin(food.phase) * 4f * unit;
        float x = food.x;
        float y = food.y + bob;

        // Тёплое свечение — еду видно издалека.
        float glow = 1f + 0.14f * (float) Math.sin(food.phase * 3f);
        paint.setShader(new RadialGradient(x, y, 30f * unit * glow,
                0x4488FF7A, 0x0088FF7A, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, 30f * unit * glow, paint);
        paint.setShader(null);

        // Спрайт-лист еды 8x8 ячеек по 16px.
        int cell = foodSheet.getWidth() / 8;
        int cols = foodSheet.getWidth() / cell;
        int col = food.type % cols;
        int row = food.type / cols;
        Rect source = new Rect(col * cell, row * cell,
                (col + 1) * cell, (row + 1) * cell);
        float size = 42f * unit;
        RectF destination = new RectF(x - size / 2f, y - size / 2f,
                x + size / 2f, y + size / 2f);
        paint.setColor(Color.WHITE);
        paint.setFilterBitmap(false);
        canvas.drawBitmap(foodSheet, source, destination, paint);
        paint.setFilterBitmap(true);

        // Маленькое сердечко-подсказка: эта еда лечит.
        drawHeart(canvas, x + 18f * unit, y - 16f * unit, 5f * unit, 2);
    }

    private void drawPowerup(Canvas canvas, PowerUp p, float unit) {
        float bob = (float) Math.sin(p.phase) * 4f * unit;
        float x = p.x;
        float y = p.y + bob;
        int[] glow = {0x5561E8FF, 0x55FF8AE0, 0x55B7FF4A};
        // Пульсирующее свечение цвета бонуса.
        float pulse = 1f + 0.15f * (float) Math.sin(p.phase * 3f);
        paint.setShader(new RadialGradient(x, y, 30f * unit * pulse,
                glow[p.type], glow[p.type] & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, 30f * unit * pulse, paint);
        paint.setShader(null);
        // Тёмная «монета»-подложка под иконкой.
        paint.setColor(0xE0101A2E);
        canvas.drawCircle(x, y, 21f * unit, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * unit);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(x, y, 21f * unit, paint);
        paint.setStyle(Paint.Style.FILL);
        // Иконка способности.
        Bitmap icon = bonusIcons[p.type];
        float s = 30f * unit;
        paint.setFilterBitmap(false);
        canvas.drawBitmap(icon, new Rect(0, 0, icon.getWidth(), icon.getHeight()),
                new RectF(x - s / 2f, y - s / 2f, x + s / 2f, y + s / 2f), paint);
        paint.setFilterBitmap(true);
    }

    private void drawShieldBubble(Canvas canvas, float unit) {
        float cx = witchX;
        float cy = witchY - 32f * unit;
        float r = 56f * unit;
        float alpha = shieldTimer < 1.2f ? Math.min(1f, shieldTimer / 1.2f) : 1f; // мигает на исходе
        if (shieldTimer < 1.2f && ((int) (shieldTimer * 10f) % 2 == 0)) {
            alpha *= 0.35f;
        }
        paint.setShader(new RadialGradient(cx, cy, r,
                withAlpha(0x0061E8FF, 0f), withAlpha(0xFF61E8FF, alpha * 0.5f), Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * unit);
        paint.setColor(withAlpha(0xFFBDEBFF, alpha * 0.9f));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // Дрон доставки Ozon: корпус, два винта-блюра и подвешенная коробка.
    private void drawDrone(Canvas canvas, Drone drone, float unit) {
        float x = drone.x;
        float y = drone.y + (float) Math.sin(drone.phase * 4f) * 2f * unit; // лёгкое покачивание (визуал)

        // Тень на полу — видно, где дрон по горизонтали (чтобы не прыгать под ним).
        float groundY = getHeight() - 48f * unit;
        paint.setColor(0x33101820);
        canvas.drawOval(x - 20f * unit, groundY - 3f * unit,
                x + 20f * unit, groundY + 5f * unit, paint);

        // Лучи-штанги.
        paint.setColor(0xFF2B3A52);
        paint.setStrokeWidth(4f * unit);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(x - 22f * unit, y - 6f * unit, x + 22f * unit, y - 6f * unit, paint);

        // Винты — мелькающий блюр.
        float spin = (float) Math.sin(drone.phase * 40f);
        paint.setColor(0x88BFD4FF);
        for (int s = -1; s <= 1; s += 2) {
            float px = x + s * 22f * unit;
            canvas.drawOval(px - (10f + spin * 3f) * unit, y - 11f * unit,
                    px + (10f + spin * 3f) * unit, y - 7f * unit, paint);
        }
        paint.setColor(0xFFCFE6FF);
        canvas.drawCircle(x - 22f * unit, y - 6f * unit, 2.5f * unit, paint);
        canvas.drawCircle(x + 22f * unit, y - 6f * unit, 2.5f * unit, paint);

        // Корпус.
        paint.setColor(0xFF1C2740);
        canvas.drawRoundRect(x - 15f * unit, y - 8f * unit, x + 15f * unit, y + 8f * unit,
                6f * unit, 6f * unit, paint);
        paint.setColor(0xFF0B63CE);
        canvas.drawRoundRect(x - 15f * unit, y - 8f * unit, x + 15f * unit, y - 2f * unit,
                6f * unit, 6f * unit, paint);
        // Мигающий красный LED.
        paint.setColor(((int) (drone.phase * 6f) % 2 == 0) ? 0xFFFF4D4D : 0xFF5A1414);
        canvas.drawCircle(x, y + 3f * unit, 2.6f * unit, paint);

        // Подвешенная коробка (груз).
        paint.setColor(0xFF8A572F);
        canvas.drawRect(x - 8f * unit, y + 8f * unit, x + 8f * unit, y + 22f * unit, paint);
        paint.setColor(0xFFB9854E);
        canvas.drawRect(x - 8f * unit, y + 8f * unit, x + 8f * unit, y + 12f * unit, paint);
    }

    private void drawReturn(Canvas canvas, ReturnBox box, float unit) {
        // Красное предупреждающее свечение — это летящий снаряд.
        paint.setShader(new RadialGradient(box.x, box.y, 26f * unit,
                0x55FF5A5A, 0x00FF5A5A, Shader.TileMode.CLAMP));
        canvas.drawCircle(box.x, box.y, 26f * unit, paint);
        paint.setShader(null);

        canvas.save();
        canvas.rotate(box.rotation, box.x, box.y);
        int cw = crateSheet.getWidth() / 3;
        int ch = crateSheet.getHeight() / 3;
        int col = box.type % 3;
        int row = box.type / 3;
        Rect source = new Rect(col * cw, row * ch, (col + 1) * cw, (row + 1) * ch);
        float w = 34f * unit;
        float h = w * ch / cw;
        paint.setColor(Color.WHITE);
        paint.setFilterBitmap(false);
        canvas.drawBitmap(crateSheet, source,
                new RectF(box.x - w / 2f, box.y - h / 2f, box.x + w / 2f, box.y + h / 2f), paint);
        paint.setFilterBitmap(true);
        canvas.restore();
    }

    private void drawCustomer(Canvas canvas, Customer customer, float unit) {
        float x = customer.x;
        float y = customer.y + (float) Math.sin(customer.phase * 6f) * 3f * unit;

        // Клиент — пиксельный спрайт в стиле остальных (4 кадра ходьбы, лицом влево).
        int frames = customerSheet.getWidth() / customerSheet.getHeight();
        int frame = (int) (customer.phase * 8f) % frames;
        int frameWidth = customerSheet.getWidth() / frames;
        Rect source = new Rect(frame * frameWidth, 0,
                (frame + 1) * frameWidth, customerSheet.getHeight());
        float size = 78f * unit;
        RectF destination = new RectF(x - size / 2f, y - size * 0.62f,
                x + size / 2f, y + size * 0.38f);
        paint.setColor(Color.WHITE);
        paint.setFilterBitmap(false);
        canvas.drawBitmap(customerSheet, source, destination, paint);
        paint.setFilterBitmap(true);

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
        float baseY = customer.y + 30f * unit;
        float feetY = baseY - hop * 16f * unit;

        // Тень: шире, когда жаба на земле, и собирается в прыжке.
        paint.setColor(0x55204000);
        float shadow = 24f * unit * (1f - 0.35f * hop);
        canvas.drawOval(x - shadow, baseY - 2f * unit,
                x + shadow, baseY + 8f * unit, paint);

        // Спрайт-лист: 8 кадров 32x32 в ряд.
        int frames = frogSheet.getWidth() / frogSheet.getHeight();
        int frame = (int) (worldTime * 12f) % frames;
        int frameWidth = frogSheet.getWidth() / frames;
        Rect source = new Rect(frame * frameWidth, 0,
                (frame + 1) * frameWidth, frogSheet.getHeight());
        float size = 62f * unit;
        RectF destination = new RectF(x - size / 2f, feetY - size, x + size / 2f, feetY);
        paint.setColor(Color.WHITE);
        paint.setFilterBitmap(false);
        canvas.drawBitmap(frogSheet, source, destination, paint);
        paint.setFilterBitmap(true);

        drawStar(canvas, x, feetY - size - 4f * unit, 6f * unit,
                worldTime * 100f, 0xFFFFE56B);
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
            int fill = Math.max(0, Math.min(2, health - i * 2)); // 0 пусто, 1 половина, 2 полное
            float beat = (fill > 0 && flashTimer > 0f)
                    ? 1f + 0.25f * (float) Math.sin(worldTime * 30f) : 1f;
            drawHeart(canvas, (176f + i * 25f) * unit, 35f * unit, 9f * unit * beat, fill);
        }

        // Комбо-множитель за посылки.
        int mult = comboMultiplier();
        if (mult > 1) {
            float pop = 1f + 0.08f * (float) Math.sin(worldTime * 16f);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setTextSize(17f * unit * pop);
            textPaint.setColor(0xFFFFE56B);
            canvas.drawText("КОМБО x" + mult, 20f * unit, 82f * unit, textPaint);
        }

        // Активные бонусы: иконка + полоска остатка времени.
        float bx = 20f * unit;
        float by = 96f * unit;
        bx = drawActiveBonus(canvas, bx, by, 0, shieldTimer, 6f, unit);
        bx = drawActiveBonus(canvas, bx, by, 1, magnetTimer, 7f, unit);
        bx = drawActiveBonus(canvas, bx, by, 2, doubleTimer, 7f, unit);
        bx = drawActiveBonus(canvas, bx, by, 2, boostTimer, 12f, unit);

        // Счётчики уезжают под кнопки паузы/рестарта (правый верхний угол).
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(14f * unit);
        textPaint.setColor(0xFFFFF3B0);
        canvas.drawText("ПОСЫЛКИ: " + packagesCollected,
                getWidth() - 16f * unit, 78f * unit, textPaint);
        // Прогресс по карте.
        int mapPct = getWidth() > 0
                ? (int) (distance / (getWidth() * DIFFICULTY_SCREENS) * 100f) : 0;
        textPaint.setColor(0xFFB9A9E0);
        canvas.drawText("КАРТА: " + mapPct + "%",
                getWidth() - 16f * unit, 98f * unit, textPaint);
    }

    private float drawActiveBonus(Canvas canvas, float bx, float by, int type,
                                  float timer, float maxTime, float unit) {
        if (timer <= 0f) {
            return bx;
        }
        float size = 26f * unit;
        Bitmap icon = bonusIcons[type];
        paint.setColor(Color.WHITE);
        paint.setFilterBitmap(false);
        canvas.drawBitmap(icon, new Rect(0, 0, icon.getWidth(), icon.getHeight()),
                new RectF(bx, by, bx + size, by + size), paint);
        paint.setFilterBitmap(true);
        float frac = Math.min(1f, timer / maxTime);
        int[] cols = {0xFF61E8FF, 0xFFFF8AE0, 0xFFB7FF4A};
        paint.setColor(0x66000000);
        canvas.drawRoundRect(bx, by + size + 2f * unit, bx + size, by + size + 6f * unit,
                2f * unit, 2f * unit, paint);
        paint.setColor(cols[type]);
        canvas.drawRoundRect(bx, by + size + 2f * unit, bx + size * frac, by + size + 6f * unit,
                2f * unit, 2f * unit, paint);
        return bx + size + 8f * unit;
    }

    // Размещение кнопок паузы/рестарта в правом верхнем углу — единое для отрисовки и тача.
    private void layoutHudButtons() {
        float unit = getHeight() / 430f;
        float size = 40f * unit;
        float gap = 10f * unit;
        float top = 14f * unit;
        float right = getWidth() - 14f * unit;
        pauseButton.set(right - size, top, right, top + size);
        restartButton.set(right - size * 2f - gap, top, right - size - gap, top + size);
        soundButton.set(right - size * 3f - gap * 2f, top, right - size * 2f - gap * 2f, top + size);
    }

    private void drawHudButtons(Canvas canvas, float unit) {
        layoutHudButtons();

        // Кнопка паузы — две вертикальные полоски.
        drawRoundButton(canvas, pauseButton, unit);
        paint.setColor(0xFFFFFFFF);
        float cx = pauseButton.centerX();
        float cy = pauseButton.centerY();
        float barH = pauseButton.height() * 0.28f;
        float barW = pauseButton.width() * 0.11f;
        canvas.drawRoundRect(cx - barW * 2.0f, cy - barH, cx - barW * 0.6f, cy + barH,
                barW * 0.5f, barW * 0.5f, paint);
        canvas.drawRoundRect(cx + barW * 0.6f, cy - barH, cx + barW * 2.0f, cy + barH,
                barW * 0.5f, barW * 0.5f, paint);

        // Кнопка рестарта — круговая стрелка.
        drawRoundButton(canvas, restartButton, unit);
        drawRestartIcon(canvas, restartButton, unit);

        // Кнопка звука — динамик, перечёркнутый если выключен.
        drawRoundButton(canvas, soundButton, unit);
        drawSoundIcon(canvas, soundButton, unit);
    }

    // Ряд кнопок-инвентаря по центру сверху: показываются только купленные предметы.
    private int[] ownedCounts() {
        return new int[]{ownShield, ownMagnet, ownBoostX2, ownBoostX3, ownHeal};
    }

    private void layoutInventory() {
        int[] owned = ownedCounts();
        invCount = 0;
        for (int i = 0; i < 5; i++) {
            if (owned[i] > 0) {
                invSlotItem[invCount] = i;
                invCount++;
            }
        }
        float unit = getHeight() / 430f;
        float size = 44f * unit;
        float gap = 10f * unit;
        float totalW = invCount * size + Math.max(0, invCount - 1) * gap;
        float startX = getWidth() / 2f - totalW / 2f;
        float top = 12f * unit;
        for (int s = 0; s < invCount; s++) {
            float x = startX + s * (size + gap);
            invButtons[s].set(x, top, x + size, top + size);
        }
    }

    private void drawInventory(Canvas canvas, float unit) {
        layoutInventory();
        int[] owned = ownedCounts();
        for (int s = 0; s < invCount; s++) {
            int item = invSlotItem[s];
            RectF b = invButtons[s];
            drawRoundButton(canvas, b, unit);
            float ic = b.width() * 0.6f;
            if (item == 4) {
                drawMedkit(canvas, b.centerX(), b.centerY(), ic);
            } else {
                Bitmap icon = bonusIcons[SHOP_ICON[item]];
                paint.setColor(Color.WHITE);
                paint.setFilterBitmap(false);
                canvas.drawBitmap(icon, new Rect(0, 0, icon.getWidth(), icon.getHeight()),
                        new RectF(b.centerX() - ic / 2f, b.centerY() - ic / 2f,
                                b.centerX() + ic / 2f, b.centerY() + ic / 2f), paint);
                paint.setFilterBitmap(true);
            }
            // Метка тира усилителя.
            if (item == 2 || item == 3) {
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor(0xFF1B0B38);
                textPaint.setTextSize(11f * unit);
                canvas.drawText(item == 2 ? "x2" : "x3", b.centerX(), b.bottom - 3f * unit, textPaint);
            }
            // Счётчик в углу.
            paint.setColor(0xFF1B0B38);
            canvas.drawCircle(b.right - 4f * unit, b.top + 4f * unit, 8f * unit, paint);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(0xFFFFE56B);
            textPaint.setTextSize(11f * unit);
            canvas.drawText("" + owned[item], b.right - 4f * unit, b.top + 8f * unit, textPaint);
        }
    }

    // Применить купленный бонус (item: 0 щит,1 магнит,2 x2,3 x3,4 хилка).
    private void activateItem(int item) {
        if (state != STATE_PLAYING) {
            return;
        }
        float unit = getHeight() / 430f;
        switch (item) {
            case 0:
                if (ownShield <= 0) return;
                shieldTimer = 6f;
                ownShield--;
                addPopup(witchX, witchY - 96f * unit, "ЩИТ!", 0xFF61E8FF, unit);
                break;
            case 1:
                if (ownMagnet <= 0) return;
                magnetTimer = 7f;
                ownMagnet--;
                addPopup(witchX, witchY - 96f * unit, "МАГНИТ!", 0xFFFF8AE0, unit);
                break;
            case 2:
                if (ownBoostX2 <= 0) return;
                boostTimer = 12f;
                boostMul = 2;
                ownBoostX2--;
                addPopup(witchX, witchY - 96f * unit, "x2 ОЧКИ!", 0xFFB7FF4A, unit);
                break;
            case 3:
                if (ownBoostX3 <= 0) return;
                boostTimer = 12f;
                boostMul = 3;
                ownBoostX3--;
                addPopup(witchX, witchY - 96f * unit, "x3 ОЧКИ!", 0xFFB7FF4A, unit);
                break;
            default:
                if (ownHeal <= 0) return;
                if (health >= MAX_HEALTH) {
                    playTone(ToneGenerator.TONE_PROP_NACK, 60); // полное здоровье — не тратим
                    return;
                }
                health = Math.min(MAX_HEALTH, health + 3); // 1,5 сердца
                ownHeal--;
                addPopup(witchX, witchY - 96f * unit, "+1,5", 0xFF8CFF6B, unit);
                break;
        }
        saveShopState();
        burst(witchX, witchY - 30f * unit, 0xFFFFE56B, 20, unit);
        playTone(ToneGenerator.TONE_PROP_ACK, 110);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void drawSoundIcon(Canvas canvas, RectF b, float unit) {
        float cx = b.centerX();
        float cy = b.centerY();
        float s = b.width() * 0.30f;
        paint.setColor(0xFFFFFFFF);
        // Корпус динамика.
        canvas.drawRect(cx - s, cy - s * 0.4f, cx - s * 0.4f, cy + s * 0.4f, paint);
        Path cone = new Path();
        cone.moveTo(cx - s * 0.4f, cy - s * 0.4f);
        cone.lineTo(cx + s * 0.2f, cy - s);
        cone.lineTo(cx + s * 0.2f, cy + s);
        cone.lineTo(cx - s * 0.4f, cy + s * 0.4f);
        cone.close();
        canvas.drawPath(cone, paint);
        if (soundOn) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * unit);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawArc(cx - s * 0.3f, cy - s * 0.7f, cx + s * 0.9f, cy + s * 0.7f, -45f, 90f, false, paint);
            canvas.drawArc(cx - s * 0.1f, cy - s, cx + s * 1.4f, cy + s, -45f, 90f, false, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.4f * unit);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xFFFF6B6B);
            canvas.drawLine(cx + s * 0.4f, cy - s * 0.7f, cx + s * 1.1f, cy + s * 0.7f, paint);
            canvas.drawLine(cx + s * 1.1f, cy - s * 0.7f, cx + s * 0.4f, cy + s * 0.7f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawRoundButton(Canvas canvas, RectF rect, float unit) {
        paint.setColor(0xCC0A1222);
        canvas.drawRoundRect(rect, 12f * unit, 12f * unit, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * unit);
        paint.setColor(0x55FFFFFF);
        canvas.drawRoundRect(rect, 12f * unit, 12f * unit, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawRestartIcon(Canvas canvas, RectF b, float unit) {
        float cx = b.centerX();
        float cy = b.centerY();
        float r = b.width() * 0.25f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f * unit);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFFFFFFF);
        // Дуга с разрывом сверху — туда поставим стрелку.
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, -60f, 290f, false, paint);
        paint.setStyle(Paint.Style.FILL);
        // Наконечник стрелки на конце дуги (угол -60+290 = 230°), вдоль касательной.
        double a = Math.toRadians(230f);
        float ex = cx + (float) Math.cos(a) * r;
        float ey = cy + (float) Math.sin(a) * r;
        canvas.save();
        canvas.translate(ex, ey);
        canvas.rotate(230f + 90f); // касательная по часовой стрелке
        float s = 5f * unit;
        Path arrow = new Path();
        arrow.moveTo(s, 0f);
        arrow.lineTo(-s, s * 0.9f);
        arrow.lineTo(-s, -s * 0.9f);
        arrow.close();
        canvas.drawPath(arrow, paint);
        canvas.restore();
    }

    private void drawPauseOverlay(Canvas canvas) {
        float unit = getHeight() / 430f;
        paint.setColor(0xC014082E);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFFFE56B);
        textPaint.setTextSize(36f * unit);
        textPaint.setShadowLayer(8f * unit, 0f, 3f * unit, 0xFF120323);
        canvas.drawText("ПАУЗА", getWidth() / 2f, getHeight() * 0.34f, textPaint);
        textPaint.clearShadowLayer();

        float bw = 220f * unit;
        float bh = 56f * unit;
        float cx = getWidth() / 2f;
        float ry = getHeight() * 0.44f;
        resumeButton.set(cx - bw / 2f, ry, cx + bw / 2f, ry + bh);
        drawMenuButton(canvas, resumeButton, "ПРОДОЛЖИТЬ", 0xFF2BD17E, unit);

        float ry2 = ry + bh + 18f * unit;
        overlayRestartButton.set(cx - bw / 2f, ry2, cx + bw / 2f, ry2 + bh);
        drawMenuButton(canvas, overlayRestartButton, "ЗАНОВО", 0xFFF15BB5, unit);
    }

    private void drawMenuButton(Canvas canvas, RectF rect, String label, int color, float unit) {
        paint.setColor(color);
        paint.setShadowLayer(12f * unit, 0f, 5f * unit, 0x99000000);
        canvas.drawRoundRect(rect, 24f * unit, 24f * unit, paint);
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * unit);
        paint.setColor(0xDDFFFFFF);
        canvas.drawRoundRect(rect, 24f * unit, 24f * unit, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(21f * unit);
        textPaint.setColor(Color.WHITE);
        canvas.drawText(label, rect.centerX(),
                rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
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
        canvas.drawText("ВЕДИ ПАЛЬЦЕМ — ЛЕВО/ПРАВО", half * 0.5f, getHeight() * 0.42f, textPaint);
        canvas.drawText("тап — прыжок (двойной)", half * 0.5f, getHeight() * 0.50f, textPaint);
        canvas.drawText("еда чинит сердечко", half * 0.5f, getHeight() * 0.58f, textPaint);
        canvas.drawText("ТАП СПРАВА — ОГОНЬ", half * 1.5f, getHeight() * 0.48f, textPaint);
        drawStar(canvas, half * 1.5f, getHeight() * 0.58f, 9f * unit,
                worldTime * 60f, withAlpha(0xFFFFE56B, alpha));
    }

    // fill: 0 — пустое, 1 — половина (левая часть), 2 — полное сердце.
    private void drawHeart(Canvas canvas, float x, float y, float size, int fill) {
        drawHeartPath(canvas, x, y, size, 0xFF695777); // фон-контур пустого сердца
        if (fill >= 2) {
            drawHeartPath(canvas, x, y, size, 0xFFFF4D7A);
        } else if (fill == 1) {
            canvas.save();
            canvas.clipRect(x - size * 2f, y - size * 2f, x, y + size * 2f);
            drawHeartPath(canvas, x, y, size, 0xFFFF4D7A);
            canvas.restore();
        }
    }

    private void drawHeartPath(Canvas canvas, float x, float y, float size, int color) {
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
        float cx = getWidth() / 2f;
        paint.setColor(0xC018082E);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        RectF card = new RectF(getWidth() * 0.20f, 30f * unit,
                getWidth() * 0.80f, getHeight() - 22f * unit);
        paint.setColor(0xF03B1B68);
        canvas.drawRoundRect(card, 26f * unit, 26f * unit, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f * unit);
        paint.setColor(0x99FFFFFF);
        canvas.drawRoundRect(card, 26f * unit, 26f * unit, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFFFE56B);
        textPaint.setTextSize(28f * unit);
        String title = newHighScore ? "НОВЫЙ РЕКОРД!"
                : lostToBoss ? "СМЕНА ЗАКОНЧЕНА" : "СМЕНА ОКОНЧЕНА!";
        canvas.drawText(title, cx, 70f * unit, textPaint);

        textPaint.setTextSize(22f * unit);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("Очки: " + score, cx, 104f * unit, textPaint);
        textPaint.setTextSize(14f * unit);
        textPaint.setColor(0xFF7DEBFF);
        canvas.drawText("Рекорд очков: " + highScore, cx, 126f * unit, textPaint);

        // Статистика забега.
        textPaint.setTextSize(14f * unit);
        textPaint.setColor(0xFFE6DCFF);
        canvas.drawText("Посылки: " + packagesCollected + "    Сбито дронов: " + dronesDowned,
                cx, 154f * unit, textPaint);
        canvas.drawText("Отбито возвратов: " + returnsDeflected + "    Карта: "
                + (int) (distance / (getWidth() * DIFFICULTY_SCREENS) * 100f) + "%", cx, 175f * unit, textPaint);
        textPaint.setColor(0xFFFFE56B);
        canvas.drawText("Макс. комбо: x" + runMaxCombo + "   (рекорд x" + comboRecord + ")",
                cx, 196f * unit, textPaint);

        // Монеты за забег.
        drawCoin(canvas, cx - 56f * unit, 222f * unit, 9f * unit);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(0xFFFFD84D);
        textPaint.setTextSize(16f * unit);
        canvas.drawText("+" + coinsEarned + " монет   (всего " + coins + ")",
                cx - 44f * unit, 227f * unit, textPaint);

        // Кнопки: ЕЩЁ РАЗ и МАГАЗИН.
        float bw = 150f * unit;
        float bh = 46f * unit;
        float by = getHeight() - bh - 34f * unit;
        gameOverRestartButton.set(cx - bw - 8f * unit, by, cx - 8f * unit, by + bh);
        gameOverShopButton.set(cx + 8f * unit, by, cx + bw + 8f * unit, by + bh);
        paint.setColor(0xFFF15BB5);
        canvas.drawRoundRect(gameOverRestartButton, 20f * unit, 20f * unit, paint);
        paint.setColor(0xFF2BA6C7);
        canvas.drawRoundRect(gameOverShopButton, 20f * unit, 20f * unit, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(19f * unit);
        canvas.drawText("ЕЩЁ РАЗ", gameOverRestartButton.centerX(),
                gameOverRestartButton.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
        canvas.drawText("МАГАЗИН", gameOverShopButton.centerX(),
                gameOverShopButton.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
    }

    // ------------------------------------------------------------- TOUCH

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (state == STATE_START) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (shopButton.contains(event.getX(), event.getY())) {
                    state = STATE_SHOP;
                } else {
                    startGame();
                }
            }
            return true;
        }
        if (state == STATE_SHOP) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float x = event.getX();
                float y = event.getY();
                if (backButton.contains(x, y)) {
                    state = STATE_START;
                } else {
                    for (int i = 0; i < buyButtons.length; i++) {
                        if (buyButtons[i].contains(x, y)) {
                            buyItem(i);
                            break;
                        }
                    }
                }
            }
            return true;
        }
        if (state == STATE_GAME_OVER) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float x = event.getX();
                float y = event.getY();
                if (gameOverShopButton.contains(x, y)) {
                    state = STATE_SHOP;
                } else {
                    startGame();
                }
            }
            return true;
        }
        if (state == STATE_PAUSED) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float x = event.getX();
                float y = event.getY();
                if (resumeButton.contains(x, y)) {
                    resumeGame();
                } else if (overlayRestartButton.contains(x, y)) {
                    startGame();
                }
            }
            return true;
        }

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                float x = event.getX(index);
                float y = event.getY(index);
                // Кнопки паузы/рестарта в HUD имеют приоритет над стрельбой/ведением.
                layoutHudButtons();
                if (pauseButton.contains(x, y)) {
                    pauseGame();
                    return true;
                }
                if (restartButton.contains(x, y)) {
                    startGame();
                    return true;
                }
                if (soundButton.contains(x, y)) {
                    soundOn = !soundOn;
                    preferences.edit().putBoolean("sound_on", soundOn).apply();
                    if (soundOn) {
                        playTone(ToneGenerator.TONE_PROP_ACK, 60);
                    }
                    return true;
                }
                layoutInventory();
                boolean usedInv = false;
                for (int s = 0; s < invCount; s++) {
                    if (invButtons[s].contains(x, y)) {
                        activateItem(invSlotItem[s]);
                        usedInv = true;
                        break;
                    }
                }
                if (usedInv) {
                    return true;
                }
                handlePointerDown(event.getPointerId(index), x, y);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float unit = getHeight() / 430f;
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if (event.getPointerId(i) == steerPointerId) {
                        float px = event.getX(i);
                        // Относительное ведение: ведьма едет за пальцем, без рывка к точке касания.
                        targetX += px - steerLastX;
                        targetX = clamp(targetX, 72f * unit, getWidth() * 0.48f);
                        steerLastX = px;
                        if (Math.abs(px - steerDownX) > getWidth() * 0.035f) {
                            steerMoved = true; // палец поехал → это ведение, не прыжок
                        }
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int index = event.getActionIndex();
                handlePointerUp(event.getPointerId(index));
                return true;
            }
            case MotionEvent.ACTION_UP: {
                handlePointerUp(event.getPointerId(event.getActionIndex()));
                steerPointerId = -1;
                firePointerId = -1;
                firing = false;
                return true;
            }
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
            // Левая половина: короткий тап — прыжок, ведение пальцем — лево/право.
            if (steerPointerId == -1) {
                steerPointerId = pointerId;
                steerDownX = x;
                steerLastX = x;
                steerMoved = false;
            }
        } else {
            // Правая половина: один выстрел на каждое нажатие (без автоогня).
            firePointerId = pointerId;
            shoot();
        }
    }

    private void handlePointerUp(int pointerId) {
        if (pointerId == steerPointerId) {
            if (!steerMoved) {
                jump(); // тап без ведения = прыжок (двойной — двумя тапами)
            }
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
        float y;
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
        float vy;        // вертикальная скорость — для дуги влёта и отскоков
        float spin;
        final int type;
        float rotation;
        int bounces;

        LostItem(float x, float y, float vy, float spin, int type) {
            this.x = x;
            this.y = y;
            this.vy = vy;
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

    private static final class Food {
        float x;
        final float y;
        final int type;
        float phase;

        Food(float x, float y, int type, float phase) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.phase = phase;
        }
    }

    private static final class PowerUp {
        float x;
        final float y;
        final int type; // 0=щит, 1=магнит, 2=усилитель
        float phase;

        PowerUp(float x, float y, int type, float phase) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.phase = phase;
        }
    }

    private static final class Customer {
        float x;
        float y;
        float baseY;
        float phase;
        boolean frog;
        float frogTimer = 10f;
        float throwTimer = 1.0f;
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

    private static final class Drone {
        float x;
        float y;
        float phase;
        boolean hit;

        Drone(float x, float y, float phase) {
            this.x = x;
            this.y = y;
            this.phase = phase;
        }
    }

    private static final class ReturnBox {
        float x;
        float y;
        final float vx;
        final float spin;
        final int type;
        float rotation;

        ReturnBox(float x, float y, float vx, float spin, int type) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.spin = spin;
            this.type = type;
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

    private static final class FloatingText {
        float x;
        float y;
        final String text;
        final int color;
        final float size;
        float life = 0.9f;
        final float maxLife = 0.9f;

        FloatingText(float x, float y, String text, int color, float size) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
            this.size = size;
        }
    }
}

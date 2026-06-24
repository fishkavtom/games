package com.happytetris.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class TetrisView extends View {
    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int[] COLORS = {
            0x00000000, 0xFF2DE2E6, 0xFFFFD166, 0xFFB388FF,
            0xFF70E000, 0xFFFF4D6D, 0xFF4D96FF, 0xFFFF8C42
    };

    private static final int[][][][] SHAPES = {
            {
                    {{0, 1}, {1, 1}, {2, 1}, {3, 1}},
                    {{2, 0}, {2, 1}, {2, 2}, {2, 3}},
                    {{0, 2}, {1, 2}, {2, 2}, {3, 2}},
                    {{1, 0}, {1, 1}, {1, 2}, {1, 3}}
            },
            {
                    {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
                    {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
                    {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
                    {{1, 0}, {2, 0}, {1, 1}, {2, 1}}
            },
            {
                    {{1, 0}, {0, 1}, {1, 1}, {2, 1}},
                    {{1, 0}, {1, 1}, {2, 1}, {1, 2}},
                    {{0, 1}, {1, 1}, {2, 1}, {1, 2}},
                    {{1, 0}, {0, 1}, {1, 1}, {1, 2}}
            },
            {
                    {{1, 0}, {2, 0}, {0, 1}, {1, 1}},
                    {{1, 0}, {1, 1}, {2, 1}, {2, 2}},
                    {{1, 1}, {2, 1}, {0, 2}, {1, 2}},
                    {{0, 0}, {0, 1}, {1, 1}, {1, 2}}
            },
            {
                    {{0, 0}, {1, 0}, {1, 1}, {2, 1}},
                    {{2, 0}, {1, 1}, {2, 1}, {1, 2}},
                    {{0, 1}, {1, 1}, {1, 2}, {2, 2}},
                    {{1, 0}, {0, 1}, {1, 1}, {0, 2}}
            },
            {
                    {{0, 0}, {0, 1}, {1, 1}, {2, 1}},
                    {{1, 0}, {2, 0}, {1, 1}, {1, 2}},
                    {{0, 1}, {1, 1}, {2, 1}, {2, 2}},
                    {{1, 0}, {1, 1}, {0, 2}, {1, 2}}
            },
            {
                    {{2, 0}, {0, 1}, {1, 1}, {2, 1}},
                    {{1, 0}, {1, 1}, {1, 2}, {2, 2}},
                    {{0, 1}, {1, 1}, {2, 1}, {0, 2}},
                    {{0, 0}, {1, 0}, {1, 1}, {1, 2}}
            }
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final int[][] board = new int[ROWS][COLS];
    private final boolean[] clearingRows = new boolean[ROWS];
    private final List<Integer> bag = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<Bubble> bubbles = new ArrayList<>();
    private final RectF pauseButton = new RectF();
    private final RectF startButton = new RectF();
    private final RectF[] controlButtons = {
            new RectF(), new RectF(), new RectF(), new RectF(), new RectF()
    };
    private final ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 36);
    private final SharedPreferences preferences;
    private final Bitmap splashArt;

    private int currentType;
    private int nextType;
    private int rotation;
    private int pieceX;
    private int pieceY;
    private int score;
    private int bestScore;
    private int lines;
    private int level = 1;
    private int combo = -1;
    private float visualX;
    private float visualY;
    private float dropTimer;
    private float clearProgress;
    private float boardLeft;
    private float boardTop;
    private float cellSize;
    private float touchStartX;
    private float touchStartY;
    private float lastTouchX;
    private float lastTouchY;
    private long lastFrameNanos;
    private boolean clearing;
    private boolean paused;
    private boolean gameOver;
    private boolean startScreen = true;
    private boolean loopRunning = true;
    private boolean touchMoved;
    private int pressedControl = -1;

    public TetrisView(Context context) {
        super(context);
        preferences = context.getSharedPreferences("happy_tetris", Context.MODE_PRIVATE);
        bestScore = preferences.getInt("best_score", 0);
        splashArt = BitmapFactory.decodeResource(getResources(), R.drawable.splash_art);
        setFocusable(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        for (int i = 0; i < 18; i++) {
            bubbles.add(new Bubble(random.nextFloat(), random.nextFloat(),
                    0.04f + random.nextFloat() * 0.09f, 0.018f + random.nextFloat() * 0.035f));
        }
        restartGame();
    }

    public void resumeGameLoop() {
        loopRunning = true;
        lastFrameNanos = 0L;
        postInvalidateOnAnimation();
    }

    public void pauseGameLoop() {
        loopRunning = false;
    }

    private void restartGame() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                board[row][col] = 0;
            }
            clearingRows[row] = false;
        }
        bag.clear();
        particles.clear();
        score = 0;
        lines = 0;
        level = 1;
        combo = -1;
        clearing = false;
        paused = false;
        gameOver = false;
        nextType = takeFromBag();
        spawnPiece();
        lastFrameNanos = 0L;
        invalidate();
    }

    private int takeFromBag() {
        if (bag.isEmpty()) {
            for (int i = 0; i < SHAPES.length; i++) {
                bag.add(i);
            }
            Collections.shuffle(bag, random);
        }
        return bag.remove(bag.size() - 1);
    }

    private void spawnPiece() {
        currentType = nextType;
        nextType = takeFromBag();
        rotation = 0;
        pieceX = 3;
        pieceY = -1;
        visualX = pieceX;
        visualY = pieceY;
        dropTimer = 0f;
        if (collides(pieceX, pieceY, rotation)) {
            finishGame();
            toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 260);
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 0f : Math.min(0.034f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        if (startScreen) {
            drawStartScreen(canvas);
            if (loopRunning) {
                postInvalidateOnAnimation();
            }
            return;
        }
        if (loopRunning) {
            update(delta);
        }
        drawBackground(canvas);
        calculateLayout();
        drawHeader(canvas);
        drawBoard(canvas);
        drawControls(canvas);
        drawParticles(canvas);
        if (paused || gameOver) {
            drawOverlay(canvas);
        }
        if (loopRunning) {
            postInvalidateOnAnimation();
        }
    }

    private void drawStartScreen(Canvas canvas) {
        if (splashArt != null) {
            float sourceRatio = splashArt.getWidth() / (float) splashArt.getHeight();
            float targetRatio = getWidth() / (float) getHeight();
            Rect source = new Rect(0, 0, splashArt.getWidth(), splashArt.getHeight());
            if (sourceRatio > targetRatio) {
                int sourceWidth = Math.round(splashArt.getHeight() * targetRatio);
                source.left = (splashArt.getWidth() - sourceWidth) / 2;
                source.right = source.left + sourceWidth;
            } else {
                int sourceHeight = Math.round(splashArt.getWidth() / targetRatio);
                source.top = (splashArt.getHeight() - sourceHeight) / 2;
                source.bottom = source.top + sourceHeight;
            }
            canvas.drawBitmap(splashArt, source,
                    new RectF(0f, 0f, getWidth(), getHeight()), paint);
        } else {
            canvas.drawColor(0xFF21105C);
        }

        paint.setShader(new LinearGradient(0f, 0f, 0f, getHeight() * 0.56f,
                0xC900082B, 0x0000082B, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, getWidth(), getHeight() * 0.58f, paint);
        paint.setShader(null);

        float density = getResources().getDisplayMetrics().density;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(Math.min(getWidth() * 0.13f, 54f * density));
        textPaint.setShadowLayer(18f, 0f, 5f, 0xFF2DE2E6);
        canvas.drawText("HAPPY", getWidth() / 2f, getHeight() * 0.18f, textPaint);
        textPaint.setColor(0xFFFFD166);
        textPaint.setShadowLayer(18f, 0f, 5f, 0xFFFF4D9D);
        canvas.drawText("TETRIS", getWidth() / 2f, getHeight() * 0.25f, textPaint);
        textPaint.clearShadowLayer();

        textPaint.setTextSize(16f * density);
        textPaint.setColor(0xFFE7F8FF);
        canvas.drawText("Собирай линии и зажигай комбо!",
                getWidth() / 2f, getHeight() * 0.31f, textPaint);

        float pulse = 1f + 0.025f * (float) Math.sin(System.nanoTime() / 260_000_000d);
        float buttonWidth = getWidth() * 0.58f * pulse;
        float buttonHeight = 64f * density * pulse;
        float centerY = getHeight() * 0.86f;
        startButton.set((getWidth() - buttonWidth) / 2f, centerY - buttonHeight / 2f,
                (getWidth() + buttonWidth) / 2f, centerY + buttonHeight / 2f);
        paint.setShadowLayer(24f, 0f, 7f, 0xFF2DE2E6);
        paint.setColor(0xED16D9D4);
        canvas.drawRoundRect(startButton, buttonHeight / 2f, buttonHeight / 2f, paint);
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f * density);
        paint.setColor(0xCCFFFFFF);
        canvas.drawRoundRect(startButton, buttonHeight / 2f, buttonHeight / 2f, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setTextSize(23f * density);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("ИГРАТЬ", startButton.centerX(),
                startButton.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f,
                textPaint);
        if (bestScore > 0) {
            textPaint.setTextSize(14f * density);
            textPaint.setColor(0xFFFFE66D);
            canvas.drawText("РЕКОРД: " + bestScore, getWidth() / 2f,
                    startButton.bottom + 30f * density, textPaint);
        }
    }

    private void update(float delta) {
        updateBubbles(delta);
        updateParticles(delta);
        if (paused || gameOver) {
            return;
        }
        if (clearing) {
            clearProgress += delta / 0.34f;
            if (clearProgress >= 1f) {
                finishLineClear();
            }
            return;
        }

        float smooth = 1f - (float) Math.exp(-delta * 18f);
        visualX += (pieceX - visualX) * smooth;
        visualY += (pieceY - visualY) * smooth;
        dropTimer += delta;
        float interval = Math.max(0.09f, 0.72f - (level - 1) * 0.055f);
        if (dropTimer >= interval) {
            dropTimer -= interval;
            if (!movePiece(0, 1)) {
                lockPiece();
            }
        }
    }

    private void updateBubbles(float delta) {
        for (Bubble bubble : bubbles) {
            bubble.y -= bubble.speed * delta;
            bubble.phase += delta;
            if (bubble.y < -0.1f) {
                bubble.y = 1.1f;
                bubble.x = random.nextFloat();
            }
        }
    }

    private void updateParticles(float delta) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle particle = particles.get(i);
            particle.life -= delta;
            particle.x += particle.vx * delta;
            particle.y += particle.vy * delta;
            particle.vy += 280f * delta;
            if (particle.life <= 0f) {
                particles.remove(i);
            }
        }
    }

    private void calculateLayout() {
        float density = getResources().getDisplayMetrics().density;
        float sidePadding = 14f * density;
        float headerHeight = 82f * density;
        float controlsHeight = 92f * density;
        float availableHeight = getHeight() - headerHeight - controlsHeight - 18f * density;
        cellSize = Math.min((getWidth() - sidePadding * 2f) / COLS, availableHeight / ROWS);
        boardLeft = (getWidth() - cellSize * COLS) / 2f;
        boardTop = headerHeight;
    }

    private void drawBackground(Canvas canvas) {
        paint.setShader(new LinearGradient(0f, 0f, getWidth(), getHeight(),
                new int[]{0xFF13104A, 0xFF42267A, 0xFF0B7285},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);

        for (int i = 0; i < bubbles.size(); i++) {
            Bubble bubble = bubbles.get(i);
            float x = bubble.x * getWidth() + (float) Math.sin(bubble.phase * 1.5f) * 12f;
            float y = bubble.y * getHeight();
            float radius = bubble.radius * getWidth();
            paint.setColor(i % 3 == 0 ? 0x20FFD166 : i % 3 == 1 ? 0x182DE2E6 : 0x18FF4D9D);
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    private void drawHeader(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(24f * density);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("HAPPY TETRIS", 16f * density, 31f * density, textPaint);

        textPaint.setTextSize(11f * density);
        textPaint.setColor(0xFFBEE9FF);
        canvas.drawText("СЧЁТ", 16f * density, 51f * density, textPaint);
        canvas.drawText("ЛИНИИ", 106f * density, 51f * density, textPaint);
        canvas.drawText("УРОВЕНЬ", 178f * density, 51f * density, textPaint);

        textPaint.setTextSize(18f * density);
        textPaint.setColor(Color.WHITE);
        canvas.drawText(String.format(Locale.US, "%06d", score), 16f * density, 70f * density, textPaint);
        canvas.drawText(String.valueOf(lines), 106f * density, 70f * density, textPaint);
        canvas.drawText(String.valueOf(level), 178f * density, 70f * density, textPaint);

        pauseButton.set(getWidth() - 55f * density, 14f * density,
                getWidth() - 13f * density, 56f * density);
        drawRoundButton(canvas, pauseButton, paused ? 0xFFFFD166 : 0x40FFFFFF, false);
        paint.setColor(Color.WHITE);
        if (paused) {
            Path path = new Path();
            path.moveTo(pauseButton.left + 16f * density, pauseButton.top + 11f * density);
            path.lineTo(pauseButton.left + 16f * density, pauseButton.bottom - 11f * density);
            path.lineTo(pauseButton.right - 11f * density, pauseButton.centerY());
            path.close();
            canvas.drawPath(path, paint);
        } else {
            canvas.drawRoundRect(pauseButton.left + 13f * density, pauseButton.top + 11f * density,
                    pauseButton.left + 18f * density, pauseButton.bottom - 11f * density, 3f, 3f, paint);
            canvas.drawRoundRect(pauseButton.right - 18f * density, pauseButton.top + 11f * density,
                    pauseButton.right - 13f * density, pauseButton.bottom - 11f * density, 3f, 3f, paint);
        }

        drawNextPiece(canvas, getWidth() - 92f * density, 56f * density, 14f * density);
    }

    private void drawNextPiece(Canvas canvas, float left, float top, float previewCell) {
        for (int[] block : SHAPES[nextType][0]) {
            float x = left + block[0] * previewCell;
            float y = top + block[1] * previewCell;
            drawBlock(canvas, x, y, previewCell, COLORS[nextType + 1], 1f);
        }
    }

    private void drawBoard(Canvas canvas) {
        float width = cellSize * COLS;
        float height = cellSize * ROWS;
        paint.setColor(0xA00B0D2A);
        canvas.drawRoundRect(boardLeft - 5f, boardTop - 5f,
                boardLeft + width + 5f, boardTop + height + 5f, 18f, 18f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x506FE7FF);
        canvas.drawRoundRect(boardLeft - 4f, boardTop - 4f,
                boardLeft + width + 4f, boardTop + height + 4f, 17f, 17f, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(0x163A86A8);
        for (int col = 1; col < COLS; col++) {
            float x = boardLeft + col * cellSize;
            canvas.drawRect(x - 0.5f, boardTop, x + 0.5f, boardTop + height, paint);
        }
        for (int row = 1; row < ROWS; row++) {
            float y = boardTop + row * cellSize;
            canvas.drawRect(boardLeft, y - 0.5f, boardLeft + width, y + 0.5f, paint);
        }

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int value = board[row][col];
                if (value == 0) {
                    continue;
                }
                float alpha = 1f;
                float scale = 1f;
                if (clearingRows[row]) {
                    alpha = Math.max(0f, 1f - clearProgress);
                    scale = 1f + (float) Math.sin(clearProgress * Math.PI) * 0.22f;
                }
                drawBlockScaled(canvas, boardLeft + col * cellSize,
                        boardTop + row * cellSize, cellSize, COLORS[value], alpha, scale);
            }
        }

        if (!gameOver && !clearing) {
            int ghostY = pieceY;
            while (!collides(pieceX, ghostY + 1, rotation)) {
                ghostY++;
            }
            for (int[] block : SHAPES[currentType][rotation]) {
                int row = ghostY + block[1];
                if (row >= 0) {
                    drawGhostBlock(canvas, boardLeft + (pieceX + block[0]) * cellSize,
                            boardTop + row * cellSize, cellSize, COLORS[currentType + 1]);
                }
            }

            for (int[] block : SHAPES[currentType][rotation]) {
                float row = visualY + block[1];
                if (row >= -0.95f) {
                    drawBlock(canvas, boardLeft + (visualX + block[0]) * cellSize,
                            boardTop + row * cellSize, cellSize, COLORS[currentType + 1], 1f);
                }
            }
        }

        if (combo > 0 && !gameOver) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(cellSize * 0.72f);
            textPaint.setColor(0xFFFFE66D);
            canvas.drawText("КОМБО x" + (combo + 1), boardLeft + width / 2f,
                    boardTop + cellSize * 2.1f, textPaint);
        }
    }

    private void drawControls(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float top = boardTop + ROWS * cellSize + 12f * density;
        float gap = 7f * density;
        float margin = 12f * density;
        float buttonWidth = (getWidth() - margin * 2f - gap * 4f) / 5f;
        float buttonHeight = Math.min(68f * density, getHeight() - top - 8f * density);
        String[] labels = {"◀", "↻", "▶", "▼", "DROP"};

        for (int i = 0; i < controlButtons.length; i++) {
            float left = margin + i * (buttonWidth + gap);
            controlButtons[i].set(left, top, left + buttonWidth, top + buttonHeight);
            int color = i == 4 ? 0xCCFF4D9D : 0x4DFFFFFF;
            drawRoundButton(canvas, controlButtons[i], color, pressedControl == i);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize((i == 4 ? 13f : 25f) * density);
            textPaint.setColor(Color.WHITE);
            canvas.drawText(labels[i], controlButtons[i].centerX(),
                    controlButtons[i].centerY() - (textPaint.ascent() + textPaint.descent()) / 2f,
                    textPaint);
        }
    }

    private void drawRoundButton(Canvas canvas, RectF rect, int color, boolean pressed) {
        paint.setColor(color);
        float inset = pressed ? 3f : 0f;
        canvas.drawRoundRect(rect.left + inset, rect.top + inset,
                rect.right - inset, rect.bottom - inset, 18f, 18f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x55FFFFFF);
        canvas.drawRoundRect(rect.left + inset, rect.top + inset,
                rect.right - inset, rect.bottom - inset, 18f, 18f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBlock(Canvas canvas, float x, float y, float size, int color, float alpha) {
        drawBlockScaled(canvas, x, y, size, color, alpha, 1f);
    }

    private void drawBlockScaled(Canvas canvas, float x, float y, float size,
                                 int color, float alpha, float scale) {
        float padding = size * 0.075f;
        float scaledSize = size * scale;
        float offset = (size - scaledSize) / 2f;
        RectF rect = new RectF(x + padding + offset, y + padding + offset,
                x + size - padding - offset, y + size - padding - offset);
        paint.setColor(withAlpha(color, alpha));
        canvas.drawRoundRect(rect, size * 0.18f, size * 0.18f, paint);

        paint.setShader(new LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                withAlpha(Color.WHITE, alpha * 0.58f), withAlpha(color, alpha * 0.05f),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect.left + size * 0.08f, rect.top + size * 0.07f,
                rect.right - size * 0.08f, rect.top + size * 0.30f,
                size * 0.10f, size * 0.10f, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, size * 0.045f));
        paint.setColor(withAlpha(Color.WHITE, alpha * 0.48f));
        canvas.drawRoundRect(rect, size * 0.18f, size * 0.18f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawGhostBlock(Canvas canvas, float x, float y, float size, int color) {
        float padding = size * 0.13f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, size * 0.07f));
        paint.setColor(withAlpha(color, 0.38f));
        canvas.drawRoundRect(x + padding, y + padding, x + size - padding, y + size - padding,
                size * 0.15f, size * 0.15f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawParticles(Canvas canvas) {
        for (Particle particle : particles) {
            float alpha = Math.min(1f, particle.life * 2.2f);
            paint.setColor(withAlpha(particle.color, alpha));
            canvas.save();
            canvas.rotate(particle.rotation, particle.x, particle.y);
            canvas.drawRoundRect(particle.x - particle.size, particle.y - particle.size,
                    particle.x + particle.size, particle.y + particle.size,
                    particle.size * 0.35f, particle.size * 0.35f, paint);
            canvas.restore();
            particle.rotation += 5f;
        }
    }

    private void drawOverlay(Canvas canvas) {
        paint.setColor(0xC20B0D2A);
        canvas.drawRoundRect(boardLeft, boardTop, boardLeft + COLS * cellSize,
                boardTop + ROWS * cellSize, 14f, 14f, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(cellSize * 1.05f);
        String title = gameOver ? "ИГРА ОКОНЧЕНА" : "ПАУЗА";
        canvas.drawText(title, getWidth() / 2f, boardTop + ROWS * cellSize * 0.43f, textPaint);
        textPaint.setTextSize(cellSize * 0.56f);
        textPaint.setColor(0xFFFFE66D);
        String subtitle = gameOver ? "Нажми, чтобы сыграть ещё" : "Нажми кнопку паузы";
        canvas.drawText(subtitle, getWidth() / 2f, boardTop + ROWS * cellSize * 0.51f, textPaint);
        if (gameOver) {
            textPaint.setTextSize(cellSize * 0.48f);
            textPaint.setColor(Color.WHITE);
            canvas.drawText("РЕКОРД: " + bestScore, getWidth() / 2f,
                    boardTop + ROWS * cellSize * 0.57f, textPaint);
        }
    }

    private int withAlpha(int color, float alpha) {
        return (Math.max(0, Math.min(255, (int) (alpha * 255f))) << 24) | (color & 0x00FFFFFF);
    }

    private boolean movePiece(int dx, int dy) {
        if (gameOver || paused || clearing) {
            return false;
        }
        int newX = pieceX + dx;
        int newY = pieceY + dy;
        if (collides(newX, newY, rotation)) {
            return false;
        }
        pieceX = newX;
        pieceY = newY;
        return true;
    }

    private void rotatePiece() {
        if (gameOver || paused || clearing) {
            return;
        }
        int newRotation = (rotation + 1) % 4;
        int[] kicks = {0, -1, 1, -2, 2};
        for (int kick : kicks) {
            if (!collides(pieceX + kick, pieceY, newRotation)) {
                pieceX += kick;
                rotation = newRotation;
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 35);
                return;
            }
        }
    }

    private boolean collides(int x, int y, int testRotation) {
        for (int[] block : SHAPES[currentType][testRotation]) {
            int col = x + block[0];
            int row = y + block[1];
            if (col < 0 || col >= COLS || row >= ROWS) {
                return true;
            }
            if (row >= 0 && board[row][col] != 0) {
                return true;
            }
        }
        return false;
    }

    private void hardDrop() {
        if (gameOver || paused || clearing) {
            return;
        }
        int distance = 0;
        while (movePiece(0, 1)) {
            distance++;
        }
        score += distance * 2;
        visualY = pieceY;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        lockPiece();
    }

    private void softDrop() {
        if (movePiece(0, 1)) {
            score++;
            dropTimer = 0f;
        } else if (!gameOver && !paused && !clearing) {
            lockPiece();
        }
    }

    private void lockPiece() {
        if (gameOver || clearing) {
            return;
        }
        for (int[] block : SHAPES[currentType][rotation]) {
            int col = pieceX + block[0];
            int row = pieceY + block[1];
            if (row < 0) {
                finishGame();
                toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 260);
                return;
            }
            board[row][col] = currentType + 1;
            burstAt(col, row, COLORS[currentType + 1], 3);
        }
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 45);
        int fullCount = 0;
        for (int row = 0; row < ROWS; row++) {
            boolean full = true;
            for (int col = 0; col < COLS; col++) {
                if (board[row][col] == 0) {
                    full = false;
                    break;
                }
            }
            clearingRows[row] = full;
            if (full) {
                fullCount++;
                for (int col = 0; col < COLS; col++) {
                    burstAt(col, row, COLORS[board[row][col]], 5);
                }
            }
        }
        if (fullCount > 0) {
            clearing = true;
            clearProgress = 0f;
            combo++;
            int[] points = {0, 100, 300, 500, 800};
            score += points[fullCount] * level + Math.max(0, combo) * 50 * level;
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 150);
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } else {
            combo = -1;
            spawnPiece();
        }
    }

    private void finishLineClear() {
        int writeRow = ROWS - 1;
        int removed = 0;
        for (int readRow = ROWS - 1; readRow >= 0; readRow--) {
            if (clearingRows[readRow]) {
                removed++;
                continue;
            }
            if (writeRow != readRow) {
                System.arraycopy(board[readRow], 0, board[writeRow], 0, COLS);
            }
            writeRow--;
        }
        while (writeRow >= 0) {
            for (int col = 0; col < COLS; col++) {
                board[writeRow][col] = 0;
            }
            writeRow--;
        }
        for (int row = 0; row < ROWS; row++) {
            clearingRows[row] = false;
        }
        lines += removed;
        level = 1 + lines / 10;
        clearing = false;
        spawnPiece();
    }

    private void burstAt(int col, int row, int color, int count) {
        float centerX = boardLeft + (col + 0.5f) * cellSize;
        float centerY = boardTop + (row + 0.5f) * cellSize;
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * (float) Math.PI * 2f;
            float speed = 50f + random.nextFloat() * 170f;
            particles.add(new Particle(centerX, centerY,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed - 60f,
                    color, 2.5f + random.nextFloat() * 4.5f,
                    0.35f + random.nextFloat() * 0.45f));
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (startScreen) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP && startButton.contains(x, y)) {
                startScreen = false;
                restartGame();
                toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 100);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = lastTouchX = x;
                touchStartY = lastTouchY = y;
                touchMoved = false;
                pressedControl = findControl(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressedControl >= 0) {
                    return true;
                }
                float step = Math.max(18f, cellSize * 0.72f);
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;
                if (Math.abs(dx) > step && Math.abs(dx) > Math.abs(dy)) {
                    if (movePiece(dx > 0 ? 1 : -1, 0)) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    lastTouchX = x;
                    lastTouchY = y;
                    touchMoved = true;
                } else if (dy > step) {
                    softDrop();
                    lastTouchY = y;
                    touchMoved = true;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (gameOver) {
                    restartGame();
                    pressedControl = -1;
                    return true;
                }
                if (pauseButton.contains(x, y)) {
                    paused = !paused;
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 45);
                    pressedControl = -1;
                    invalidate();
                    return true;
                }
                int releasedControl = findControl(x, y);
                if (pressedControl >= 0 && releasedControl == pressedControl) {
                    activateControl(pressedControl);
                } else if (!touchMoved && distance(touchStartX, touchStartY, x, y) < cellSize * 0.45f
                        && y >= boardTop && y <= boardTop + ROWS * cellSize) {
                    rotatePiece();
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                } else if (y - touchStartY > cellSize * 4f) {
                    hardDrop();
                }
                pressedControl = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressedControl = -1;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private int findControl(float x, float y) {
        for (int i = 0; i < controlButtons.length; i++) {
            if (controlButtons[i].contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private void activateControl(int control) {
        switch (control) {
            case 0:
                movePiece(-1, 0);
                break;
            case 1:
                rotatePiece();
                break;
            case 2:
                movePiece(1, 0);
                break;
            case 3:
                softDrop();
                break;
            case 4:
                hardDrop();
                break;
            default:
                break;
        }
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void finishGame() {
        gameOver = true;
        if (score > bestScore) {
            bestScore = score;
            preferences.edit().putInt("best_score", bestScore).apply();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        toneGenerator.release();
        super.onDetachedFromWindow();
    }

    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        final int color;
        final float size;
        float life;
        float rotation;

        Particle(float x, float y, float vx, float vy, int color, float size, float life) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.size = size;
            this.life = life;
        }
    }

    private static final class Bubble {
        float x;
        float y;
        final float radius;
        final float speed;
        float phase;

        Bubble(float x, float y, float radius, float speed) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.speed = speed;
            this.phase = x * 8f;
        }
    }
}

package com.vibecode.minesweeper;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.content.Context;
import java.util.*;

public class MainActivity extends Activity {
    Board board;
    Button modeButton;
    Button zoomButton;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(192, 192, 192));
        getWindow().setNavigationBarColor(Color.rgb(192, 192, 192));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(192, 192, 192));

        // Android 15 / targetSdk 35 draws edge-to-edge. Reserve the system-bar area
        // so the toolbar cannot disappear underneath the status bar.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(4, 4, 4, 4);

        HorizontalScrollView toolbarScroll = new HorizontalScrollView(this);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        toolbarScroll.setFillViewport(true);
        toolbarScroll.addView(toolbar, new HorizontalScrollView.LayoutParams(-2, -1));

        modeButton = button("⚑");
        Button easy = button("9×9");
        Button medium = button("16×16");
        Button hard = button("30×16");
        Button restart = button("↻");
        Button minus = button("−");
        zoomButton = button("100%");
        Button plus = button("+");

        toolbar.addView(modeButton, toolLp(64));
        toolbar.addView(easy, toolLp(76));
        toolbar.addView(medium, toolLp(86));
        toolbar.addView(hard, toolLp(86));
        toolbar.addView(restart, toolLp(64));
        toolbar.addView(minus, toolLp(64));
        toolbar.addView(zoomButton, toolLp(82));
        toolbar.addView(plus, toolLp(64));

        root.addView(toolbarScroll, new LinearLayout.LayoutParams(-1, 68));

        board = new Board(this);

        ScrollView vertical = new ScrollView(this);
        vertical.setFillViewport(false);
        vertical.setClipToPadding(false);
        vertical.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(false);
        horizontal.setClipToPadding(false);
        horizontal.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        horizontal.addView(board, new HorizontalScrollView.LayoutParams(-2, -2));
        vertical.addView(horizontal, new ScrollView.LayoutParams(-1, -2));
        root.addView(vertical, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);

        modeButton.setOnClickListener(v -> {
            board.flagMode = !board.flagMode;
            modeButton.setText(board.flagMode ? "⚑" : "⛏");
        });
        easy.setOnClickListener(v -> board.newGame(9, 9, 10));
        medium.setOnClickListener(v -> board.newGame(16, 16, 40));
        hard.setOnClickListener(v -> board.newGame(30, 16, 99));
        restart.setOnClickListener(v -> board.newGame(board.w, board.h, board.mines));
        minus.setOnClickListener(v -> { board.setCellSize(board.cell - 4); updateZoom(); });
        plus.setOnClickListener(v -> { board.setCellSize(board.cell + 4); updateZoom(); });
        zoomButton.setOnClickListener(v -> { board.setCellSize(64); updateZoom(); });

        board.newGame(9, 9, 10);
    }

    void updateZoom() {
        zoomButton.setText(Math.round(board.cell * 100f / 64f) + "%");
    }

    Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(18);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setMinimumHeight(0);
        b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, 0);
        b.setAllCaps(false);
        return b;
    }

    LinearLayout.LayoutParams toolLp(int width) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, 58);
        p.setMargins(2, 1, 2, 1);
        return p;
    }

    class Board extends View {
        int w = 9, h = 9, mines = 10, cell = 64, flagCount = 0;
        boolean flagMode, lost, won;
        boolean[][] mine, open, marked;
        int[][] number;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random random = new Random();
        float downX, downY;
        boolean moved;

        Board(Context context) {
            super(context);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            setBackgroundColor(Color.rgb(128, 128, 128));
            setFocusable(true);
            setClickable(true);
        }

        void setCellSize(int value) {
            cell = Math.max(40, Math.min(96, value));
            requestLayout();
            invalidate();
        }

        void newGame(int W, int H, int M) {
            w = W;
            h = H;
            mines = M;
            flagCount = 0;
            lost = false;
            won = false;
            mine = new boolean[h][w];
            open = new boolean[h][w];
            marked = new boolean[h][w];
            number = new int[h][w];

            for (int k = 0; k < M;) {
                int x = random.nextInt(w);
                int y = random.nextInt(h);
                if (!mine[y][x]) {
                    mine[y][x] = true;
                    k++;
                }
            }

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int count = 0;
                    for (int yy = y - 1; yy <= y + 1; yy++) {
                        for (int xx = x - 1; xx <= x + 1; xx++) {
                            if (inside(xx, yy) && mine[yy][xx]) count++;
                        }
                    }
                    number[y][x] = count;
                }
            }

            requestLayout();
            invalidate();
        }

        boolean inside(int x, int y) {
            return x >= 0 && x < w && y >= 0 && y < h;
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(w * cell, h * cell);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            p.setTextAlign(Paint.Align.CENTER);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float l = x * cell;
                    float t = y * cell;
                    float r = l + cell;
                    float b = t + cell;

                    if (open[y][x]) drawOpenCell(c, l, t, r, b);
                    else drawClosedCell(c, l, t, r, b);

                    if (marked[y][x]) {
                        drawFlag(c, l, t);
                    } else if (open[y][x] && mine[y][x]) {
                        drawMine(c, l, t);
                    } else if (open[y][x] && number[y][x] > 0) {
                        drawNumber(c, l, t, number[y][x]);
                    }
                }
            }
        }

        void drawOpenCell(Canvas c, float l, float t, float r, float b) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(195, 195, 195));
            c.drawRect(l, t, r, b, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1);
            p.setColor(Color.rgb(125, 125, 125));
            c.drawRect(l, t, r, b, p);
        }

        void drawClosedCell(Canvas c, float l, float t, float r, float b) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(192, 192, 192));
            c.drawRect(l, t, r, b, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(3, cell / 16f));
            p.setColor(Color.WHITE);
            c.drawLine(l + 2, b - 2, l + 2, t + 2, p);
            c.drawLine(l + 2, t + 2, r - 2, t + 2, p);
            p.setColor(Color.rgb(128, 128, 128));
            c.drawLine(r - 2, t + 2, r - 2, b - 2, p);
            c.drawLine(r - 2, b - 2, l + 2, b - 2, p);
            p.setStyle(Paint.Style.FILL);
        }

        void drawNumber(Canvas c, float l, float t, int n) {
            int[] colors = {
                    Color.TRANSPARENT,
                    Color.BLUE,
                    Color.rgb(0, 128, 0),
                    Color.RED,
                    Color.rgb(0, 0, 128),
                    Color.rgb(128, 0, 0),
                    Color.rgb(0, 128, 128),
                    Color.BLACK,
                    Color.GRAY
            };
            p.setStyle(Paint.Style.FILL);
            p.setColor(colors[Math.min(n, 8)]);
            p.setTextSize(cell * 0.58f);
            c.drawText(String.valueOf(n), l + cell / 2f, t + cell * 0.72f, p);
        }

        void drawFlag(Canvas c, float l, float t) {
            float cx = l + cell * .47f;
            float base = t + cell * .78f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(3, cell * .055f));
            p.setColor(Color.BLACK);
            c.drawLine(cx, t + cell * .20f, cx, base, p);
            c.drawLine(l + cell * .25f, base, l + cell * .72f, base, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.RED);
            Path flag = new Path();
            flag.moveTo(cx, t + cell * .20f);
            flag.lineTo(l + cell * .78f, t + cell * .36f);
            flag.lineTo(cx, t + cell * .54f);
            flag.close();
            c.drawPath(flag, p);
        }

        void drawMine(Canvas c, float l, float t) {
            float cx = l + cell / 2f;
            float cy = t + cell / 2f;
            float rad = cell * .22f;

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(3, cell * .045f));
            p.setColor(Color.BLACK);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                c.drawLine(
                        cx + (float) Math.cos(a) * rad * .55f,
                        cy + (float) Math.sin(a) * rad * .55f,
                        cx + (float) Math.cos(a) * rad * 1.45f,
                        cy + (float) Math.sin(a) * rad * 1.45f,
                        p
                );
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.RED);
            c.drawCircle(cx, cy, rad, p);
            p.setColor(Color.WHITE);
            c.drawCircle(cx - rad * .35f, cy - rad * .35f, rad * .22f, p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    moved = false;
                    // Do not ask the parent to stop intercepting: ScrollView must be
                    // allowed to take over when the finger starts dragging the board.
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getX() - downX) > 12 || Math.abs(e.getY() - downY) > 12) {
                        moved = true;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (moved || lost || won) return true;

                    int x = (int) (e.getX() / cell);
                    int y = (int) (e.getY() / cell);
                    if (!inside(x, y)) return true;

                    // A short tap on an already-open number ALWAYS chords.
                    // Flag mode only changes what tapping a closed cell does.
                    if (open[y][x] && number[y][x] > 0) {
                        chord(x, y);
                    } else if (flagMode) {
                        toggleFlag(x, y);
                    } else if (!open[y][x]) {
                        reveal(x, y);
                    }

                    checkWin();
                    invalidate();
                    return true;
            }
            return true;
        }

        void toggleFlag(int x, int y) {
            if (open[y][x] || lost || won) return;
            if (marked[y][x]) {
                marked[y][x] = false;
                flagCount--;
            } else if (flagCount < mines) {
                marked[y][x] = true;
                flagCount++;
            }
        }

        void reveal(int x, int y) {
            if (lost || won || !inside(x, y) || open[y][x] || marked[y][x]) return;

            open[y][x] = true;
            if (mine[y][x]) {
                lost = true;
                // Reveal all mines only after losing; no further actions are accepted.
                for (int yy = 0; yy < h; yy++) {
                    for (int xx = 0; xx < w; xx++) {
                        if (mine[yy][xx]) open[yy][xx] = true;
                    }
                }
                return;
            }

            if (number[y][x] == 0) {
                for (int yy = y - 1; yy <= y + 1; yy++) {
                    for (int xx = x - 1; xx <= x + 1; xx++) {
                        if (inside(xx, yy) && (xx != x || yy != y)) reveal(xx, yy);
                    }
                }
            }
        }

        void chord(int x, int y) {
            if (lost || won || !open[y][x] || number[y][x] == 0) return;

            int flags = 0;
            for (int yy = y - 1; yy <= y + 1; yy++) {
                for (int xx = x - 1; xx <= x + 1; xx++) {
                    if (inside(xx, yy) && marked[yy][xx]) flags++;
                }
            }

            // Standard Minesweeper chord: reveal neighbours only when the
            // number of adjacent flags equals the number on the cell.
            if (flags != number[y][x]) return;

            for (int yy = y - 1; yy <= y + 1; yy++) {
                for (int xx = x - 1; xx <= x + 1; xx++) {
                    if (inside(xx, yy) && !marked[yy][xx] && !open[yy][xx]) {
                        reveal(xx, yy);
                        if (lost) return;
                    }
                }
            }
        }

        void checkWin() {
            if (lost || won) return;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (!mine[y][x] && !open[y][x]) return;
                }
            }
            won = true;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (mine[y][x] && !marked[y][x]) {
                        marked[y][x] = true;
                        flagCount++;
                    }
                }
            }
        }
    }
}

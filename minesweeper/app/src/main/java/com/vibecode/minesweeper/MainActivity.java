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
    Button mode, size, zoom;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(192, 192, 192));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(192, 192, 192));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(4, 4, 4, 4);

        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.CENTER);
        mode = button("⚑");
        Button easy = button("9×9");
        Button medium = button("16×16");
        Button hard = button("30×16");
        row1.addView(mode, lp(0, 1));
        row1.addView(easy, lp(0, 1));
        row1.addView(medium, lp(0, 1));
        row1.addView(hard, lp(0, 1));

        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER);
        Button restart = button("↻");
        Button minus = button("−");
        zoom = button("100%");
        Button plus = button("+");
        size = button("10");
        row2.addView(restart, lp(0, 1));
        row2.addView(minus, lp(0, 1));
        row2.addView(zoom, lp(0, 1));
        row2.addView(plus, lp(0, 1));
        row2.addView(size, lp(0, 1));

        top.addView(row1, new LinearLayout.LayoutParams(-1, 50));
        top.addView(row2, new LinearLayout.LayoutParams(-1, 50));
        root.addView(top, new LinearLayout.LayoutParams(-1, 104));

        board = new Board(this);
        ScrollView vertical = new ScrollView(this);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(false);
        horizontal.addView(board);
        vertical.addView(horizontal);
        root.addView(vertical, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        mode.setOnClickListener(v -> {
            board.flagMode = !board.flagMode;
            mode.setText(board.flagMode ? "⚑" : "⛏");
            board.invalidate();
        });
        easy.setOnClickListener(v -> board.newGame(9, 9, 10));
        medium.setOnClickListener(v -> board.newGame(16, 16, 40));
        hard.setOnClickListener(v -> board.newGame(30, 16, 99));
        restart.setOnClickListener(v -> board.newGame(board.w, board.h, board.mines));
        minus.setOnClickListener(v -> { board.setCellSize(board.cell - 4); updateZoom(); });
        plus.setOnClickListener(v -> { board.setCellSize(board.cell + 4); updateZoom(); });
        zoom.setOnClickListener(v -> { board.setCellSize(52); updateZoom(); });

        board.newGame(9, 9, 10);
    }

    void updateZoom() {
        zoom.setText(Math.round(board.cell * 100f / 52f) + "%");
    }

    Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(17);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    LinearLayout.LayoutParams lp(int width, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, -1, weight);
        p.setMargins(2, 2, 2, 2);
        return p;
    }

    class Board extends View {
        int w = 9, h = 9, mines = 10, cell = 52, flagCount = 0;
        boolean flagMode, lost, won;
        boolean[][] mine, open, marked;
        int[][] number;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random random = new Random();
        float downX, downY;

        Board(Context context) {
            super(context);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            setBackgroundColor(Color.rgb(128, 128, 128));
            setFocusable(true);
        }

        void setCellSize(int value) {
            cell = Math.max(36, Math.min(84, value));
            requestLayout();
            invalidate();
        }

        void newGame(int W, int H, int M) {
            w = W; h = H; mines = M; flagCount = 0; lost = false; won = false;
            mine = new boolean[h][w];
            open = new boolean[h][w];
            marked = new boolean[h][w];
            number = new int[h][w];

            for (int k = 0; k < M;) {
                int x = random.nextInt(w), y = random.nextInt(h);
                if (!mine[y][x]) { mine[y][x] = true; k++; }
            }
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int count = 0;
                for (int yy = y - 1; yy <= y + 1; yy++) for (int xx = x - 1; xx <= x + 1; xx++)
                    if (inside(xx, yy) && mine[yy][xx]) count++;
                number[y][x] = count;
            }
            size.setText(String.valueOf(mines - flagCount));
            requestLayout();
            invalidate();
        }

        boolean inside(int x, int y) { return x >= 0 && x < w && y >= 0 && y < h; }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(w * cell, h * cell);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            p.setTextAlign(Paint.Align.CENTER);

            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                float l = x * cell, t = y * cell, r = l + cell, b = t + cell;

                if (open[y][x]) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.rgb(195, 195, 195));
                    c.drawRect(l, t, r, b, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1);
                    p.setColor(Color.rgb(150, 150, 150));
                    c.drawRect(l, t, r, b, p);
                } else {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.rgb(210, 210, 210));
                    c.drawRect(l, t, r, b, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(2, cell / 16f));
                    p.setColor(Color.WHITE);
                    c.drawLine(l + 1, b - 1, l + 1, t + 1, p);
                    c.drawLine(l + 1, t + 1, r - 1, t + 1, p);
                    p.setColor(Color.rgb(120, 120, 120));
                    c.drawLine(r - 1, t + 1, r - 1, b - 1, p);
                    c.drawLine(r - 1, b - 1, l + 1, b - 1, p);
                    p.setStyle(Paint.Style.FILL);
                }

                if (marked[y][x]) {
                    drawFlag(c, l, t);
                } else if (open[y][x] && mine[y][x]) {
                    drawMine(c, l, t);
                } else if (open[y][x] && number[y][x] > 0) {
                    int[] colors = {Color.TRANSPARENT, Color.BLUE, Color.rgb(0, 128, 0), Color.RED,
                            Color.rgb(0, 0, 128), Color.rgb(128, 0, 0), Color.CYAN, Color.BLACK, Color.GRAY};
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(colors[number[y][x]]);
                    p.setTextSize(cell * 0.58f);
                    c.drawText(String.valueOf(number[y][x]), l + cell / 2f, t + cell * 0.72f, p);
                }
            }
        }

        void drawFlag(Canvas c, float l, float t) {
            float cx = l + cell * .48f;
            float base = t + cell * .76f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2, cell * .055f));
            p.setColor(Color.BLACK);
            c.drawLine(cx, t + cell * .22f, cx, base, p);
            c.drawLine(l + cell * .28f, base, l + cell * .70f, base, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.RED);
            Path flag = new Path();
            flag.moveTo(cx, t + cell * .22f);
            flag.lineTo(l + cell * .78f, t + cell * .38f);
            flag.lineTo(cx, t + cell * .55f);
            flag.close();
            c.drawPath(flag, p);
        }

        void drawMine(Canvas c, float l, float t) {
            float cx = l + cell / 2f, cy = t + cell / 2f, rad = cell * .23f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2, cell * .045f));
            p.setColor(Color.BLACK);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                c.drawLine(cx + (float)Math.cos(a) * rad * .55f, cy + (float)Math.sin(a) * rad * .55f,
                        cx + (float)Math.cos(a) * rad * 1.45f, cy + (float)Math.sin(a) * rad * 1.45f, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.RED);
            c.drawCircle(cx, cy, rad, p);
            p.setColor(Color.WHITE);
            c.drawCircle(cx - rad * .35f, cy - rad * .35f, rad * .22f, p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                return true;
            }
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            if (lost || won) return true;
            if (Math.abs(e.getX() - downX) > 20 || Math.abs(e.getY() - downY) > 20) return true;

            int x = (int)(e.getX() / cell), y = (int)(e.getY() / cell);
            if (!inside(x, y)) return true;

            if (flagMode) {
                toggleFlag(x, y);
            } else if (open[y][x] && number[y][x] > 0) {
                chord(x, y);
            } else if (!open[y][x]) {
                reveal(x, y);
            }
            checkWin();
            size.setText(String.valueOf(Math.max(0, mines - flagCount)));
            invalidate();
            return true;
        }

        void toggleFlag(int x, int y) {
            if (open[y][x]) return;
            if (marked[y][x]) {
                marked[y][x] = false;
                flagCount--;
            } else if (flagCount < mines) {
                marked[y][x] = true;
                flagCount++;
            }
        }

        void reveal(int x, int y) {
            if (lost || won || open[y][x] || marked[y][x]) return;
            open[y][x] = true;
            if (mine[y][x]) {
                lost = true;
                for (int yy = 0; yy < h; yy++) for (int xx = 0; xx < w; xx++)
                    if (mine[yy][xx]) open[yy][xx] = true;
                return;
            }
            if (number[y][x] == 0) {
                for (int yy = y - 1; yy <= y + 1; yy++) for (int xx = x - 1; xx <= x + 1; xx++)
                    if (inside(xx, yy) && (xx != x || yy != y)) reveal(xx, yy);
            }
        }

        void chord(int x, int y) {
            if (!open[y][x] || number[y][x] == 0) return;
            int flags = 0;
            for (int yy = y - 1; yy <= y + 1; yy++) for (int xx = x - 1; xx <= x + 1; xx++)
                if (inside(xx, yy) && marked[yy][xx]) flags++;
            if (flags != number[y][x]) return;

            for (int yy = y - 1; yy <= y + 1; yy++) for (int xx = x - 1; xx <= x + 1; xx++)
                if (inside(xx, yy) && !marked[yy][xx] && !open[yy][xx]) reveal(xx, yy);
        }

        void checkWin() {
            if (lost) return;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                if (!mine[y][x] && !open[y][x]) return;
            won = true;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                if (mine[y][x] && !marked[y][x]) {
                    marked[y][x] = true;
                    flagCount++;
                }
        }
    }
}

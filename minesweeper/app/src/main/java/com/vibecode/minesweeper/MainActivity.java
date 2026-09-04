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
    TextView status;
    Button modeButton;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(35,35,35));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(10, 10, 10, 10);
        root.setBackgroundColor(Color.rgb(245,245,245));

        TextView title = new TextView(this);
        title.setText("САПЕР");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(30,30,30));
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, 52));

        status = new TextView(this);
        status.setTextSize(16);
        status.setTextColor(Color.rgb(60,60,60));
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, 42));

        HorizontalScrollView controlsScroll = new HorizontalScrollView(this);
        controlsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        modeButton = button("ЛОПАТА");
        Button easy = button("ЛЕГКО");
        Button medium = button("СЕРЕДНЬО");
        Button hard = button("СКЛАДНО");
        Button restart = button("НОВА");
        Button minus = button("−");
        Button zoom = button("100%");
        Button plus = button("+");

        controls.addView(modeButton, lp(118));
        controls.addView(easy, lp(90));
        controls.addView(medium, lp(112));
        controls.addView(hard, lp(100));
        controls.addView(restart, lp(80));
        controls.addView(minus, lp(54));
        controls.addView(zoom, lp(66));
        controls.addView(plus, lp(54));
        controlsScroll.addView(controls);
        root.addView(controlsScroll, new LinearLayout.LayoutParams(-1, 58));

        board = new Board(this);
        ScrollView vertical = new ScrollView(this);
        vertical.setFillViewport(false);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(false);
        horizontal.addView(board);
        vertical.addView(horizontal);
        root.addView(vertical, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);

        modeButton.setOnClickListener(v -> {
            board.flagMode = !board.flagMode;
            modeButton.setText(board.flagMode ? "ПРАПОР" : "ЛОПАТА");
            board.invalidate();
        });
        easy.setOnClickListener(v -> board.newGame(9, 9, 10));
        medium.setOnClickListener(v -> board.newGame(16, 16, 40));
        hard.setOnClickListener(v -> board.newGame(30, 16, 99));
        restart.setOnClickListener(v -> board.newGame(board.w, board.h, board.mines));
        minus.setOnClickListener(v -> { board.setCellSize(board.cell - 4); zoom.setText(board.cell + "%"); });
        plus.setOnClickListener(v -> { board.setCellSize(board.cell + 4); zoom.setText(board.cell + "%"); });
        zoom.setOnClickListener(v -> { board.setCellSize(42); zoom.setText("100%"); });

        board.newGame(9, 9, 10);
    }

    Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        return b;
    }

    LinearLayout.LayoutParams lp(int width) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, 48);
        p.setMargins(3, 0, 3, 0);
        return p;
    }

    void updateStatus() {
        if (board.lost) status.setText("МІНА. Гру завершено — натисни НОВА.");
        else if (board.won) status.setText("ПЕРЕМОГА! Усі міни знайдені.");
        else status.setText("Міни: " + board.mines + "   Прапорів: " + board.flagCount + "   Торкання цифри = АКОРД");
    }

    class Board extends View {
        int w = 9, h = 9, mines = 10, cell = 42, flagCount = 0;
        boolean flagMode, lost, won;
        boolean[][] mine, open, marked;
        int[][] number;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random random = new Random();

        Board(Context context) {
            super(context);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            setBackgroundColor(Color.rgb(150,150,150));
            setFocusable(true);
        }

        void setCellSize(int value) {
            cell = Math.max(24, Math.min(72, value));
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
                for (int yy = y-1; yy <= y+1; yy++) for (int xx = x-1; xx <= x+1; xx++)
                    if (yy >= 0 && yy < h && xx >= 0 && xx < w && mine[yy][xx]) count++;
                number[y][x] = count;
            }
            requestLayout(); invalidate(); updateStatus();
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(w * cell, h * cell);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(cell * 0.54f);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                float left = x * cell, top = y * cell;
                p.setStyle(Paint.Style.FILL);
                p.setColor(open[y][x] ? Color.rgb(218,218,218) : Color.rgb(190,190,190));
                c.drawRect(left, top, left + cell, top + cell, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1);
                p.setColor(Color.rgb(125,125,125));
                c.drawRect(left, top, left + cell, top + cell, p);
                p.setStyle(Paint.Style.FILL);

                if (marked[y][x]) {
                    p.setColor(Color.RED);
                    p.setTextSize(cell * 0.58f);
                    c.drawText("F", left + cell/2f, top + cell*0.70f, p);
                } else if (open[y][x] && mine[y][x]) {
                    p.setColor(Color.BLACK);
                    c.drawCircle(left + cell/2f, top + cell/2f, cell*0.22f, p);
                } else if (open[y][x] && number[y][x] > 0) {
                    int[] colors = {Color.TRANSPARENT, Color.BLUE, Color.rgb(0,120,0), Color.RED,
                            Color.rgb(0,0,130), Color.rgb(128,0,0), Color.CYAN, Color.BLACK, Color.GRAY};
                    p.setColor(colors[number[y][x]]);
                    p.setTextSize(cell * 0.54f);
                    c.drawText(String.valueOf(number[y][x]), left + cell/2f, top + cell*0.70f, p);
                }
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            if (lost || won) return true;
            int x = (int)(e.getX() / cell), y = (int)(e.getY() / cell);
            if (x < 0 || x >= w || y < 0 || y >= h) return true;

            if (flagMode) toggleFlag(x, y);
            else if (open[y][x] && number[y][x] > 0) chord(x, y);
            else if (!open[y][x]) reveal(x, y);

            checkWin();
            invalidate(); updateStatus();
            return true;
        }

        void toggleFlag(int x, int y) {
            if (open[y][x]) return;
            if (marked[y][x]) { marked[y][x] = false; flagCount--; }
            else if (flagCount < mines) { marked[y][x] = true; flagCount++; }
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
                for (int yy = y-1; yy <= y+1; yy++) for (int xx = x-1; xx <= x+1; xx++)
                    if (yy >= 0 && yy < h && xx >= 0 && xx < w && (xx != x || yy != y)) reveal(xx, yy);
            }
        }

        void chord(int x, int y) {
            if (lost || won || !open[y][x] || number[y][x] == 0) return;
            int flags = 0;
            for (int yy = y-1; yy <= y+1; yy++) for (int xx = x-1; xx <= x+1; xx++)
                if (yy >= 0 && yy < h && xx >= 0 && xx < w && marked[yy][xx]) flags++;
            if (flags != number[y][x]) return;
            for (int yy = y-1; yy <= y+1; yy++) for (int xx = x-1; xx <= x+1; xx++)
                if (yy >= 0 && yy < h && xx >= 0 && xx < w && !marked[yy][xx]) reveal(xx, yy);
        }

        void checkWin() {
            if (lost) return;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                if (!mine[y][x] && !open[y][x]) return;
            won = true;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                if (mine[y][x] && !marked[y][x]) { marked[y][x] = true; flagCount++; }
        }
    }
}

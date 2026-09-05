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
    HorizontalScrollView horizontal;
    ScrollView vertical;
    TextView zoomText, mineCounter;
    Button smileButton;
    static final int BASE_CELL = 128;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(192, 192, 192));
        getWindow().setNavigationBarColor(Color.rgb(192, 192, 192));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(192, 192, 192));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(5, 5, 5, 5);

        HorizontalScrollView toolbarScroll = new HorizontalScrollView(this);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        toolbarScroll.setFillViewport(true);
        toolbarScroll.addView(toolbar, new HorizontalScrollView.LayoutParams(-2, -1));

        Button mode = button("⚑");
        Button easy = button("9×9");
        Button medium = button("16×16");
        Button hard = button("30×16");
        mineCounter = counter();
        smileButton = button("🙂");
        zoomText = new TextView(this);
        zoomText.setTextSize(22);
        zoomText.setTextColor(Color.BLACK);
        zoomText.setGravity(Gravity.CENTER);

        toolbar.addView(mode, toolLp(162));
        toolbar.addView(easy, toolLp(174));
        toolbar.addView(medium, toolLp(198));
        toolbar.addView(hard, toolLp(198));
        toolbar.addView(mineCounter, toolLp(162));
        toolbar.addView(smileButton, toolLp(162));
        toolbar.addView(zoomText, toolLp(162));
        root.addView(toolbarScroll, new LinearLayout.LayoutParams(-1, 142));

        board = new Board(this);
        vertical = new ScrollView(this);
        vertical.setFillViewport(false);
        vertical.setClipToPadding(false);
        vertical.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(false);
        horizontal.setClipToPadding(false);
        horizontal.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        horizontal.addView(board, new HorizontalScrollView.LayoutParams(-2, -2));
        vertical.addView(horizontal, new ScrollView.LayoutParams(-1, -2));
        root.addView(vertical, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        mode.setOnClickListener(v -> {
            board.flagMode = !board.flagMode;
            mode.setText(board.flagMode ? "⚑" : "⛏");
        });
        easy.setOnClickListener(v -> board.newGame(9, 9, 10));
        medium.setOnClickListener(v -> board.newGame(16, 16, 40));
        hard.setOnClickListener(v -> board.newGame(30, 16, 99));
        smileButton.setOnClickListener(v -> board.newGame(board.w, board.h, board.mines));
        board.newGame(9, 9, 10);
    }

    void updateZoomText() {
        zoomText.setText(Math.round(board.cell * 100f / BASE_CELL) + "%");
    }

    void updateMineCounter() {
        mineCounter.setText("💣 " + Math.max(0, board.mines - board.flagCount));
    }

    void updateSmiley() {
        if (board.lost) smileButton.setText("😵");
        else if (board.won) smileButton.setText("😎");
        else smileButton.setText("🙂");
    }

    Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(25);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setMinimumHeight(0);
        b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, 0);
        b.setAllCaps(false);
        return b;
    }

    TextView counter() {
        TextView t = new TextView(this);
        t.setTextSize(24);
        t.setTextColor(Color.BLACK);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(Color.rgb(160, 160, 160));
        return t;
    }

    LinearLayout.LayoutParams toolLp(int width) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, 132);
        p.setMargins(4, 2, 4, 2);
        return p;
    }

    class Board extends View {
        int w = 9, h = 9, mines = 10, cell = BASE_CELL, flagCount;
        boolean flagMode, lost, won, minesGenerated;
        boolean[][] mine, open, marked;
        int[][] number;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Random random = new Random();
        ScaleGestureDetector scaleDetector;
        float downX, downY;
        boolean moved, scaling;
        final float MIN_CELL = 64f, MAX_CELL = 256f, TOUCH_SLOP = 18f;

        Board(Context context) {
            super(context);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            setBackgroundColor(Color.rgb(128, 128, 128));
            setFocusable(true);
            setClickable(true);
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScaleBegin(ScaleGestureDetector d) {
                    scaling = true;
                    moved = true;
                    requestDisallowParentIntercept(true);
                    return true;
                }
                @Override public boolean onScale(ScaleGestureDetector d) {
                    float oldCell = cell;
                    float newCell = Math.max(MIN_CELL, Math.min(MAX_CELL, oldCell * d.getScaleFactor()));
                    if (Math.abs(newCell - oldCell) < 0.5f) return true;
                    float fx = d.getFocusX(), fy = d.getFocusY();
                    final float contentX = horizontal.getScrollX() + fx;
                    final float contentY = vertical.getScrollY() + fy;
                    final float ratio = newCell / oldCell;
                    cell = Math.round(newCell);
                    requestLayout();
                    invalidate();
                    updateZoomText();
                    post(() -> {
                        horizontal.scrollTo(Math.max(0, Math.round(contentX * ratio - fx)), horizontal.getScrollY());
                        vertical.scrollTo(vertical.getScrollX(), Math.max(0, Math.round(contentY * ratio - fy)));
                    });
                    return true;
                }
                @Override public void onScaleEnd(ScaleGestureDetector d) {
                    scaling = false;
                    requestDisallowParentIntercept(false);
                }
            });
        }

        void requestDisallowParentIntercept(boolean disallow) {
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(disallow);
        }

        void newGame(int W, int H, int M) {
            w = W; h = H; mines = M; flagCount = 0;
            lost = false; won = false; minesGenerated = false;
            mine = new boolean[h][w];
            open = new boolean[h][w];
            marked = new boolean[h][w];
            number = new int[h][w];
            requestLayout();
            invalidate();
            updateMineCounter();
            updateSmiley();
            updateZoomText();
        }

        void generateMines(int safeX, int safeY) {
            int generated = 0;
            while (generated < mines) {
                int x = random.nextInt(w), y = random.nextInt(h);
                if ((x == safeX && y == safeY) || mine[y][x]) continue;
                mine[y][x] = true;
                generated++;
            }
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int count = 0;
                for (int yy = y - 1; yy <= y + 1; yy++)
                    for (int xx = x - 1; xx <= x + 1; xx++)
                        if (inside(xx, yy) && mine[yy][xx]) count++;
                number[y][x] = count;
            }
            minesGenerated = true;
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
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                float l = x * cell, t = y * cell, r = l + cell, b = t + cell;
                if (open[y][x]) drawOpenCell(c, l, t, r, b);
                else drawClosedCell(c, l, t, r, b);
                if (marked[y][x]) drawFlag(c, l, t);
                else if (open[y][x] && mine[y][x]) drawMine(c, l, t);
                else if (open[y][x] && number[y][x] > 0) drawNumber(c, l, t, number[y][x]);
            }
        }

        void drawOpenCell(Canvas c, float l, float t, float r, float b) {
            p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(195, 195, 195)); c.drawRect(l, t, r, b, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1); p.setColor(Color.rgb(125, 125, 125)); c.drawRect(l, t, r, b, p);
        }

        void drawClosedCell(Canvas c, float l, float t, float r, float b) {
            p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(192, 192, 192)); c.drawRect(l, t, r, b, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(3, cell / 16f)); p.setColor(Color.WHITE);
            c.drawLine(l + 2, b - 2, l + 2, t + 2, p); c.drawLine(l + 2, t + 2, r - 2, t + 2, p);
            p.setColor(Color.rgb(128, 128, 128)); c.drawLine(r - 2, t + 2, r - 2, b - 2, p); c.drawLine(r - 2, b - 2, l + 2, b - 2, p);
            p.setStyle(Paint.Style.FILL);
        }

        void drawNumber(Canvas c, float l, float t, int n) {
            int[] colors = {Color.TRANSPARENT, Color.BLUE, Color.rgb(0,128,0), Color.RED, Color.rgb(0,0,128), Color.rgb(128,0,0), Color.rgb(0,128,128), Color.BLACK, Color.GRAY};
            p.setStyle(Paint.Style.FILL); p.setColor(colors[Math.min(n, 8)]); p.setTextSize(cell * .58f);
            c.drawText(String.valueOf(n), l + cell / 2f, t + cell * .72f, p);
        }

        void drawFlag(Canvas c, float l, float t) {
            float cx = l + cell * .47f, base = t + cell * .78f;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(3, cell * .055f)); p.setColor(Color.BLACK);
            c.drawLine(cx, t + cell * .20f, cx, base, p); c.drawLine(l + cell * .25f, base, l + cell * .72f, base, p);
            p.setStyle(Paint.Style.FILL); p.setColor(Color.RED);
            Path flag = new Path(); flag.moveTo(cx, t + cell * .20f); flag.lineTo(l + cell * .78f, t + cell * .36f); flag.lineTo(cx, t + cell * .54f); flag.close(); c.drawPath(flag, p);
        }

        void drawMine(Canvas c, float l, float t) {
            float cx = l + cell / 2f, cy = t + cell / 2f, rad = cell * .22f;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(3, cell * .045f)); p.setColor(Color.BLACK);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                c.drawLine(cx + (float)Math.cos(a) * rad * .55f, cy + (float)Math.sin(a) * rad * .55f,
                        cx + (float)Math.cos(a) * rad * 1.45f, cy + (float)Math.sin(a) * rad * 1.45f, p);
            }
            p.setStyle(Paint.Style.FILL); p.setColor(Color.RED); c.drawCircle(cx, cy, rad, p);
            p.setColor(Color.WHITE); c.drawCircle(cx - rad * .35f, cy - rad * .35f, rad * .22f, p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            scaleDetector.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX(); downY = e.getY(); moved = false; scaling = false; return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    moved = true; requestDisallowParentIntercept(true); return true;
                case MotionEvent.ACTION_MOVE:
                    if (e.getPointerCount() > 1 || scaling) { moved = true; return true; }
                    if (Math.abs(e.getX() - downX) > TOUCH_SLOP || Math.abs(e.getY() - downY) > TOUCH_SLOP) moved = true;
                    return true;
                case MotionEvent.ACTION_POINTER_UP: return true;
                case MotionEvent.ACTION_CANCEL:
                    requestDisallowParentIntercept(false); scaling = false; return true;
                case MotionEvent.ACTION_UP:
                    requestDisallowParentIntercept(false);
                    if (moved || scaling || lost || won) return true;
                    int x = (int)(e.getX() / cell), y = (int)(e.getY() / cell);
                    if (!inside(x, y)) return true;
                    if (open[y][x] && number[y][x] > 0) chord(x, y);
                    else if (flagMode) toggleFlag(x, y);
                    else if (!open[y][x]) {
                        if (!minesGenerated) generateMines(x, y);
                        reveal(x, y);
                    }
                    checkWin(); updateMineCounter(); updateSmiley(); invalidate(); performClick(); return true;
            }
            return true;
        }

        @Override public boolean performClick() { super.performClick(); return true; }

        void toggleFlag(int x, int y) {
            if (open[y][x]) return;
            if (marked[y][x]) { marked[y][x] = false; flagCount--; }
            else if (flagCount < mines) { marked[y][x] = true; flagCount++; }
        }

        void reveal(int x, int y) {
            if (lost || won || !inside(x, y) || open[y][x] || marked[y][x]) return;
            open[y][x] = true;
            if (mine[y][x]) { lose(); return; }
            if (number[y][x] != 0) return;
            for (int yy = y - 1; yy <= y + 1; yy++)
                for (int xx = x - 1; xx <= x + 1; xx++)
                    if (inside(xx, yy) && (xx != x || yy != y)) reveal(xx, yy);
        }

        void chord(int x, int y) {
            int flags = 0;
            ArrayList<int[]> covered = new ArrayList<>();
            for (int yy = y - 1; yy <= y + 1; yy++) for (int xx = x - 1; xx <= x + 1; xx++) {
                if (!inside(xx, yy) || (xx == x && yy == y)) continue;
                if (marked[yy][xx]) flags++;
                else if (!open[yy][xx]) covered.add(new int[]{xx, yy});
            }
            int needed = number[y][x] - flags;
            if (needed < 0) return;
            if (needed == covered.size() && needed > 0) {
                for (int[] q : covered) { marked[q[1]][q[0]] = true; flagCount++; }
                return;
            }
            if (flags != number[y][x]) return;
            for (int[] q : covered) { reveal(q[0], q[1]); if (lost) return; }
        }

        void lose() {
            lost = true;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (mine[y][x]) open[y][x] = true;
        }

        void checkWin() {
            if (lost) return;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (!mine[y][x] && !open[y][x]) return;
            won = true;
        }
    }
}

package com.amigos.kinbridge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Cognitive Wellness Trend chart (ui_spec.md §3): solid EWMA line in signal
 * violet, daily CRI as ink dots, faint dashed 40-threshold. No red zones.
 */
public class TrendChartView extends View {

    private final List<Double> cri = new ArrayList<>();
    private final List<Double> ewma = new ArrayList<>();

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TrendChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        linePaint.setColor(0xFF5F79FF);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(5f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint.setColor(0xFF000000);
        dotPaint.setStyle(Paint.Style.FILL);

        thresholdPaint.setColor(0xFFA6A6A6);
        thresholdPaint.setStyle(Paint.Style.STROKE);
        thresholdPaint.setStrokeWidth(2f);
        thresholdPaint.setPathEffect(new DashPathEffect(new float[]{12f, 10f}, 0));
    }

    public void setData(List<Double> criPoints, List<Double> ewmaPoints) {
        cri.clear();
        ewma.clear();
        cri.addAll(criPoints);
        ewma.addAll(ewmaPoints);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float pad = 24f;
        float usableW = w - 2 * pad;
        float usableH = h - 2 * pad;

        // 40-threshold dashed line
        float thresholdY = pad + usableH * (1 - 40f / 100f);
        canvas.drawLine(pad, thresholdY, w - pad, thresholdY, thresholdPaint);

        int n = Math.max(cri.size(), ewma.size());
        if (n < 2) {
            return;
        }

        Path path = new Path();
        for (int i = 0; i < ewma.size(); i++) {
            float x = pad + usableW * i / (n - 1);
            float y = pad + usableH * (1 - (float) (Math.max(0, Math.min(100, ewma.get(i))) / 100f));
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, linePaint);

        for (int i = 0; i < cri.size(); i++) {
            float x = pad + usableW * i / (n - 1);
            float y = pad + usableH * (1 - (float) (Math.max(0, Math.min(100, cri.get(i))) / 100f));
            canvas.drawCircle(x, y, 5f, dotPaint);
        }
    }
}

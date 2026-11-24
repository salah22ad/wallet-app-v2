package com.hpp.daftree.budget.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import com.hpp.daftree.R;

@SuppressLint("ViewConstructor")
public class BudgetPieMarkerView extends MarkerView {

    private final TextView tvCategory;
    private final TextView tvValue;
    private String currencyLabel = "";
    private double totalExpense = 0.0;

    public BudgetPieMarkerView(Context context) {
        super(context, R.layout.budget_marker_pie);
        tvCategory = findViewById(R.id.tvMarkerCategory);
        tvValue = findViewById(R.id.tvMarkerValue);
    }

    public void setMeta(String currencyLabel, double totalExpense) {
        this.currencyLabel = currencyLabel != null ? currencyLabel : "";
        this.totalExpense = totalExpense;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        String label = "";
        if (e instanceof PieEntry) {
            PieEntry pe = (PieEntry) e;
            label = pe.getLabel() != null ? pe.getLabel() : "";
        }
        double val = e.getY();
        double pct = totalExpense > 0 ? (val / totalExpense) * 100.0 : 0.0;
        tvCategory.setText(label);
        tvValue.setText(BudgetUtils.formatAmountWithCurrency(val, currencyLabel)
                + " (" + String.format(java.util.Locale.US, "%.1f%%", pct) + ")");
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 12f);
    }
}

package com.hpp.daftree.budget.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.hpp.daftree.R;
import com.hpp.daftree.helpers.AppFontHelper;
import com.hpp.daftree.budget.adapter.BudgetEntryAdapter;
import com.hpp.daftree.budget.adapter.CategoryDistributionAdapter;
import com.hpp.daftree.budget.data.BudgetEntry;
import com.hpp.daftree.budget.data.BudgetEntryWithCategory;
import com.hpp.daftree.budget.data.CategoryExpenseTotal;
import com.hpp.daftree.budget.data.MonthSummaryStats;
import com.hpp.daftree.budget.data.MonthlyTotals;
import com.hpp.daftree.budget.util.BudgetPieMarkerView;
import com.hpp.daftree.budget.util.BudgetUtils;
import com.hpp.daftree.budget.viewmodel.BudgetViewModel;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.databinding.FragmentBudgetMainBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BudgetContainerFragment extends Fragment {

    public interface BudgetHostListener {
        void onBudgetOpenDrawer();
        void onBudgetRequestAddEntry(String entryType);
        void onBudgetRequestEditEntry(BudgetEntryWithCategory entry);
        void onBudgetRequestEditLimit(double currentLimit);
        void onBudgetCurrencyChanged(Currency currency);
    }

    private FragmentBudgetMainBinding binding;
    private BudgetViewModel viewModel;
    private BudgetHostListener hostListener;

    private CategoryDistributionAdapter distributionAdapter;
    private BudgetEntryAdapter entryAdapter;
    private BudgetPieMarkerView pieMarkerView;
    private Typeface cairoTypeface;

    private final List<Currency> availableCurrencies = new ArrayList<>();
    private final List<BudgetEntryWithCategory> latestMonthEntries = new ArrayList<>();
    private List<CategoryExpenseTotal> latestDistribution = new ArrayList<>();
    private List<MonthlyTotals> latestComparison = new ArrayList<>();
    private MonthSummaryStats latestSummary;
    private Double latestLimit;
    private boolean showingSummaryTab = true;
    private boolean currencyInitialized = false;
    private boolean summaryLoaded = false;
    private boolean limitLoaded = false;
    private boolean limitTourScheduled = false;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof BudgetHostListener) {
            hostListener = (BudgetHostListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        hostListener = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBudgetMainBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(BudgetViewModel.class);
        cairoTypeface = AppFontHelper.getAppTypeface(requireContext());

        setupRecyclerViews();
        setupCharts();
        setupClickListeners();
        observeData();
    }

    private void setupRecyclerViews() {
        distributionAdapter = new CategoryDistributionAdapter();
        binding.rvCategoryDistribution.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategoryDistribution.setAdapter(distributionAdapter);

        entryAdapter = new BudgetEntryAdapter(new BudgetEntryAdapter.OnEntryActionListener() {
            @Override
            public void onEditEntry(BudgetEntryWithCategory entry) {
                if (hostListener != null) {
                    hostListener.onBudgetRequestEditEntry(entry);
                }
            }

            @Override
            public void onDeleteEntry(BudgetEntryWithCategory entry) {
                confirmDeleteEntry(entry);
            }
        });
        binding.rvBudgetEntries.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvBudgetEntries.setAdapter(entryAdapter);
    }

    private void setupCharts() {
        // 1. المخطط الدائري (Donut)
        binding.pieChartExpenses.getDescription().setEnabled(false);
        binding.pieChartExpenses.getLegend().setEnabled(false);
        binding.pieChartExpenses.setDrawEntryLabels(false);
        binding.pieChartExpenses.setUsePercentValues(false);
        binding.pieChartExpenses.setDrawHoleEnabled(true);
        binding.pieChartExpenses.setHoleColor(Color.TRANSPARENT);
        binding.pieChartExpenses.setHoleRadius(62f);
        binding.pieChartExpenses.setTransparentCircleRadius(66f);
        binding.pieChartExpenses.setTransparentCircleAlpha(60);
        binding.pieChartExpenses.setRotationAngle(270f);
        binding.pieChartExpenses.setRotationEnabled(true);
        binding.pieChartExpenses.setHighlightPerTapEnabled(true);
        binding.pieChartExpenses.setCenterTextTypeface(cairoTypeface);

        pieMarkerView = new BudgetPieMarkerView(requireContext());
        pieMarkerView.setChartView(binding.pieChartExpenses);
        binding.pieChartExpenses.setMarker(pieMarkerView);

        // 2. مخطط الأعمدة (المقارنة الشهرية)
        binding.barChartComparison.getDescription().setEnabled(false);
        binding.barChartComparison.getLegend().setEnabled(false);
        binding.barChartComparison.setDrawGridBackground(false);
        binding.barChartComparison.setDrawBarShadow(false);
        binding.barChartComparison.setPinchZoom(false);
        binding.barChartComparison.setScaleEnabled(false);
        binding.barChartComparison.setDoubleTapToZoomEnabled(false);
        binding.barChartComparison.setExtraBottomOffset(6f);

        int axisTextColor = ContextCompat.getColor(requireContext(), R.color.budget_text_secondary);
        int gridColor = ContextCompat.getColor(requireContext(), R.color.budget_border);

        XAxis xAxis = binding.barChartComparison.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setGranularity(1f);
        xAxis.setCenterAxisLabels(true);
        xAxis.setTextColor(axisTextColor);
        xAxis.setTextSize(10f);
        if (cairoTypeface != null) xAxis.setTypeface(cairoTypeface);

        YAxis leftAxis = binding.barChartComparison.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setGridColor(gridColor);
        leftAxis.setTextColor(axisTextColor);
        leftAxis.setTextSize(10f);
        leftAxis.setLabelCount(4, false);
        if (cairoTypeface != null) leftAxis.setTypeface(cairoTypeface);

        binding.barChartComparison.getAxisRight().setEnabled(false);
    }

    private void setupClickListeners() {
        binding.btnBudgetMenu.setOnClickListener(v -> {
            if (hostListener != null) {
                hostListener.onBudgetOpenDrawer();
            }
        });

        binding.btnPrevMonth.setOnClickListener(v -> viewModel.shiftMonth(-1));
        binding.btnNextMonth.setOnClickListener(v -> viewModel.shiftMonth(1));
        binding.btnBudgetCurrency.setOnClickListener(v -> showCurrencyPickerDialog());

        binding.tabBudgetSummary.setOnClickListener(v -> switchInnerTab(true));
        binding.tabBudgetTransactions.setOnClickListener(v -> switchInnerTab(false));

        binding.layoutEditBudgetLimit.setOnClickListener(v -> {
            if (hostListener != null) {
                hostListener.onBudgetRequestEditLimit(latestLimit != null ? latestLimit : 0.0);
            }
        });

        binding.cardQuickAddIncome.setOnClickListener(v -> {
            if (hostListener != null) {
                hostListener.onBudgetRequestAddEntry(BudgetEntry.TYPE_INCOME);
            }
        });

        binding.cardQuickAddExpense.setOnClickListener(v -> {
            if (hostListener != null) {
                hostListener.onBudgetRequestAddEntry(BudgetEntry.TYPE_EXPENSE);
            }
        });

        binding.btnFilterAll.setOnClickListener(v -> viewModel.setActiveFilter(BudgetViewModel.FILTER_ALL));
        binding.btnFilterExpenses.setOnClickListener(v -> viewModel.setActiveFilter(BudgetViewModel.FILTER_EXPENSE));
        binding.btnFilterIncome.setOnClickListener(v -> viewModel.setActiveFilter(BudgetViewModel.FILTER_INCOME));
    }

    private void observeData() {
        AppDatabase.getDatabase(requireContext())
                .currencyDao()
                .getAllCurrencies()
                .observe(getViewLifecycleOwner(), currencies -> {
                    availableCurrencies.clear();
                    if (currencies != null) {
                        availableCurrencies.addAll(currencies);
                    }
                    if (!currencyInitialized && !availableCurrencies.isEmpty()) {
                        currencyInitialized = true;
                        Currency first = availableCurrencies.get(0);
                        for (Currency c : availableCurrencies) {
                            if (c.isDefault()) {
                                first = c;
                                break;
                            }
                        }
                        applyCurrencySelection(first, false);
                    }
                });

        viewModel.getSelectedYearMonth().observe(getViewLifecycleOwner(), ym -> {
            if (ym != null) {
                binding.tvBudgetMonthTitle.setText(BudgetUtils.formatYearMonthDisplay(ym, requireContext()));
                boolean atMaxMonth = BudgetUtils.isAtOrAfterCurrentMonth(ym);
                binding.btnNextMonth.setEnabled(!atMaxMonth);
                binding.btnNextMonth.setAlpha(atMaxMonth ? 0.28f : 1.0f);
                renderBarChart();
            }
        });

        viewModel.getSelectedCurrencySymbol().observe(getViewLifecycleOwner(), symbol -> {
            binding.tvBudgetCurrencyLabel.setText(symbol != null ? symbol : "");
            renderSummaryCard();
            renderDistribution();
            renderFilteredEntries();
        });

        viewModel.getMonthSummary().observe(getViewLifecycleOwner(), summary -> {
            latestSummary = summary;
            summaryLoaded = true;
            renderSummaryCard();
            maybeShowFirstTimeBudgetLimitTour();
        });

        viewModel.getEffectiveBudgetLimit().observe(getViewLifecycleOwner(), limit -> {
            latestLimit = limit;
            limitLoaded = true;
            renderSummaryCard();
            maybeShowFirstTimeBudgetLimitTour();
        });

        viewModel.getExpenseDistribution().observe(getViewLifecycleOwner(), list -> {
            latestDistribution = list != null ? list : new ArrayList<>();
            renderDistribution();
        });

        viewModel.getMonthlyComparison().observe(getViewLifecycleOwner(), list -> {
            latestComparison = list != null ? list : new ArrayList<>();
            renderBarChart();
        });

        viewModel.getMonthEntries().observe(getViewLifecycleOwner(), list -> {
            latestMonthEntries.clear();
            if (list != null) {
                latestMonthEntries.addAll(list);
            }
            binding.tvBudgetCountBadge.setText(String.valueOf(latestMonthEntries.size()));
            renderFilteredEntries();
        });

        viewModel.getActiveFilter().observe(getViewLifecycleOwner(), filter -> {
            updateFilterButtonsUi(filter != null ? filter : BudgetViewModel.FILTER_ALL);
            renderFilteredEntries();
        });
    }

    public void syncCurrencyFromHost(@Nullable Currency currency) {
        if (currency == null || viewModel == null) return;
        currencyInitialized = true;
        applyCurrencySelection(currency, false);
    }

    private void applyCurrencySelection(Currency currency, boolean notifyHost) {
        if (currency == null) return;
        String shortLabel = getShortCurrencyLabel(currency);
        viewModel.setSelectedCurrency(
                currency.getId(),
                currency.getName(),
                shortLabel,
                currency.getFirestoreId()
        );
        if (notifyHost && hostListener != null) {
            hostListener.onBudgetCurrencyChanged(currency);
        }
    }

    private String getShortCurrencyLabel(Currency c) {
        if (c == null) return "";
        String sym = c.getSymbol() != null ? c.getSymbol().trim() : "";
        String code = c.getCode() != null ? c.getCode().trim() : "";
        String name = c.getName() != null ? c.getName().trim() : "";
        if (!sym.isEmpty() && !"﷼".equals(sym)) {
            return sym;
        }
        if (!code.isEmpty()) {
            return code;
        }
        return !name.isEmpty() ? name : sym;
    }

    private void showCurrencyPickerDialog() {
        if (availableCurrencies.isEmpty()) return;
        String[] labels = new String[availableCurrencies.size()];
        int checkedIndex = 0;
        int currentId = viewModel.getCurrentCurrencyIdValue();
        for (int i = 0; i < availableCurrencies.size(); i++) {
            Currency c = availableCurrencies.get(i);
            String shortLbl = getShortCurrencyLabel(c);
            String name = c.getName() != null ? c.getName() : shortLbl;
            labels[i] = name.equals(shortLbl) ? name : (name + " (" + shortLbl + ")");
            if (c.getId() == currentId) {
                checkedIndex = i;
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.budget_select_currency)
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    applyCurrencySelection(availableCurrencies.get(which), true);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.budget_cancel, null)
                .show();
    }

    private void switchInnerTab(boolean showSummary) {
        showingSummaryTab = showSummary;
        binding.scrollBudgetSummary.setVisibility(showSummary ? View.VISIBLE : View.GONE);
        binding.layoutBudgetTransactions.setVisibility(showSummary ? View.GONE : View.VISIBLE);

        int activeText = ContextCompat.getColor(requireContext(), R.color.budget_text_on_card);
        int inactiveText = ContextCompat.getColor(requireContext(), R.color.budget_text_secondary);

        if (showSummary) {
            binding.tabBudgetSummary.setBackgroundResource(R.drawable.budget_bg_tab_active);
            binding.tvTabSummary.setTextColor(activeText);
            binding.tabBudgetTransactions.setBackground(null);
            binding.tvTabTransactions.setTextColor(inactiveText);
            tintCountBadge(false);
        } else {
            binding.tabBudgetSummary.setBackground(null);
            binding.tvTabSummary.setTextColor(inactiveText);
            binding.tabBudgetTransactions.setBackgroundResource(R.drawable.budget_bg_tab_active);
            binding.tvTabTransactions.setTextColor(activeText);
            tintCountBadge(true);
        }
    }

    private void tintCountBadge(boolean onActiveTab) {
        Context ctx = requireContext();
        Drawable bg = binding.tvBudgetCountBadge.getBackground();
        if (bg != null) {
            Drawable wrapped = DrawableCompat.wrap(bg.mutate());
            DrawableCompat.setTint(wrapped, ContextCompat.getColor(
                    ctx, onActiveTab ? R.color.budget_badge_active_bg : R.color.budget_badge_bg));
            binding.tvBudgetCountBadge.setBackground(wrapped);
        }
        binding.tvBudgetCountBadge.setTextColor(ContextCompat.getColor(
                ctx, onActiveTab ? R.color.budget_badge_active_text : R.color.budget_badge_text));
    }

    private void updateFilterButtonsUi(String filter) {
        styleFilterPill(binding.btnFilterAll, BudgetViewModel.FILTER_ALL.equals(filter));
        styleFilterPill(binding.btnFilterExpenses, BudgetViewModel.FILTER_EXPENSE.equals(filter));
        styleFilterPill(binding.btnFilterIncome, BudgetViewModel.FILTER_INCOME.equals(filter));
    }

    private void styleFilterPill(android.widget.TextView pill, boolean selected) {
        pill.setBackgroundResource(selected ? R.drawable.budget_bg_tab_active : R.drawable.budget_bg_currency_chip);
        pill.setTextColor(ContextCompat.getColor(
                requireContext(),
                selected ? R.color.budget_chip_selected_text : R.color.budget_chip_unselected_text));
    }

    private void renderSummaryCard() {
        if (binding == null) return;
        Context ctx = requireContext();
        String cur = viewModel.getCurrentCurrencySymbolValue();

        double income = latestSummary != null ? latestSummary.totalIncome : 0.0;
        double expense = latestSummary != null ? latestSummary.totalExpense : 0.0;
        double net = income - expense;

        String sign = net > 0 ? "+" : (net < 0 ? "-" : "");
        binding.tvNetBalanceAmount.setText(sign + BudgetUtils.formatAmountWithCurrency(net, cur));
        binding.tvTotalIncome.setText(BudgetUtils.formatAmountWithCurrency(income, cur));
        binding.tvTotalExpense.setText(BudgetUtils.formatAmountWithCurrency(expense, cur));

        if (net < 0) {
            binding.tvBudgetStatusBadge.setText(R.string.budget_status_deficit);
            binding.tvBudgetStatusBadge.setBackgroundResource(R.drawable.budget_bg_status_deficit);
        } else if (net > 0) {
            binding.tvBudgetStatusBadge.setText(R.string.budget_status_surplus);
            binding.tvBudgetStatusBadge.setBackgroundResource(R.drawable.budget_bg_status_surplus);
        } else {
            binding.tvBudgetStatusBadge.setText(R.string.budget_status_balanced);
            binding.tvBudgetStatusBadge.setBackgroundResource(R.drawable.budget_bg_glass_box);
        }

        double limit = (latestLimit != null && latestLimit > 0) ? latestLimit : 0.0;
        if (limit > 0) {
            binding.tvBudgetLimitLabel.setText(
                    getString(R.string.budget_limit_label, BudgetUtils.formatAmountWithCurrency(limit, cur)));
            int usagePct = (int) Math.round((expense / limit) * 100.0);
            binding.tvBudgetUsagePercent.setText(getString(R.string.budget_usage_label, usagePct));
            binding.progressBudgetUsage.setProgress(Math.min(usagePct, 100));

            int progressColor;
            if (usagePct > 100) {
                progressColor = ContextCompat.getColor(ctx, R.color.budget_progress_over);
            } else if (usagePct >= 80) {
                progressColor = ContextCompat.getColor(ctx, R.color.budget_progress_warn);
            } else {
                progressColor = ContextCompat.getColor(ctx, R.color.budget_progress_ok);
            }
            applyProgressBarColor(progressColor);

            double remaining = limit - expense;
            binding.tvBudgetRemainingPill.setVisibility(View.VISIBLE);
            if (remaining >= 0) {
                binding.tvBudgetRemainingPill.setText(
                        getString(R.string.budget_remaining_label, BudgetUtils.formatAmountWithCurrency(remaining, cur)));
                binding.tvBudgetRemainingPill.setBackgroundResource(R.drawable.budget_bg_glass_box);
            } else {
                binding.tvBudgetRemainingPill.setText(
                        getString(R.string.budget_exceeded_label, BudgetUtils.formatAmountWithCurrency(Math.abs(remaining), cur)));
                binding.tvBudgetRemainingPill.setBackgroundResource(R.drawable.budget_bg_status_deficit);
            }
        } else {
            binding.tvBudgetLimitLabel.setText(R.string.budget_limit_unset);
            binding.tvBudgetUsagePercent.setText(getString(R.string.budget_usage_label, 0));
            binding.progressBudgetUsage.setProgress(0);
            applyProgressBarColor(ContextCompat.getColor(ctx, R.color.budget_progress_ok));
            binding.tvBudgetRemainingPill.setVisibility(View.GONE);
        }
    }

    private void applyProgressBarColor(int color) {
        Drawable d = binding.progressBudgetUsage.getProgressDrawable();
        if (d instanceof LayerDrawable) {
            LayerDrawable layer = (LayerDrawable) d;
            Drawable clip = layer.findDrawableByLayerId(android.R.id.progress);
            if (clip != null) {
                Drawable wrapped = DrawableCompat.wrap(clip.mutate());
                DrawableCompat.setTint(wrapped, color);
                layer.setDrawableByLayerId(android.R.id.progress, wrapped);
            }
        }
    }

    private void renderDistribution() {
        if (binding == null) return;
        Context ctx = requireContext();
        String cur = viewModel.getCurrentCurrencySymbolValue();

        double totalExpense = 0.0;
        for (CategoryExpenseTotal item : latestDistribution) {
            totalExpense += item.totalAmount;
        }

        distributionAdapter.submitData(latestDistribution, totalExpense, cur);
        if (pieMarkerView != null) {
            pieMarkerView.setMeta(cur, totalExpense);
        }

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        int fallbackColor = ContextCompat.getColor(ctx, R.color.budget_primary);

        for (CategoryExpenseTotal item : latestDistribution) {
            if (item.totalAmount > 0) {
                String label = BudgetUtils.getCategoryDisplayName(ctx, item.categoryCode, item.name, item.isDefault);
                entries.add(new PieEntry((float) item.totalAmount, label));
                colors.add(BudgetUtils.parseColorSafe(item.colorHex, fallbackColor));
            }
        }

        if (entries.isEmpty()) {
            binding.pieChartExpenses.setVisibility(View.INVISIBLE);
            binding.tvEmptyPieState.setVisibility(View.VISIBLE);
            return;
        }

        binding.tvEmptyPieState.setVisibility(View.GONE);
        binding.pieChartExpenses.setVisibility(View.VISIBLE);

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(6f);
        dataSet.setDrawValues(false);

        PieData pieData = new PieData(dataSet);
        binding.pieChartExpenses.setData(pieData);
        binding.pieChartExpenses.setCenterText(buildDonutCenterText(totalExpense, cur));
        binding.pieChartExpenses.highlightValues(null);
        binding.pieChartExpenses.animateY(450);
        binding.pieChartExpenses.invalidate();
    }

    private CharSequence buildDonutCenterText(double totalExpense, String cur) {
        Context ctx = requireContext();
        int secondary = ContextCompat.getColor(ctx, R.color.budget_text_secondary);
        int primary = ContextCompat.getColor(ctx, R.color.budget_text_primary);

        String title = getString(R.string.budget_total_center);
        String amount = BudgetUtils.formatAmount(totalExpense);

        SpannableStringBuilder sb = new SpannableStringBuilder();
        int s1 = sb.length();
        sb.append(title).append("\n");
        sb.setSpan(new ForegroundColorSpan(secondary), s1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new AbsoluteSizeSpan(11, true), s1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int s2 = sb.length();
        sb.append(amount);
        sb.setSpan(new ForegroundColorSpan(primary), s2, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), s2, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new AbsoluteSizeSpan(16, true), s2, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (cur != null && !cur.trim().isEmpty()) {
            sb.append("\n");
            int s3 = sb.length();
            sb.append(cur.trim());
            sb.setSpan(new ForegroundColorSpan(secondary), s3, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new AbsoluteSizeSpan(11, true), s3, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private void renderBarChart() {
        if (binding == null) return;
        Context ctx = requireContext();
        String currentYm = viewModel.getCurrentYearMonthValue();
        List<String> sixMonths = BudgetUtils.getLastSixMonths(currentYm);

        Map<String, MonthlyTotals> map = new HashMap<>();
        for (MonthlyTotals mt : latestComparison) {
            if (mt != null && mt.yearMonth != null) {
                map.put(mt.yearMonth, mt);
            }
        }

        List<BarEntry> incomeEntries = new ArrayList<>(6);
        List<BarEntry> expenseEntries = new ArrayList<>(6);
        List<String> labels = new ArrayList<>(6);

        for (int i = 0; i < sixMonths.size(); i++) {
            String ym = sixMonths.get(i);
            MonthlyTotals mt = map.get(ym);
            float inc = mt != null ? (float) mt.totalIncome : 0f;
            float exp = mt != null ? (float) mt.totalExpense : 0f;
            incomeEntries.add(new BarEntry(i, inc));
            expenseEntries.add(new BarEntry(i, exp));
            labels.add(BudgetUtils.formatMonthShortLabel(ym, ctx));
        }

        BarDataSet incomeSet = new BarDataSet(incomeEntries, getString(R.string.budget_legend_income));
        incomeSet.setColor(ContextCompat.getColor(ctx, R.color.budget_income));
        incomeSet.setDrawValues(false);

        BarDataSet expenseSet = new BarDataSet(expenseEntries, getString(R.string.budget_legend_expenses));
        expenseSet.setColor(ContextCompat.getColor(ctx, R.color.budget_expense));
        expenseSet.setDrawValues(false);

        float barWidth = 0.28f;
        float barSpace = 0.06f;
        float groupSpace = 0.32f;

        BarData data = new BarData(incomeSet, expenseSet);
        data.setBarWidth(barWidth);

        XAxis xAxis = binding.barChartComparison.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(sixMonths.size());
        xAxis.setLabelCount(sixMonths.size());

        binding.barChartComparison.setData(data);
        binding.barChartComparison.groupBars(0f, groupSpace, barSpace);
        binding.barChartComparison.animateY(450);
        binding.barChartComparison.invalidate();
    }

    private void renderFilteredEntries() {
        if (binding == null) return;
        String filter = viewModel.getActiveFilter().getValue();
        if (filter == null) filter = BudgetViewModel.FILTER_ALL;

        List<BudgetEntryWithCategory> filtered = new ArrayList<>();
        for (BudgetEntryWithCategory item : latestMonthEntries) {
            if (BudgetViewModel.FILTER_ALL.equals(filter) || filter.equals(item.type)) {
                filtered.add(item);
            }
        }

        entryAdapter.submitData(filtered, viewModel.getCurrentCurrencySymbolValue());
        boolean empty = filtered.isEmpty();
        binding.layoutEmptyTransactions.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvBudgetEntries.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void confirmDeleteEntry(BudgetEntryWithCategory entry) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.budget_delete_confirm_title)
                .setMessage(R.string.budget_delete_confirm_msg)
                .setPositiveButton(R.string.budget_delete, (d, w) -> viewModel.deleteEntry(entry.id, () -> {
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), R.string.budget_deleted_success, Toast.LENGTH_SHORT).show());
                    }
                }))
                .setNegativeButton(R.string.budget_cancel, null)
                .show();
    }


    private void maybeShowFirstTimeBudgetLimitTour() {
        if (limitTourScheduled || !summaryLoaded || !limitLoaded || !showingSummaryTab) return;
        if (!isAdded() || binding == null) return;
        if (latestLimit != null && latestLimit > 0) return;
        if (latestSummary != null && latestSummary.entryCount > 0) return;

        SharedPreferences prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("budget_limit_tour_shown", false)) {
            limitTourScheduled = true;
            return;
        }

        limitTourScheduled = true;
        AppDatabase db = AppDatabase.getDatabase(requireContext());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int totalEntries = db.budgetEntryDao().getTotalActiveEntryCountSync();
            int totalLimits = db.monthlyBudgetLimitDao().getTotalActiveLimitCountSync();
            if (totalEntries > 0 || totalLimits > 0) {
                prefs.edit().putBoolean("budget_limit_tour_shown", true).apply();
                return;
            }
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || binding == null || !showingSummaryTab) return;
                binding.layoutEditBudgetLimit.postDelayed(() -> {
                    if (!isAdded() || binding == null || !binding.layoutEditBudgetLimit.isShown()) {
                        limitTourScheduled = false;
                        return;
                    }
                    showBudgetLimitSpotlight(prefs);
                }, 450);
            });
        });
    }

    private void showBudgetLimitSpotlight(SharedPreferences prefs) {
        try {
            TapTarget target = TapTarget.forView(
                            binding.layoutEditBudgetLimit,
                            getString(R.string.budget_tour_limit_title),
                            getString(R.string.budget_tour_limit_desc))
                    .outerCircleColor(R.color.budget_primary)
                    .outerCircleAlpha(0.95f)
                    .targetCircleColor(android.R.color.white)
                    .titleTextSize(19)
                    .titleTextColor(android.R.color.white)
                    .descriptionTextSize(14)
                    .descriptionTextColor(android.R.color.white)
                    .drawShadow(true)
                    .cancelable(true)
                    .tintTarget(false)
                    .transparentTarget(true)
                    .targetRadius(54);

            if (cairoTypeface != null) {
                target.textTypeface(cairoTypeface);
            }

            TapTargetView.showFor(requireActivity(), target, new TapTargetView.Listener() {
                @Override
                public void onTargetClick(TapTargetView view) {
                    super.onTargetClick(view);
                    prefs.edit().putBoolean("budget_limit_tour_shown", true).apply();
                    if (hostListener != null) {
                        hostListener.onBudgetRequestEditLimit(0.0);
                    }
                }

                @Override
                public void onTargetCancel(TapTargetView view) {
                    super.onTargetCancel(view);
                    prefs.edit().putBoolean("budget_limit_tour_shown", true).apply();
                }

                @Override
                public void onTargetDismissed(TapTargetView view, boolean userInitiated) {
                    prefs.edit().putBoolean("budget_limit_tour_shown", true).apply();
                }
            });
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

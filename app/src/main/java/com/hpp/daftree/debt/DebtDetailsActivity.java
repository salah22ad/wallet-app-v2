package com.hpp.daftree.debt;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.adapters.TransactionAdapter;
import com.hpp.daftree.databinding.ActivityDebtDetailsBinding;
import com.hpp.daftree.debt.ExternalDebtModels.ExternalTransaction;
import com.hpp.daftree.ui.BaseActivity;
import com.hpp.daftree.viewmodels.DebtDetailsViewModel;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DebtDetailsActivity extends BaseActivity {

    private ActivityDebtDetailsBinding binding;
    private TransactionAdapter adapter;
    private DebtDetailsViewModel viewModel; // استخدام ViewModel الجديد

    private Calendar startDateCalendar = Calendar.getInstance();
    private Calendar endDateCalendar = Calendar.getInstance();
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDebtDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        setupEdgeToEdgeInsets();

        // تفعيل زر الرجوع في شريط الأدوات
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }



        // استلام البيانات من Intent
        String accountId = getIntent().getStringExtra("ACCOUNT_ID");
        String currencyId = getIntent().getStringExtra("CURRENCY_ID");
        String ownerName = getIntent().getStringExtra("OWNER_NAME");
        String accountName = getIntent().getStringExtra("ACCOUNT_NAME");
        String currencyName = getIntent().getStringExtra("CURRENCY_NAME");

        // إعداد العناوين
        binding.tvTitle.setText(getString(R.string.details_title, ownerName));
        binding.tvAccountName.setText(getString(R.string.account_name) +": "+ (accountName != null ? accountName : ""));
        binding.tvCurrencyName.setText(getString(R.string.currency)+": "+ (currencyName != null ? currencyName : ""));

        // إعداد القائمة
        adapter = new TransactionAdapter();
        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTransactions.setAdapter(adapter);
        binding.btnBack.setOnClickListener(v -> finish());
        // تهيئة ViewModel باستخدام Factory لأننا نمرر parameters
        viewModel = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) new DebtDetailsViewModel(getApplication(), accountId, currencyId);
            }
        }).get(DebtDetailsViewModel.class);

        setupFilterLogic();
        observeViewModel();
        setupInsets();

    }
    private void setupInsets() {
        View bottomNavContainer = binding.bottomBar; // أو findViewById(R.id.bottom_nav_container);

        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) bottomNavContainer.getLayoutParams();
        int originalBottomMargin = layoutParams.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavContainer, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.bottomMargin = originalBottomMargin + systemBars.bottom;
            view.setLayoutParams(params);
            return windowInsets;
        });
    }

    private void observeViewModel() {
        // مراقبة القائمة (المفلترة أو الكاملة)
        viewModel.getDisplayedTransactions().observe(this, list -> {
            if (list == null || list.isEmpty()) {
                binding.tvNoTransactions.setVisibility(View.VISIBLE);
                adapter.submitList(list);
                updateSummarySection(0, 0); // تصفير الملخص
            } else {
                binding.tvNoTransactions.setVisibility(View.GONE);
                adapter.submitList(list);
                calculateTotalsForList(list);

                // تحديث التاريخ الافتراضي "من" ليكون تاريخ أول عملية (فقط عند العرض الأول للكل)
                // يمكن تحسين المنطق هنا لمنع تغير التاريخ عند الفلترة اليدوية
                if (startDateCalendar.get(Calendar.DAY_OF_YEAR) == 1 && list.size() > 0) { // اذا كان افتراضي بداية السنة
                    long firstTs = viewModel.getFirstTransactionTimestamp();
                    if(firstTs > 0) startDateCalendar.setTimeInMillis(firstTs);
                    updateDateViews();
                }
            }
            binding.progressBar.setVisibility(View.GONE);
        });
    }


    private void setupEdgeToEdgeInsets() {
        // 2. جعل شريط الحالة شفافاً

        // 3. ضبط ارتفاع الفاصل (Spacer) ليساوي ارتفاع شريط الحالة
        View spacer = findViewById(R.id.status_bar_spacer);
        if (spacer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(spacer, (view, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                // تغيير ارتفاع الفاصل ليطابق ارتفاع شريط الحالة
                android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
                if (params.height != bars.top) {
                    params.height = bars.top;
                    view.setLayoutParams(params);
                }

                return insets;
            });
        }

        // 4. ضبط الحافة السفلية للمحتوى (لتجنب شريط التنقل السفلي)
        View contentContainer = findViewById(R.id.main_content_container); // تأكد أن هذا الـ ID موجود في الـ XML
        if (contentContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(contentContainer, (view, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bars.bottom);
                return insets;
            });
        }
    }

    private void setupFilterLogic() {
        resetDateFilters();

        binding.tvFromDate.setOnClickListener(v -> showDatePicker(startDateCalendar, binding.tvFromDate));
        binding.tvToDate.setOnClickListener(v -> showDatePicker(endDateCalendar, binding.tvToDate));

        // زر "الكل" أو "إعادة تعيين"
        binding.btnResetFilter.setOnClickListener(v -> {
            resetDateFilters();
            viewModel.showAllTransactions(); // طلب عرض الكل من ViewModel
        });

        // زر "تطبيق الفلتر"
        binding.btnApplyFilter.setOnClickListener(v -> {
            long start = startDateCalendar.getTimeInMillis();
            long end = endDateCalendar.getTimeInMillis();
            viewModel.filterTransactions(start, end); // طلب الفلترة من ViewModel
        });
    }

    private void resetDateFilters() {
        endDateCalendar = Calendar.getInstance();
        startDateCalendar = Calendar.getInstance();
        startDateCalendar.set(Calendar.DAY_OF_YEAR, 1);
        startDateCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startDateCalendar.set(Calendar.MINUTE, 0);
        startDateCalendar.set(Calendar.SECOND, 0);
        updateDateViews();
    }

    private void updateDateViews() {
        binding.tvFromDate.setText(displayDateFormat.format(startDateCalendar.getTime()));
        binding.tvToDate.setText(displayDateFormat.format(endDateCalendar.getTime()));
    }

    private void showDatePicker(Calendar calendar, android.widget.TextView textView) {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    if (textView == binding.tvFromDate) {
                        calendar.set(Calendar.HOUR_OF_DAY, 0);
                    } else {
                        calendar.set(Calendar.HOUR_OF_DAY, 23);
                        calendar.set(Calendar.MINUTE, 59);
                    }
                    textView.setText(displayDateFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void calculateTotalsForList(List<ExternalTransaction> list) {
        double totalDebit = 0;
        double totalCredit = 0;

        for (ExternalTransaction tx : list) {
            if (tx.type == 1) totalDebit += tx.amount;
            else totalCredit += tx.amount;
        }

        updateSummarySection(totalDebit, totalCredit);

        binding.tvGrandTotalDebit.setText(format(totalDebit));
        binding.tvGrandTotalCredit.setText(format(totalCredit));
//        binding.tvGrandNetBalance.setText(format(totalDebit - totalCredit));
        binding.tvGrandNetBalance.setText(format( Math.abs(totalDebit - totalCredit)));
        binding.tvGrandNetBalance.setTextColor(totalDebit - totalCredit >= 0
                ? ContextCompat.getColor(this, R.color.colorDebit)
                :ContextCompat.getColor(this, R.color.colorCredit));
    }

    private void updateSummarySection(double totalDebit, double totalCredit) {
        double netBalance = totalDebit - totalCredit;

//        binding.tvSummary.setVisibility(View.VISIBLE);
//        binding.tvTotalBalance.setVisibility(View.VISIBLE);

        String debitColor = getHexColor(R.color.colorDebit);
        String creditColor = getHexColor(R.color.colorCredit);

        String summaryHtml = "ما علي: <font color='" + debitColor + "'><b>" + format(totalDebit) + "</b></font>" +
                "  |  " +
                "ما لي: <font color='" + creditColor + "'><b>" + format(totalCredit) + "</b></font>";

        binding.tvSummary.setText(Html.fromHtml(summaryHtml, Html.FROM_HTML_MODE_LEGACY));

        binding.tvTotalBalance.setText("الرصيد: " + format(Math.abs(netBalance)));

        if (netBalance > 0) {
            binding.tvTotalBalance.setTextColor(ContextCompat.getColor(this, R.color.colorDebit));
            binding.tvTotalBalance.append(" (عليك)");
        } else if (netBalance < 0) {
            binding.tvTotalBalance.setTextColor(ContextCompat.getColor(this, R.color.colorCredit));
            binding.tvTotalBalance.append(" (لك)");
        } else {
            binding.tvTotalBalance.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        }
    }

    private String getHexColor(int colorResId) {
        int color = ContextCompat.getColor(this, colorResId);
        return String.format("#%06X", (0xFFFFFF & color));
    }

    private String format(double val) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        return nf.format(val);
    }
}
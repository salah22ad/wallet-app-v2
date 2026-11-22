package com.hpp.daftree.adapters;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hpp.daftree.R;
import com.hpp.daftree.databinding.ItemDebtTransactionBinding;
import com.hpp.daftree.debt.ExternalDebtModels.ExternalTransaction;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.google.firebase.Timestamp;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private List<ExternalTransaction> items = new ArrayList<>();
    // قائمة إضافية لتخزين الرصيد المحسوب مسبقاً لكل سطر
    private List<Double> preCalculatedBalances = new ArrayList<>();

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public void submitList(List<ExternalTransaction> newItems) {
        this.items = newItems;
        // نقوم بحساب الأرصدة مرة واحدة هنا قبل العرض
        calculateRunningBalances();
        notifyDataSetChanged();
    }

    // دالة لحساب الرصيد التراكمي وتخزينه
    private void calculateRunningBalances() {
        preCalculatedBalances.clear();
        double currentRunningBalance = 0;

        for (ExternalTransaction tx : items) {
            // المنطق: إذا كان نوع 1 (مدين/عليك) يضاف، وإذا غير ذلك (دائن/لك) يخصم
            // أو العكس حسب منطق تطبيقك. هنا افترضنا:
            // Type 1 = مدين (زيادة الدين)
            // Type -1 = دائن (سداد/نقص الدين)

            if (tx.type == 1) {
                currentRunningBalance += tx.amount;
            } else {
                currentRunningBalance -= tx.amount;
            }

            // تخزين النتيجة لهذه العملية تحديداً
            preCalculatedBalances.add(currentRunningBalance);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemDebtTransactionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExternalTransaction item = items.get(position);
        // نمرر الرصيد المحسوب مسبقاً لهذا السطر
        double balanceForThisRow = preCalculatedBalances.get(position);
        holder.bind(item, balanceForThisRow, position);
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemDebtTransactionBinding binding;

        ViewHolder(ItemDebtTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        // تم إضافة المعامل currentBalance للدالة
        void bind(ExternalTransaction tx, double currentBalance, int position) {
            Context context = binding.getRoot().getContext();

            // تلوين الخلفية (صفوف مخططة)
            if (position % 2 == 0) {
                binding.getRoot().setBackgroundColor(ContextCompat.getColor(context, R.color.striped_row_background));
            } else {
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true);
                binding.getRoot().setBackgroundColor(typedValue.data);
            }

            // عرض التاريخ
            if (tx.timestamp instanceof Timestamp) {
                binding.tvDate.setText(sdf.format(((Timestamp) tx.timestamp).toDate()));
            } else if (tx.timestamp instanceof Long) {
                binding.tvDate.setText(sdf.format(new Date((Long) tx.timestamp)));
            }

            binding.tvDetails.setText(tx.details);

            // عرض المبلغ في العمود المناسب
            if (tx.type == 1) { // مدين
                binding.tvDebit.setText(formatCurrency(tx.amount));
                binding.tvCredit.setText("0");
            } else { // دائن
                binding.tvDebit.setText("0");
                binding.tvCredit.setText(formatCurrency(tx.amount));
            }

            // عرض الرصيد التراكمي (يستخدم القيمة الممررة المحسوبة مسبقاً)
            // استخدام Math.abs لإزالة السالب كما طلبت سابقاً
            binding.tvBalance.setText(formatCurrency(Math.abs(currentBalance)));

            // تلوين الرصيد بناءً على القيمة الحقيقية (سالب/موجب)
            binding.tvBalance.setTextColor(currentBalance >= 0
                    ? ContextCompat.getColor(context, R.color.colorDebit)
                    : ContextCompat.getColor(context, R.color.colorCredit));
        }
    }

    private String formatCurrency(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount);
    }
}
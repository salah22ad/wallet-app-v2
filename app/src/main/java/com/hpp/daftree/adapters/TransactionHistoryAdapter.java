package com.hpp.daftree.adapters;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.models.TransactionItem;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.databinding.ItemTransactionHistoryBinding;
import com.hpp.daftree.models.FormatingAmount;
import com.hpp.daftree.utils.TextViewUtils;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import androidx.core.text.HtmlCompat;

public class TransactionHistoryAdapter extends ListAdapter<TransactionItem, TransactionHistoryAdapter.TransactionViewHolder> {

    public interface OnItemInteractionListener {
        void onItemLongClick(TransactionItem transactionItem);

        void onEditIconClick(TransactionItem transactionItem); // إضافة دالة جديدة للأيقونة
    }

    private final OnItemInteractionListener listener;
    private final Context context;
    private final boolean showEditIcon; // متغير جديد للتحكم في عرض الأيقونة

    // تعديل الكونستركتور ليقبل معلمة إضافية
    public TransactionHistoryAdapter(Context context, OnItemInteractionListener listener, boolean showEditIcon) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.listener = listener;
        this.showEditIcon = showEditIcon;
    }

    // الكونستركتور القديم للحفاظ على التوافق
    public TransactionHistoryAdapter(Context context, OnItemInteractionListener listener) {
        this(context, listener, false); // قيمة افتراضية false
    }

    private static final DiffUtil.ItemCallback<TransactionItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<TransactionItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull TransactionItem oldItem, @NonNull TransactionItem newItem) {
            // استخدام firestoreId لأنه فريد وغير قابل للتكرار
            return oldItem.getTransaction().getFirestoreId().equals(newItem.getTransaction().getFirestoreId());
        }
        @Override
        public boolean areContentsTheSame(@NonNull TransactionItem oldItem, @NonNull TransactionItem newItem) {
            return oldItem.getBalanceAfter() == newItem.getBalanceAfter() &&
                    oldItem.getTransaction().getAmount() == newItem.getTransaction().getAmount() &&
                    oldItem.getTransaction().getDetails().equals(newItem.getTransaction().getDetails()) &&
                    oldItem.getTransaction().getTimestamp().equals(newItem.getTransaction().getTimestamp()); // تم إضافة مقارنة التاريخ هنا
        }
    };

//    private static final DiffUtil.ItemCallback<TransactionItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<TransactionItem>() {
//        @Override
//        public boolean areItemsTheSame(@NonNull TransactionItem oldItem, @NonNull TransactionItem newItem) {
//            return oldItem.getTransaction().getId() == newItem.getTransaction().getId();
//        }
//
//        @Override
//        public boolean areContentsTheSame(@NonNull TransactionItem oldItem, @NonNull TransactionItem newItem) {
//            return oldItem.getBalanceAfter() == newItem.getBalanceAfter() &&
//                    oldItem.getTransaction().getAmount() == newItem.getTransaction().getAmount() &&
//                    oldItem.getTransaction().getDetails().equals(newItem.getTransaction().getDetails());
//
//        }
//    };

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTransactionHistoryBinding binding = ItemTransactionHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new TransactionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        TransactionItem transactionItem = getItem(position);
        holder.bind(transactionItem, position);

        // إعداد النقر المطول على العنصر كاملاً
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(transactionItem);
            }
            return true;
        });
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final ItemTransactionHistoryBinding binding;

        public TransactionViewHolder(ItemTransactionHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TransactionItem item, int position) {
            if (position % 2 == 0) {
                // الصفوف الزوجية
                itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.striped_row_background));
            } else {
                // الصفوف الفردية، نستخدم لون السطح الافتراضي للثيم
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true);
                itemView.setBackgroundColor(typedValue.data);
            }
            Transaction transaction = item.getTransaction();

            // **المنطق الجديد: دمج نوع الفاتورة مع التفاصيل**
//            if (transaction.getBillType() != null && !transaction.getBillType().isEmpty()) {
//                String combinedDetails = "<b>" + transaction.getBillType() + ":</b> " + transaction.getDetails();
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                    binding.detailsTextView.setText(Html.fromHtml(combinedDetails, Html.FROM_HTML_MODE_LEGACY));
//                } else {
//                    binding.detailsTextView.setText(Html.fromHtml(combinedDetails));
//                }
//            } else {
//                binding.detailsTextView.setText(transaction.getDetails());
//            }
            if (transaction.getBillType() != null && !transaction.getBillType().isEmpty()) {
                String combinedDetails = "<b>" + transaction.getBillType() + ":</b> " + transaction.getDetails();
                // FIX: استخدام HtmlCompat لمعالجة التحذير ودعم جميع الإصدارات
                binding.detailsTextView.setText(HtmlCompat.fromHtml(combinedDetails, HtmlCompat.FROM_HTML_MODE_LEGACY));
            } else {
                binding.detailsTextView.setText(transaction.getDetails());
            }
            NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);

            binding.dateTextView.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(transaction.getTimestamp()));
            binding.timeTextView.setText(new SimpleDateFormat("HH:mm:ss", Locale.US).format(transaction.getTimestamp()));
            binding.amountTextView.setText(FormatingAmount.formatForDisplay(transaction.getAmount()));
            binding.balanceTextView.setText(FormatingAmount.formatForDisplay(item.getBalanceAfter()));
//             TextViewUtils.adjustTextSizeToFit(binding.amountTextView,
//                     formatCurrency(transaction.getAmount()), 14f, 6f);
//               binding.balanceTextView.setText(formatCurrency(item.getBalanceAfter()));
//            TextViewUtils.adjustTextSizeToFit(binding.balanceTextView,
//                    formatCurrency(item.getBalanceAfter()), 14f, 6f);
            if (transaction.getType() == 1) { // مدين (له)
                binding.arrowImageView.setImageResource(R.drawable.ic_arrow_down_red);
                binding.amountTextView.setTextColor(ContextCompat.getColor(context, R.color.red));
            } else { // دائن (عليه)
                binding.arrowImageView.setImageResource(R.drawable.ic_arrow_up_green);
                binding.amountTextView.setTextColor(ContextCompat.getColor(context, R.color.green));
            }

            // ⭐⭐ التحكم في عرض أيقونة التعديل ⭐⭐
            if (showEditIcon && listener != null) {
                binding.arrowEditView.setVisibility(View.VISIBLE);
                binding.arrowEditView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onEditIconClick(item);
                    }
                });
            } else {
                binding.arrowEditView.setVisibility(View.GONE);
            }
        }
    }
    private String formatCurrency(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }
}
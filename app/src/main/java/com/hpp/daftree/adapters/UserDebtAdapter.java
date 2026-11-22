package com.hpp.daftree.adapters;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hpp.daftree.R;
import com.hpp.daftree.databinding.ItemUserDebtSummaryBinding;
import com.hpp.daftree.databinding.ItemAccountSummaryBinding; // نحتاج إنشاء بسيط لهذا أو استخدام نص
import com.hpp.daftree.databinding.ItemCurrencySummaryBinding;
import com.hpp.daftree.debt.ExternalDebtModels.UserDebtSummary;
import com.hpp.daftree.debt.ExternalDebtModels.AccountDebtSummary;
import com.hpp.daftree.debt.ExternalDebtModels.CurrencySummary;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UserDebtAdapter extends RecyclerView.Adapter<UserDebtAdapter.ViewHolder> {

    private List<UserDebtSummary> items = new ArrayList<>();
    private final OnDetailsClickListener listener;

    public interface OnDetailsClickListener {
        void onDetailsClick(UserDebtSummary user, AccountDebtSummary account, CurrencySummary currency);
    }

    public UserDebtAdapter(OnDetailsClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<UserDebtSummary> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemUserDebtSummaryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemUserDebtSummaryBinding binding;

        ViewHolder(ItemUserDebtSummaryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(UserDebtSummary user) {
            // اسم المستخدم (صاحب الديون)
            binding.tvUserName.setText(binding.getRoot().getContext().getString(R.string.title_statement_with, user.ownerName));
            String ownerPhone = user.ownerPhone;
            if (ownerPhone != null && !ownerPhone.isEmpty()) {
                binding.tvUserPhone.setText(binding.getRoot().getContext().getString(R.string.title_statement_phone, user.ownerPhone));
            } else {
                binding.tvUserPhone.setVisibility(View.GONE);
            }
            // نخفي tvAccountName من الكارد الرئيسي لأننا سنعرض أسماء الحسابات بالداخل
            binding.tvAccountName.setVisibility(View.GONE);

            // نجهز القائمة الداخلية المسطحة (Header -> Currencies -> Header -> Currencies)
            List<Object> flatList = new ArrayList<>();
            for (AccountDebtSummary acc : user.accounts) {
                flatList.add(acc); // هذا سيكون العنوان (اسم الحساب)
                flatList.addAll(acc.currencies); // هذه العملات
            }

            InnerAdapter innerAdapter = new InnerAdapter(flatList, user);
            binding.rvCurrencies.setLayoutManager(new LinearLayoutManager(binding.getRoot().getContext()));
            binding.rvCurrencies.setAdapter(innerAdapter);

            // منطق التوسيع
            binding.btnExpand.setOnClickListener(v -> {
                boolean isVisible = binding.currenciesContainer.getVisibility() == View.VISIBLE;
                binding.currenciesContainer.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                binding.btnExpand.setRotation(isVisible ? 0 : 180);
            });

            // افتراضياً ظاهر حسب طلبك أو مخفي
            binding.currenciesContainer.setVisibility(View.VISIBLE);
            binding.btnExpand.setRotation(180);
        }
    }

    // Adapter داخلي للتعامل مع العناوين (أسماء الحسابات) والعملات
    class InnerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Object> items;
        private final UserDebtSummary user;
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_CURRENCY = 1;

        InnerAdapter(List<Object> items, UserDebtSummary user) {
            this.items = items;
            this.user = user;
        }

        @Override
        public int getItemViewType(int position) {
            return (items.get(position) instanceof AccountDebtSummary) ? TYPE_HEADER : TYPE_CURRENCY;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                // استخدام item_account_summary.xml الذي أرسلته سابقاً
                // أو إنشاء Layout بسيط يحتوي TextView فقط لاسم الحساب
                return new HeaderViewHolder(ItemAccountSummaryBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false));
            } else {
                return new CurrencyViewHolder(ItemCurrencySummaryBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind((AccountDebtSummary) items.get(position));
            } else {
                // نحتاج معرفة الحساب التابع لهذه العملة
                CurrencySummary currency = (CurrencySummary) items.get(position);
                // نبحث عن الحساب الخاص بهذه العملة (يمكن تمريره، أو البحث في القائمة العكسية)
                // للسهولة، سنعتمد على أن currency.accountFirestoreId موجود
                // ولكن للنقر، نحتاج كائن AccountDebtSummary.
                // الحل: عند النقر نمرر البيانات.
                ((CurrencyViewHolder) holder).bind(currency,position);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            ItemAccountSummaryBinding binding;

            HeaderViewHolder(ItemAccountSummaryBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(AccountDebtSummary acc) {
                binding.tvAccountName.setText(binding.getRoot().getContext().getString(R.string.account_name) + ": " + acc.accountName); // اسم الحساب: محطة صلاح
                // نخفي الـ RecyclerView الداخلي الموجود في XML التصميم لأنه غير مستخدم هنا
                binding.rvCurrencies.setVisibility(View.GONE);
            }
        }

        class CurrencyViewHolder extends RecyclerView.ViewHolder {
            ItemCurrencySummaryBinding binding;

            CurrencyViewHolder(ItemCurrencySummaryBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(CurrencySummary item, int position) {
                Context context = binding.getRoot().getContext();
                // تلوين الصفوف الفردية والزوجية
                if (position % 2 == 0) {
                    // الصفوف الزوجية
                    itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.striped_row_background));
                } else {
                    // الصفوف الفردية
                    TypedValue typedValue = new TypedValue();
                    context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true);
                    itemView.setBackgroundColor(typedValue.data);
                }
                binding.tvCurrencyName.setText(item.currencyName);
                binding.tvTotalDebit.setText(format(item.totalDebit));
                binding.tvTotalCredit.setText(format(item.totalCredit));
                binding.tvNetBalance.setText(format(Math.abs(item.netBalance)));

                // الألوان والنصوص (لك / عليك)
                if (item.netBalance > 0) {
                    binding.tvNetBalance.setTextColor(binding.getRoot().getContext().getColor(R.color.colorDebit));
//                    binding.tvNetBalance.append(" ("+ContextCompat.getString(R.string.for_you) + ")");
                } else if (item.netBalance < 0) {
                    binding.tvNetBalance.setTextColor(binding.getRoot().getContext().getColor(R.color.colorCredit)); // تأكد من وجود اللون
//                    binding.tvNetBalance.append(" (عليك)");
                }

                binding.getRoot().setOnClickListener(v -> {
                    if (listener != null) {
                        // نحتاج تمرير AccountDebtSummary، يمكننا إيجاده من الذاكرة أو إنشاؤه مؤقتاً
                        // أو تعديل Interface ليأخذ IDs فقط.
                        // هنا سنبحث عنه في القائمة (سابقاً)
                        AccountDebtSummary acc = findAccountForCurrency(item);
                        listener.onDetailsClick(user, acc, item);
                    }
                });
            }
        }

        private AccountDebtSummary findAccountForCurrency(CurrencySummary curr) {
            for (AccountDebtSummary acc : user.accounts) {
                if (acc.accountId.equals(curr.accountFirestoreId)) return acc;
            }
            return null;
        }
    }

    private String format(double val) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        return nf.format(val);
    }
}
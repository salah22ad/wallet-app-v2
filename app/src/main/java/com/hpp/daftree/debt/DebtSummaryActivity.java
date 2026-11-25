package com.hpp.daftree.debt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hpp.daftree.models.AppAuth;
import com.hpp.daftree.MainActivity;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.adapters.UserDebtAdapter;
import com.hpp.daftree.databinding.ActivityDebtSummaryBinding;
import com.hpp.daftree.debt.ExternalDebtModels.AccountDebtSummary;
import com.hpp.daftree.debt.ExternalDebtModels.CurrencySummary;
import com.hpp.daftree.debt.ExternalDebtModels.UserDebtSummary;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.ui.BaseActivity;
import com.hpp.daftree.ui.SubscriptionActivity;
import com.hpp.daftree.utils.AdsManager;
import com.hpp.daftree.utils.DialogHelper;
import com.hpp.daftree.utils.ReferralManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.viewmodels.DebtSummaryViewModel;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.recyclerview.widget.RecyclerView;

public class DebtSummaryActivity extends BaseActivity {

    private ActivityDebtSummaryBinding binding;
    private DebtSummaryViewModel viewModel;
    private UserDebtAdapter adapter;
    private boolean isPremium = false;
    private DaftreeRepository repository;
    private boolean isGuest;
    private String currentUserId = "";
    private static final int LOGIN_REQUEST_CODE = 1001;

    private AdsManager adsManager;
    private boolean isLinked = false, isDebtFeatureEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        if (isGuest) {

            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.putExtra("DEBUT_GUEST", "true");
            startActivity(mainIntent);
            finish();
        } else {
            isLinked = SecureLicenseManager.getInstance(this).isLinkedAccount();
            isDebtFeatureEnabled = SecureLicenseManager.getInstance(this).isDebtFeatureEnabled();
            if (!isLinked) {
                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.putExtra("DEBUT_USER", "true");
                startActivity(mainIntent);
                finish();
            } else if (!isDebtFeatureEnabled) {
                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.putExtra("DEBUT_USER", "true");
                startActivity(mainIntent);
                finish();
            }

        }
        binding = ActivityDebtSummaryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        setupEdgeToEdgeInsets();

        repository = new DaftreeRepository(getApplication());
        isPremium = SecureLicenseManager.getInstance(this).isPremium();
        String guestUID = SecureLicenseManager.getInstance(this).guestUID();
        if (isGuest) {
            currentUserId = SecureLicenseManager.getInstance(this).guestUID();

        } else {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getUid() : AppAuth.getUserID(this);
        }

        viewModel = new ViewModelProvider(this).get(DebtSummaryViewModel.class);

        adsManager = new AdsManager(this);
        // إعداد الحالة الأولية: إخفاء كل شيء وإظهار التحميل فوراً قبل وصول أي بيانات
        binding.contentLayout.setVisibility(View.GONE);
        binding.emptyLayout.setVisibility(View.GONE);
        binding.errorLayout.setVisibility(View.GONE);
        binding.loadingLayout.setVisibility(View.VISIBLE);

        setupRecyclerView();
        observeViewModel();
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRetry.setOnClickListener(v -> loadData());
        binding.swipeRefresh.setOnRefreshListener(this::loadData);
        binding.tvLastSync.setText(getString(R.string.offline_last) + viewModel.getLastSyncTimeFormatted());


        loadData();
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
    private boolean isTrialValid() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        long trialExpire = prefs.getLong("trial_expire", 0);
        if (trialExpire == 0) return false;
        return System.currentTimeMillis() < trialExpire;
    }
    boolean refreshData() {
        if(isPremium) return true;
        return isTrialValid() && !isPremium;
    }
    private void loadData() {
        viewModel.refreshData(refreshData());
    }

    private void setupRecyclerView() {
        adapter = new UserDebtAdapter((user, account, currency) -> {
            Intent intent = new Intent(this, DebtDetailsActivity.class);
            intent.putExtra("ACCOUNT_ID", account.accountId);
            intent.putExtra("CURRENCY_ID", currency.currencyFirestoreId);
            intent.putExtra("OWNER_NAME", user.ownerName);
            intent.putExtra("ACCOUNT_NAME", account.accountName);
            intent.putExtra("CURRENCY_NAME", currency.currencyName);
            startActivity(intent);
        });

        binding.rvUserDebts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvUserDebts.setAdapter(adapter);
        binding.swipeRefresh.setDistanceToTriggerSync(500);

        // 2. منع الـ SwipeRefresh من العمل أثناء التمرير داخل القائمة
        binding.rvUserDebts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // يتم تفعيل SwipeRefresh فقط إذا كان الـ RecyclerView في القمة تماماً (Vertical Scroll = 0)
                // دالة canScrollVertically(-1) تعيد false إذا كان لا يمكن التمرير للأعلى (أي في البداية)
                boolean isAtTop = !recyclerView.canScrollVertically(-1);
                binding.swipeRefresh.setEnabled(isAtTop);
            }
        });
    }

    private void observeViewModel() {
        viewModel.getShowLimitDialogEvent().observe(this, show -> {
            if (show) {
                // إيقاف مؤشر التحميل فوراً
                binding.swipeRefresh.setRefreshing(false);
                // إظهار الديالوج
                showNoOperationsDialog();
            }
        });
        // 1. مراقبة حالة التحميل (المايسترو الذي يتحكم في الظهور)
//        viewModel.getIsLoading().observe(this, isLoading -> {
//            // نوقف دوران SwipeRefresh لأننا نستخدم loadingLayout الخاص بنا
//            binding.swipeRefresh.setRefreshing(false);
//
//            // تحديث الواجهة بناءً على حالة التحميل
//            updateUIState(isLoading);
//        });
        viewModel.getIsLoading().observe(this, isLoading -> {
              if (adapter.getItemCount() > 0) {
                binding.swipeRefresh.setRefreshing(isLoading);
                binding.loadingLayout.setVisibility(View.GONE);
            } else {
                binding.swipeRefresh.setRefreshing(false);
                updateUIState(isLoading);
            }
        });

        viewModel.getSummaryList().observe(this, list -> {
            if (list != null) {
                adapter.submitList(list);
                calculateGrandTotalPerCurrency(list);
                binding.tvUserCount.setText(getString(R.string.summary_users_count, list.size()));
            }

            // تحديث حالة الواجهة (إظهار القائمة أو حالة "فارغ")
            if (!Boolean.TRUE.equals(viewModel.getIsLoading().getValue())) {
                updateUIState(false);
            }
        });

        // 3. مراقبة الأخطاء (التعديل الجوهري هنا)
        viewModel.getError().observe(this, errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                binding.swipeRefresh.setRefreshing(false);
                binding.loadingLayout.setVisibility(View.GONE);

                if (adapter.getItemCount() > 0) {
                    // إذا كان لدينا بيانات محليا، نظهرها ونكتفي برسالة تنبيه (Toast)
                    binding.contentLayout.setVisibility(View.VISIBLE);
                    binding.errorLayout.setVisibility(View.GONE);
                   // Toast.makeText(this, getString(R.string.error_showing_local_data) + ": " + errorMsg, Toast.LENGTH_LONG).show();
                } else {
                    // إذا لم توجد بيانات نهائياً، نظهر شاشة الخطأ
                    binding.errorLayout.setVisibility(View.VISIBLE);
                    binding.contentLayout.setVisibility(View.GONE);
                    binding.tvErrorMessage.setText(errorMsg);
                }
            }
        });

        // 4. مراقبة حالة الأوفلاين
        viewModel.getIsOfflineMode().observe(this, isOffline -> {
            binding.tvOfflineBanner.setVisibility(isOffline ? View.VISIBLE : View.GONE);
            // إذا كنا أوفلاين، التحميل سيتوقف فوراً، لذا updateUIState سيتكفل بالباقي
        });

        viewModel.getSyncMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }

    // دالة مركزية للتحكم في الظهور والاختفاء
    private void updateUIState(boolean isLoading) {
        if (isLoading) {
            // إذا لم يكن هناك بيانات، نظهر شاشة التحميل الكاملة
            if (adapter.getItemCount() == 0) {
                binding.loadingLayout.setVisibility(View.VISIBLE);
                binding.contentLayout.setVisibility(View.GONE);
                binding.emptyLayout.setVisibility(View.GONE);
                binding.errorLayout.setVisibility(View.GONE);
            }
        } else {
            binding.loadingLayout.setVisibility(View.GONE);
            if (adapter.getItemCount() > 0) {
                binding.contentLayout.setVisibility(View.VISIBLE);
                binding.emptyLayout.setVisibility(View.GONE);
                binding.errorLayout.setVisibility(View.GONE);
            } else {
                binding.contentLayout.setVisibility(View.GONE);
                binding.emptyLayout.setVisibility(View.VISIBLE);
                binding.errorLayout.setVisibility(View.GONE);
                // تفريغ الهيدر
                binding.llTotalsContainer.removeAllViews();
            }
        }
    }
    private void calculateGrandTotalPerCurrency(List<UserDebtSummary> list) {
        Map<String, CurrencySummary> totalsMap = new HashMap<>();

        for (UserDebtSummary user : list) {
            for (AccountDebtSummary acc : user.accounts) {
                for (CurrencySummary curr : acc.currencies) {
                    String key = curr.currencyName;
                    if (key == null) key = getString(R.string.not_specified);

                    if (!totalsMap.containsKey(key)) {
                        CurrencySummary s = new CurrencySummary();
                        s.currencyName = key;
                        totalsMap.put(key, s);
                    }
                    CurrencySummary total = totalsMap.get(key);
                    total.totalDebit += curr.totalDebit;
                    total.totalCredit += curr.totalCredit;
                    total.netBalance += curr.netBalance;
                }
            }
        }

        binding.llTotalsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int rowIndex = 0;

        for (CurrencySummary total : totalsMap.values()) {
            View rowView = inflater.inflate(R.layout.item_grand_total_row, binding.llTotalsContainer, false);

            TextView tvName = rowView.findViewById(R.id.tvCurrencyName);
            TextView tvDebit = rowView.findViewById(R.id.tvTotalDebit);
            TextView tvCredit = rowView.findViewById(R.id.tvTotalCredit);
            TextView tvNet = rowView.findViewById(R.id.tvNetBalance);

            tvName.setText(total.currencyName);
            tvDebit.setText(formatCurrency(total.totalDebit));
            tvCredit.setText(formatCurrency(total.totalCredit));
            tvNet.setText(formatCurrency(Math.abs(total.netBalance)));

            if (total.netBalance > 0) {
                tvNet.setTextColor(getColor(R.color.colorDebit));
            } else if (total.netBalance < 0) {
                tvNet.setTextColor(getColor(R.color.colorCredit));
            } else {
                tvNet.setTextColor(getColor(android.R.color.black));
            }

            if (rowIndex % 2 == 0) {
                rowView.setBackgroundColor(ContextCompat.getColor(this, R.color.striped_row_background));
            } else {
                rowView.setBackgroundColor(Color.TRANSPARENT);
            }

            binding.llTotalsContainer.addView(rowView);
            rowIndex++;
        }
    }

    private void showNoOperationsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_no_operations, null); // تأكد من وجود ملف الـ Layout هذا
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();


        TextView titelTextView = dialogView.findViewById(R.id.titel_textView);
        TextView noTransactionTextView = dialogView.findViewById(R.id.no_transaction_textView);
        MaterialButton purchaseBtn = dialogView.findViewById(R.id.btn_purchase);
        MaterialButton inviteBtn = dialogView.findViewById(R.id.btn_invite);
        MaterialButton watchBtn = dialogView.findViewById(R.id.btn_watch_ad);
        MaterialButton cancelBtn = dialogView.findViewById(R.id.btn_cancel);
        titelTextView.setText(getString(R.string.sync_limit_reached_title));
        noTransactionTextView.setText(getString(R.string.sync_limit_reached_msg1));
        purchaseBtn.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(this, SubscriptionActivity.class);
            startActivity(intent);
//            handlePurchaseApp();
        });
        inviteBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (isGuest) {
                Toast.makeText(this, getString(R.string.login_1), Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentUserId != null && !currentUserId.isEmpty()) {
                ReferralManager referralManager = new ReferralManager(this);
                referralManager.generateAndShareReferralLink(FirebaseAuth.getInstance().getCurrentUser());
            }
        });
        watchBtn.setOnClickListener(v -> {
            dialog.dismiss();
            watchAd();

        });
        dialog.show();

        if (dialog.getWindow() != null) {
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;

            dialog.getWindow().setAttributes(lp);
        }
    }


    private void watchAd() {

        // 1. إظهار البروجرس
        binding.progressBar.setVisibility(View.VISIBLE);
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                // إذا وصلنا هنا، يعني أن 10 ثوانٍ مرت ولم يظهر الإعلان
                binding.progressBar.setVisibility(View.GONE);
                DialogHelper.show(DebtSummaryActivity.this,
                        getString(R.string.ad_not_ready_message),
                        DialogHelper.FeedbackType.WARNING_2, null);
                Log.e("AdsTimeout", "تم تجاوز المهلة الزمنية (10 ثوانٍ) دون استجابة من AdsManager");
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, 10000);
        // 2. طلب الإعلان
        adsManager.showRewardedAd(this, new AdsManager.AdListener() {
            @Override
            public void onAdLoaded() {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                binding.progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onAdFailedToLoad(String error) {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                binding.progressBar.setVisibility(View.GONE);
                // Toast.makeText(MainActivity.this, getString(R.string.setupListeners_02), Toast.LENGTH_LONG).show();
                DialogHelper.show(DebtSummaryActivity.this, getString(R.string.setupListeners_02), DialogHelper.FeedbackType.ERROR, null);

            }

            @Override
            public void onPointsEarned(int amount) {
                updatePointsInFirestore(amount);
            }

            @Override
            public void onAdClosed() {
                timeoutHandler.removeCallbacks(timeoutRunnable);
            }
        });
    }

    private void updatePointsInFirestore(int points) {
        SecureLicenseManager.getInstance(this)
                .setAdRewards(SecureLicenseManager.getInstance(this).getAdRewards() + points);
        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        String userDoc = "", userUID;
        if (isGuest) {
            userUID = SecureLicenseManager.getInstance(this).guestUID();
            userDoc = "guests";
        } else {
            userUID=AppAuth.getUserID(this);
            userDoc = "users";
        }
        assert userUID != null;
        Map<String, Object> updates = new HashMap<>();
        updates.put("ad_rewards", FieldValue.increment(points));
        updates.put("lastModified", System.currentTimeMillis());
        FirebaseFirestore.getInstance().collection(userDoc).document(userUID)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, getString(R.string.updatePointsInFirestore_10) + points + getString(R.string.updatePointsInFirestore_11), Toast.LENGTH_SHORT).show();

                })
                .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.updatePointsInFirestore_12), Toast.LENGTH_SHORT).show());
    }

    private String formatCurrency(double amount) {
        return NumberFormat.getNumberInstance(Locale.US).format(amount);
    }
}
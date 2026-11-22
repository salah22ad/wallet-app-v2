package com.hpp.daftree.ui;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hpp.daftree.MainActivity;
import com.hpp.daftree.viewmodels.MainViewModel;
import com.hpp.daftree.adapters.AccountsAdapter;
import com.hpp.daftree.databinding.FragmentAccountListBinding;
import com.hpp.daftree.models.DaftreeRepository;

public class AccountListFragment extends Fragment {

    private static final String ARG_ACCOUNT_TYPE = "account_type";
    private FragmentAccountListBinding binding;
    private MainViewModel mainViewModel;
    private AccountsAdapter accountsAdapter;
    private DaftreeRepository daftreeRepository;

    public static AccountListFragment newInstance(@Nullable String accountType) {
        AccountListFragment fragment = new AccountListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ACCOUNT_TYPE, accountType); // null يعني "كل الحسابات"
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAccountListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // **مهم:** استخدام ViewModel الخاص بالـ Activity لتبقى البيانات متزامنة
        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        daftreeRepository = new DaftreeRepository(requireActivity().getApplication());
        setupRecyclerView();

        String accountType = getArguments() != null ? getArguments().getString(ARG_ACCOUNT_TYPE) : null;
        mainViewModel.setFilter(accountType); // ضبط الفلتر لهذا الـ Fragment

        // هذا المراقب سيراقب الحسابات ويعرضها
        mainViewModel.getAccounts().observe(getViewLifecycleOwner(), accounts -> {
            if (accountsAdapter != null) {
                accountsAdapter.submitList(accounts);
            }
        });

        // **وهذا المراقب سيراقب العملة ويقوم بتحديث الـ Adapter بها**
        mainViewModel.getCurrency().observe(getViewLifecycleOwner(), currency -> {
            if (accountsAdapter != null) {
                accountsAdapter.setCurrency(currency);
            }
        });
        // 🔥 تحديث عرض الحالة الفارغة بناءً على الحسابات والعمليات
//       try {
//           AppDatabase.databaseWriteExecutor.execute(() -> {
//               updateEmptyViewVisibility(daftreeRepository.getAccounts());
//           });
//       } catch (Exception e) {
//
//       }
//        requireActivity().runOnUiThread(() -> {
//
//            try {
//                AppDatabase.databaseWriteExecutor.execute(() -> {
//                    updateEmptyViewVisibility(daftreeRepository.getAccounts());
//                });
//            } catch (Exception e) {
//
//            }
//        });
    }
    private void setupRecyclerView() {
        accountsAdapter = new AccountsAdapter((AccountsAdapter.OnAccountInteractionListener) requireActivity());

        // تحديد تخطيط الشبكة بناءً على حجم الشاشة والاتجاه
        RecyclerView.LayoutManager layoutManager = createAppropriateLayoutManager();
        binding.accountsRecyclerView.setLayoutManager(layoutManager);
        binding.accountsRecyclerView.setAdapter(accountsAdapter);

        // إضافة مستمع لمعرفة متى يبدأ وينتهي التمرير في RecyclerView
        binding.accountsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (getActivity() instanceof MainActivity) {
                    MainActivity mainActivity = (MainActivity) getActivity();
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        mainActivity.setRecyclerViewScrolling(true);
                    } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        mainActivity.setRecyclerViewScrolling(false);
                    }
                }
            }
        });
    }

    /**
     * إنشاء LayoutManager مناسب بناءً على حجم الشاشة والاتجاه
     */
    private RecyclerView.LayoutManager createAppropriateLayoutManager() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float screenWidthDp = displayMetrics.widthPixels / displayMetrics.density;

        // التحقق من اتجاه الشاشة
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape || screenWidthDp >= 600) {
            // في الوضع الأفقي أو الشاشات الكبيرة، استخدام GridLayoutManager
            int spanCount = calculateSpanCount(screenWidthDp, isLandscape);
            return new GridLayoutManager(getContext(), spanCount);
        } else {
            // في الوضع العمودي للهواتف، استخدام LinearLayoutManager
            return new LinearLayoutManager(getContext());
        }
    }

    /**
     * حساب عدد الأعمدة بناءً على حجم الشاشة والاتجاه
     */
    private int calculateSpanCount(float screenWidthDp, boolean isLandscape) {
        if (screenWidthDp >= 1200) {
            return 4; // شاشات كبيرة جداً
        } else if (screenWidthDp >= 720) {
            return 3; // أجهزة لوحية
        } else if (isLandscape) {
            return 3; // هواتف أفقية
        } else {
            return 1; // هواتف عمودية
        }
    }

    // إضافة مستمع لتغير الإعدادات (بما في ذلك اتجاه الشاشة)
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // إعادة إنشاء الـ LayoutManager عند تغيير اتجاه الشاشة
        recreateLayoutManager();
    }

    /**
     * إعادة إنشاء الـ LayoutManager مع الحفاظ على موضع التمرير
     */
    private void recreateLayoutManager() {
        if (binding != null && binding.accountsRecyclerView != null) {
            // حفظ موضع التمرير الحالي
            int scrollPosition = 0;
            RecyclerView.LayoutManager layoutManager = binding.accountsRecyclerView.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                scrollPosition = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
            } else if (layoutManager instanceof GridLayoutManager) {
                scrollPosition = ((GridLayoutManager) layoutManager).findFirstVisibleItemPosition();
            }

            // إنشاء الـ LayoutManager الجديد
            RecyclerView.LayoutManager newLayoutManager = createAppropriateLayoutManager();
            binding.accountsRecyclerView.setLayoutManager(newLayoutManager);

            // استعادة موضع التمرير
            if (newLayoutManager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) newLayoutManager).scrollToPosition(scrollPosition);
            } else if (newLayoutManager instanceof GridLayoutManager) {
                ((GridLayoutManager) newLayoutManager).scrollToPosition(scrollPosition);
            }
        }
    }
    /**
     * دالة عامة لإعادة إنشاء الـ LayoutManager (يمكن استدعاؤها من MainActivity)
     */
    public void recreateLayoutManagerForMainActivity() {
        if (getActivity() != null && isAdded()) {
            getActivity().runOnUiThread(this::recreateLayoutManager);
        }
    }
    private void setupRecyclerView1() {
        // تمرير الـ Activity كـ Listener
        accountsAdapter = new AccountsAdapter((AccountsAdapter.OnAccountInteractionListener) requireActivity());
        binding.accountsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.accountsRecyclerView.setAdapter(accountsAdapter);

        // إضافة مستمع لمعرفة متى يبدأ وينتهي التمرير في RecyclerView
        binding.accountsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                // إعلام MainActivity بحالة التمرير
                if (getActivity() instanceof MainActivity) {
                    MainActivity mainActivity = (MainActivity) getActivity();
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        mainActivity.setRecyclerViewScrolling(true);
                    } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        mainActivity.setRecyclerViewScrolling(false);
                    }
                }
            }
        });
    }

    /**
     * 🔥 تحديث عرض الحالة الفارغة بناءً على الحسابات والعمليات
     */
    private void updateEmptyViewVisibility(java.util.List<com.hpp.daftree.database.Account> accounts) {
        try {
            boolean shouldShowEmptyView = shouldShowEmptyView(accounts);

            if (binding != null && binding.emptyViewText != null) {
                binding.emptyViewText.setVisibility(shouldShowEmptyView ? View.VISIBLE : View.GONE);
            }
        } catch (Exception e) {

        }
    }

    /**
     * 🔥 تحديد ما إذا كان يجب عرض الحالة الفارغة
     * تُعرض عندما: لا توجد حسابات أو لا توجد عمليات في الحسابات
     */
    private boolean shouldShowEmptyView(java.util.List<com.hpp.daftree.database.Account> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return true; // لا توجد حسابات
        }

        // فحص جميع الحسابات للبحث عن عمليات
        for (com.hpp.daftree.database.Account account : accounts) {
            if (account != null && daftreeRepository.getRecentTransactionsCount() > 0) {
                return false; // يوجد حساب واحد على الأقل مع عمليات
            }
        }

        return true; // جميع الحسابات لا تحتوي على عمليات
    }
}
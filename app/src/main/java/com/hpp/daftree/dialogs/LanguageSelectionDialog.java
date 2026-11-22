package com.hpp.daftree.dialogs;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;
import com.google.android.play.core.splitinstall.SplitInstallRequest;
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;
import com.hpp.daftree.BuildConfig;
import com.hpp.daftree.R;
import com.hpp.daftree.helpers.LanguageManager;
import com.hpp.daftree.models.LanguageItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LanguageSelectionDialog extends BottomSheetDialogFragment {

    private static final String TAG = "LangSelectionDialog";
    private RecyclerView rvLanguages;
    private LanguageSelectionListener listener;
    private EditText etSearch;
    private LanguageAdapter adapter;
    private List<LanguageItem> allLanguages;
    private String currentLangCode;
    private SplitInstallManager splitInstallManager;
    public interface LanguageSelectionListener {
        void onLanguageSelected(String languageCode);
        void onLanguageSelectionCancelled();
    }
    public void setLanguageSelectionListener(LanguageSelectionListener listener) {
        this.listener = listener;
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_language_bottom_sheet, container, false);

        rvLanguages = view.findViewById(R.id.recycler_languages);
        etSearch = view.findViewById(R.id.et_search_language);

        currentLangCode = LanguageManager.getInstance(requireContext()).getCurrentLanguage();
        allLanguages = LanguageManager.getInstance(requireContext()).getSupportedLanguageItems();

        rvLanguages.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new LanguageAdapter(allLanguages, currentLangCode, this::onLanguageSelected);
        adapter = new LanguageAdapter(allLanguages, currentLangCode, code -> {
            // عند الضغط على لغة، نتأكد أولاً أنها مثبتة قبل التطبيق
            if (!BuildConfig.DEBUG) {
                if (isLanguageInstalled(code)) {
                    if (listener != null) {
                        listener.onLanguageSelected(code);
                    }
                    dismiss();
                } else {
                    // نظرياً الكود بالأسفل سيقوم بتحميل الكل، لكن هذا فحص إضافي للأمان
                    //  Toast.makeText(getContext(), getString(R.string.downloading_language_please_wait), Toast.LENGTH_SHORT).show();
                    downloadAndInstallLanguage(code);
                }
            }else{
                if (listener != null) {
                    listener.onLanguageSelected(code);
                }
                dismiss();
            }
        });

        rvLanguages.setAdapter(adapter);

        splitInstallManager = SplitInstallManagerFactory.create(requireContext());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        return view;
    }
    private boolean isLanguageInstalled(String langCode) {
        return splitInstallManager.getInstalledLanguages().contains(langCode);
    }
    private void onLanguageSelected(String langCode) {
        if (langCode.equals(currentLangCode)) {
            dismiss();
            return;
        }

        if (splitInstallManager.getInstalledModules().contains("lang_" + langCode) || langCode.equals("ar") || langCode.equals("en")) {
            applyLanguageAndRestart(langCode);
        } else {
            if (isNetworkAvailable()) {
                downloadAndInstallLanguage(langCode);
            } else {
                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.warning_title)) // تأكد من وجود سترينج مناسب
                        .setMessage(getString(R.string.no_internet_language_warning)) // أضف رسالة: "لا يوجد اتصال بالإنترنت. لن تظهر نصوص باقي اللغات حتى يتم الاتصال."
                        .setPositiveButton(getString(R.string.ok), null)
                        .show();

            }
        }
    }

    private void downloadAndInstallLanguage(String langCode) {
        // --- نظام حماية: التأكد من أن الواجهة موجودة قبل البدء ---
        if (!isAdded() || getContext() == null || (getActivity() != null && getActivity().isFinishing())) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_loading, null);
        TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        tvMessage.setText(R.string.downloading_languages_resources);
        builder.setView(dialogView);
        builder.setCancelable(false);

        AlertDialog progressDialog = builder.create();

        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // إظهار الديالوج بأمان
        if (!getActivity().isFinishing()) {
            progressDialog.show();
        }
        // -----------------------------------------------------

        SplitInstallRequest request = SplitInstallRequest.newBuilder()
                .addLanguage(Locale.forLanguageTag(langCode))
                .build();

        SplitInstallStateUpdatedListener listener = new SplitInstallStateUpdatedListener() {
            @Override
            public void onStateUpdate(com.google.android.play.core.splitinstall.SplitInstallSessionState state) {
                if (state.status() == SplitInstallSessionStatus.INSTALLED) {
                    safeDismissDialog(progressDialog);
                    applyLanguageAndRestart(langCode);
                } else if (state.status() == SplitInstallSessionStatus.FAILED) {
                    safeDismissDialog(progressDialog);
                    // فحص أمان إضافي قبل إظهار الـ Toast
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(getContext(), R.string.error_downloading_language1, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };

        splitInstallManager.registerListener(listener);
        splitInstallManager.startInstall(request)
                .addOnSuccessListener(sessionId -> monitorDownloadProgress(sessionId, progressDialog))
                .addOnFailureListener(e -> {
                    safeDismissDialog(progressDialog);
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(getContext(), R.string.error_starting_download1, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void monitorDownloadProgress(int sessionId, AlertDialog progressDialog) {
        splitInstallManager.registerListener(state -> {
            if (state.sessionId() == sessionId) {
                if (state.status() == SplitInstallSessionStatus.INSTALLED || state.status() == SplitInstallSessionStatus.FAILED) {
                    safeDismissDialog(progressDialog);
                }
            }
        });
    }

    private void safeDismissDialog(AlertDialog dialog) {
        // حماية قصوى عند إغلاق الديالوج
        try {
            if (dialog != null && dialog.isShowing()) {
                Context context = dialog.getContext();
                if (context instanceof android.app.Activity) {
                    if (!((android.app.Activity) context).isFinishing() && !((android.app.Activity) context).isDestroyed()) {
                        dialog.dismiss();
                    }
                } else {
                    dialog.dismiss();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Prevented crash during dismiss", e);
        }
    }

    private void applyLanguageAndRestart(String langCode) {
        if (!isAdded() || getContext() == null) return;

        LanguageManager.getInstance(requireContext()).setLocale(langCode);
        if (getActivity() != null) {
            getActivity().recreate();
        }
        dismiss();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    // ... باقي كود الـ Adapter يبقى كما هو دون تغيير لضمان الاستقرار ...
    static class LanguageAdapter extends RecyclerView.Adapter<LanguageAdapter.ViewHolder> {
        private final List<LanguageItem> originalList;
        private List<LanguageItem> displayedList;
        private final String currentLangCode;
        private final OnItemClickListener itemClickListener;

        interface OnItemClickListener {
            void onItemClick(String langCode);
        }

        LanguageAdapter(List<LanguageItem> languages, String currentLangCode, OnItemClickListener listener) {
            this.originalList = languages;
            this.displayedList = new ArrayList<>(languages);
            this.currentLangCode = currentLangCode;
            this.itemClickListener = listener;
        }

        void filter(String query) {
            displayedList = new ArrayList<>();
            for (LanguageItem item : originalList) {
                if (item.getName().toLowerCase().contains(query.toLowerCase()) ||
                        item.getNativeName().toLowerCase().contains(query.toLowerCase())) {
                    displayedList.add(item);
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_language_row, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LanguageItem item = displayedList.get(position);
            holder.tvNativeName.setText(item.getNativeName());
            holder.tvFlag.setText(item.getFlag());

            String currentAppLang = LanguageManager.getInstance(holder.itemView.getContext()).getCurrentLanguage();
            if (!item.getCode().equals(currentAppLang)) {
                holder.tvNameByAppLang.setText(item.getName());
                holder.tvNameByAppLang.setVisibility(View.VISIBLE);
            } else {
                holder.tvNameByAppLang.setVisibility(View.GONE);
            }

            boolean isAppRTL = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;
            holder.llsLang.setLayoutDirection(isAppRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

            holder.rbSelected.setChecked(item.getCode().equals(currentLangCode));

            holder.itemView.setOnClickListener(v -> itemClickListener.onItemClick(item.getCode()));
            holder.rbSelected.setOnClickListener(v -> itemClickListener.onItemClick(item.getCode()));
        }

        @Override
        public int getItemCount() { return displayedList.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvNativeName, tvFlag, tvNameByAppLang;
            RadioButton rbSelected;
            LinearLayout llsLang;

            ViewHolder(View itemView) {
                super(itemView);
                tvNativeName = itemView.findViewById(R.id.tv_native_name);
                tvFlag = itemView.findViewById(R.id.tv_flag);
                rbSelected = itemView.findViewById(R.id.rb_selected);
                tvNameByAppLang = itemView.findViewById(R.id.tvNameByAppLang);
                llsLang = itemView.findViewById(R.id.llsLang);
            }
        }
    }
}
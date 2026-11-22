package com.hpp.daftree.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hpp.daftree.R;
import com.hpp.daftree.database.RecurringOperation;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecurringOperationsAdapter extends RecyclerView.Adapter<RecurringOperationsAdapter.ViewHolder> {

    public interface OnRecurringOperationListener {
        void onEdit(RecurringOperation operation);
        void onDelete(RecurringOperation operation);
    }

    private List<RecurringOperation> operations = new ArrayList<>();
    private final OnRecurringOperationListener listener;

    public RecurringOperationsAdapter(OnRecurringOperationListener listener) {
        this.listener = listener;
    }

    public void submitList(List<RecurringOperation> list) {
        operations = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recurring_operation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecurringOperation op = operations.get(position);
        holder.tvAccountName.setText(op.getAccountName());
        holder.tvAmount.setText(NumberFormat.getNumberInstance(Locale.US).format(op.getAmount()));

        String recurrence = "";
        switch (op.getRecurrenceType()) {
            case "day": recurrence = holder.itemView.getContext().getString(R.string.daily); break;
            case "week": recurrence = holder.itemView.getContext().getString(R.string.weekly); break;
            case "month": recurrence = holder.itemView.getContext().getString(R.string.monthly); break;
        }
        holder.tvRecurrence.setText(recurrence);

        holder.btnEdit.setOnClickListener(v -> listener.onEdit(op));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(op));
    }

    @Override
    public int getItemCount() {
        return operations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAccountName, tvAmount, tvRecurrence;
        ImageButton btnEdit, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAccountName = itemView.findViewById(R.id.tvAccountName);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvRecurrence = itemView.findViewById(R.id.tvRecurrence);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
package com.contacts.app.phone.calls.gallerydemo.adapter;

import android.content.Context;
import android.graphics.Color;
import android.provider.CallLog;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.databinding.ItemCallHistoryBinding;
import com.contacts.app.phone.calls.gallerydemo.model.CallLogItem;
import com.google.firebase.crashlytics.buildtools.reloc.javax.annotation.Nonnull;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("all")

public class CallHistoryAdapter extends ListAdapter<CallLogItem, CallHistoryAdapter.CallViewHolder> {

    /**
     * Interface to communicate click and long-press events back to CallHistoryFragment.
     */
    public interface OnCallItemListener{
        void onItemClicked(@NonNull CallLogItem item);
        void onItemLongClicked(@NonNull CallLogItem item);
        void onIconClicked(@NonNull CallLogItem item);
        void onIconLongClicked(@NonNull CallLogItem item);

    }
    private final OnCallItemListener listener;
    private final Set<Long> selectedIds = new HashSet<>();
    private boolean isSelectionMode = false;

    // DiffUtil calculates the exact difference between two lists so animations are smooth
    private static final DiffUtil.ItemCallback<CallLogItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CallLogItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull CallLogItem oldItem, @NonNull CallLogItem newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull CallLogItem oldItem, @NonNull CallLogItem newItem) {
                    return oldItem.equals(newItem);
                }
            };

// For Multiple Selection
    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public boolean areAllSelected() {
        return !getCurrentList().isEmpty() && selectedIds.size() == getCurrentList().size();
    }
    public void setSelectionMode(boolean selectionMode) {
        if (this.isSelectionMode != selectionMode) {
            this.isSelectionMode = selectionMode;
            if (!selectionMode) {
                selectedIds.clear();
            }
            notifyDataSetChanged();
        }
    }
    public void toggleSelection(long id) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        for (CallLogItem item : getCurrentList()) {
            selectedIds.add(item.getId());
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
    }

    public Set<Long> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    public int getSelectedItemCount() {
        return selectedIds.size();
    }




    public CallHistoryAdapter(OnCallItemListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemCallHistoryBinding binding = ItemCallHistoryBinding.inflate(inflater, parent, false);
        return new CallViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        CallLogItem item = getItem(position);
        boolean isSelected = selectedIds.contains(item.getId());
        holder.bind(item, listener, isSelectionMode, isSelected);
    }

    /**
     * ViewHolder holds references to the view widgets for one row in the list.
     */

    public class CallViewHolder extends RecyclerView.ViewHolder {
        private final ItemCallHistoryBinding binding;

        public CallViewHolder(@Nonnull ItemCallHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(@NonNull CallLogItem item,
                         OnCallItemListener listener,
                         boolean isSelectionMode,
                         boolean isSelected) {
            Context context = itemView.getContext();

            // 1. Caller Name: if name is null/empty, display the phone number instead
            String displayName = item.getName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = item.getNumber().isEmpty() ? "Unknown" : item.getNumber();
            }
            binding.textCallerName.setText(displayName);

            // 2. Subtitle: Format call type text, phone number, and duration
            String typeText = getCallTypeText(item.getType());
            String durationText = formatDuration(item.getDuration());
            String details = String.format(Locale.getDefault(), "%s • %s • %s",
                    typeText, item.getNumber(), durationText);
            binding.textCallDetails.setText(details);

            // 3. Date: relative time like "Yesterday", "2 hours ago", or "Oct 28"
            CharSequence relativeDate = DateUtils.getRelativeDateTimeString(
                    context,
                    item.getDate(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.WEEK_IN_MILLIS,
                    0
            );
            binding.textCallDate.setText(relativeDate);

            // 4. Set Call Type Icon and tint color based on call type
            bindCallTypeIcon(item.getType());

             // 5. SELECTION MODE UI:
            if (isSelectionMode) {
                // In selection mode: tint row background if selected
                itemView.setBackgroundColor(isSelected
                        ? ContextCompat.getColor(context, R.color.brand_primary_container)
                        : android.graphics.Color.TRANSPARENT);
            } else {
                itemView.setBackgroundResource(0);
            }


            // 6. SEPARATED TOUCH LISTENERS:
            // A. The Call Type Icon:
            binding.imageCallType.setClickable(true);
            binding.imageCallType.setFocusable(true);
            binding.imageCallType.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onIconClicked(item);
                }
            });
            binding.imageCallType.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onIconLongClicked(item);
                    return true; // CONSUMED: Do NOT trigger the parent row's long-click!
                }
                return false;
            });

            // B. The Rest of the Row (itemView):
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClicked(item);
                }
            });
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onItemLongClicked(item);
                    return true; // CONSUMED
                }
                return false;
            });
        }

        private void bindCallTypeIcon(int type) {
            Context context = itemView.getContext();
            switch (type) {
                case CallLog.Calls.OUTGOING_TYPE:
                    binding.imageCallType.setImageResource(R.drawable.ic_call_outgoing);
                    binding.imageCallType.setColorFilter(ContextCompat.getColor(context,
                            android.R.color.holo_green_dark));
                    break;
                case CallLog.Calls.MISSED_TYPE:
                case CallLog.Calls.REJECTED_TYPE:
                    binding.imageCallType.setImageResource(R.drawable.ic_call_missed);
                    binding.imageCallType.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_red_dark));
                    break;
                case CallLog.Calls.INCOMING_TYPE:
                default:
                    binding.imageCallType.setImageResource(R.drawable.ic_call_incoming);
                    binding.imageCallType.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_blue_dark));
                    break;
            }
        }
        private String getCallTypeText(int type) {
            switch (type) {
                case CallLog.Calls.OUTGOING_TYPE:
                    return "Outgoing";
                case CallLog.Calls.MISSED_TYPE:
                    return "Missed";
                case CallLog.Calls.REJECTED_TYPE:
                    return "Rejected";
                case CallLog.Calls.INCOMING_TYPE:
                default:
                    return "Incoming";
            }
        }
        private String formatDuration(long seconds) {
            if (seconds <= 0) {
                return "0s";
            }
            long minutes = seconds / 60;
            long remSeconds = seconds % 60;
            if (minutes > 0) {
                return String.format(Locale.getDefault(), "%dm %ds", minutes, remSeconds);
            }
            return String.format(Locale.getDefault(), "%ds", remSeconds);
        }
    }
}

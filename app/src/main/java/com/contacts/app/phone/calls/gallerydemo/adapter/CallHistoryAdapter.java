package com.contacts.app.phone.calls.gallerydemo.adapter;

import static com.contacts.app.phone.calls.gallerydemo.util.MediaUtils.formatDuration;

import android.content.Context;
import android.provider.CallLog;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
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

import java.util.Locale;

public class CallHistoryAdapter extends ListAdapter<CallLogItem, CallHistoryAdapter.CallViewHolder> {

    /**
     * Interface to communicate click and long-press events back to CallHistoryFragment.
     */
    public interface OnCallItemListener{
        void onItemClicked(@NonNull CallLogItem item);
        void onItemLongClick(@NonNull CallLogItem item);

    }
    private final OnCallItemListener listener;

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
        holder.bind(item, listener);

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

        public void bind(@NonNull CallLogItem item, OnCallItemListener listener) {
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

            // 5. Clicks
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClicked(item);
                }
            });
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onItemLongClick(item);
                    return true; // true means we consumed the event (do not trigger normal click)
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

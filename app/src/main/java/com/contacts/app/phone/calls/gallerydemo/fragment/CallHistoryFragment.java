package com.contacts.app.phone.calls.gallerydemo.fragment;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.adapter.CallHistoryAdapter;
import com.contacts.app.phone.calls.gallerydemo.data.CallLogRepository;
import com.contacts.app.phone.calls.gallerydemo.databinding.DialogEditCallContactBinding;
import com.contacts.app.phone.calls.gallerydemo.databinding.FragmentCallHistoryBinding;
import com.contacts.app.phone.calls.gallerydemo.model.CallLogItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;



import java.util.List;
@SuppressWarnings("all")


public class CallHistoryFragment extends Fragment implements CallHistoryAdapter.OnCallItemListener{

    private FragmentCallHistoryBinding binding;
    private CallHistoryAdapter adapter;
    private CallLogRepository repository;
    @Nullable
    private AlertDialog progressDialog;
    @Nullable
    private @org.jspecify.annotations.Nullable ActionMode actionMode;


    public CallHistoryFragment() {
        // Required empty public constructor
    }

    private  static final String[] CALL_PERMISSIONS = {
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_CONTACTS
    };

    // New Code
    // Triggers when the user returns from saving/editing a contact in the Contacts app
    private final ActivityResultLauncher<String[]> callLogPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),result -> {
                        Boolean readCallLog  = result.get(Manifest.permission.READ_CALL_LOG);
                        Boolean readContacts  = result.get(Manifest.permission.READ_CONTACTS);

                        if (Boolean.TRUE.equals(readCallLog)) {
                            onPermissionGranted();
                        } else {
                            onPermissionDenied();
                        }

            });

    private final Handler observerHandler = new Handler(Looper.getMainLooper());
    private final ContentObserver contactChangeObserver  = new ContentObserver(observerHandler) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            // User came back from saving! Show the loading dialog
            showProgressDialog("Updating contact information...");
            // Changes detected! Automatically reload call records and contact names
            if (isAdded() && hasCallLogPermission()) {
                binding.recyclerViewCalls.postDelayed(() -> {
                    loadCallData();
                }, 1000);
            }
        }
    };

    private final androidx.appcompat.view.ActionMode.Callback actionModeCallback =
            new androidx.appcompat.view.ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(androidx.appcompat.view.ActionMode mode, android.view.Menu menu) {
                    mode.getMenuInflater().inflate(R.menu.menu_call_selection, menu);
                    adapter.setSelectionMode(true);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(androidx.appcompat.view.ActionMode mode, android.view.Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(androidx.appcompat.view.ActionMode mode, android.view.MenuItem item) {
                    int id = item.getItemId();
                    /*if (id == R.id.action_select_all) {
                        // If already all selected, clear all and exit selection mode!
                        adapter.clearSelection();
                        if (actionMode != null) {
                            actionMode.finish();
                        }
                    }else {
                        // Not all selected: select all of them!
                        adapter.selectAll();
                        updateActionModeTitle();
                    }
                    return true;*/
                    if (id == R.id.action_select_all) {
                        adapter.selectAll();
                        updateActionModeTitle();
                        return true;
                    } else if (id == R.id.action_delete_selected) {
                        confirmBatchDelete();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(androidx.appcompat.view.ActionMode mode) {
                    adapter.setSelectionMode(false);
                    actionMode = null;
                }
            };




    private void updateActionModeTitle() {
        if (actionMode != null) {
            int count = adapter.getSelectedItemCount();
            actionMode.setTitle(count + " selected");
            if (count == 0) {
                actionMode.finish(); // Exit selection mode if user unselects everything
            }
        }
    }

    private void confirmBatchDelete() {
        java.util.Set<Long> selectedIds = adapter.getSelectedIds();
        if (selectedIds.isEmpty()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Call Records")
                .setMessage("Are you sure you want to delete " + selectedIds.size() + " selected call records?")
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    performBatchDelete(selectedIds);
                })
                .show();
    }
    private void performBatchDelete(java.util.Set<Long> selectedIds) {
        if (!hasWriteCallLogPermission()) {
            Toast.makeText(requireContext(), "Write permission required to delete calls", Toast.LENGTH_SHORT).show();
            callLogPermissionLauncher.launch(CALL_PERMISSIONS);
            return;
        }
        showProgressDialog("Deleting " + selectedIds.size() + " records...");
        repository.deleteMultipleCallRecords(selectedIds, new CallLogRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                dismissProgressDialog();
                if (actionMode != null) {
                    actionMode.finish();
                }
                Toast.makeText(requireContext(), "Selected records deleted", Toast.LENGTH_SHORT).show();
                loadCallData();
            }
            @Override
            public void onError(@NonNull Exception error) {
                dismissProgressDialog();
                Toast.makeText(requireContext(), R.string.call_delete_failed, Toast.LENGTH_LONG).show();
            }
        });
    }






    private void showProgressDialog(String message) {
        if (!isAdded()) return;
        dismissProgressDialog(); // Dismiss any previous one
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_progress_loading, null);
        TextView messageView = view.findViewById(R.id.text_loading_message);
        if (messageView != null && message != null) {
            messageView.setText(message);
        }
        progressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .setCancelable(false) // User cannot accidentally dismiss by tapping outside while saving
                .create();
        progressDialog.show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the XML layout using ViewBinding
        binding = FragmentCallHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new CallLogRepository(requireContext());
        setupRecyclerView();
        // When the user clicks the "Grant Permission" button in the empty state
        binding.buttonPermission.setOnClickListener(v -> requestCallLogPermission());
        // Check if we already have permission or need to ask
        checkAndRequestPermission();
    }

    private void setupRecyclerView() {
        // 1. Create the adapter, passing 'this' as the click/long-click listener
        adapter = new CallHistoryAdapter(this);
        // 2. Set LinearLayoutManager (vertical scrolling list)
        binding.recyclerViewCalls.setLayoutManager(new LinearLayoutManager(requireContext()));
        // 3. Attach the adapter to the RecyclerView
        binding.recyclerViewCalls.setAdapter(adapter);
    }

    // ===== Permission Handling =====

    private void checkAndRequestPermission() {
        if (hasCallLogPermission()) {
            onPermissionGranted();
        } else {
            requestCallLogPermission();
        }
    }


    private boolean hasCallLogPermission() {
        boolean hasCallLog = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED;
        boolean hasContacts = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED;
        return hasCallLog && hasContacts;
    }

    private void requestCallLogPermission() {
        // If the user previously denied, shouldShowRequestPermissionRationale returns true
        callLogPermissionLauncher.launch(CALL_PERMISSIONS);
//            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG);
    }

    private void onPermissionGranted() {
        // Hide empty/denied state, hide button, show the list
        binding.layoutEmptyState.setVisibility(View.GONE);
        binding.buttonPermission.setVisibility(View.GONE);
        loadCallData();
//        binding.recyclerViewCalls.setVisibility(View.VISIBLE);
//        Toast.makeText(requireContext(), "Call log permission granted!", Toast.LENGTH_SHORT).show();
    }



    private void onPermissionDenied() {
        // Hide list, show empty state message and "Grant Permission" button
        binding.recyclerViewCalls.setVisibility(View.GONE);
        binding.progressLoading.setVisibility(View.GONE);
        binding.layoutEmptyState.setVisibility(View.VISIBLE);
        binding.textEmptyMessage.setText("Permission required to view call history");
        binding.buttonPermission.setVisibility(View.VISIBLE);

        // If permanently denied ("Don't ask again"), direct the user to device App Settings
        if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)) {
            binding.buttonPermission.setText("Open Settings");
            binding.buttonPermission.setOnClickListener(v -> openAppSettings());
        } else {
            binding.buttonPermission.setText("Grant Permission");
            binding.buttonPermission.setOnClickListener(v -> requestCallLogPermission());
        }
    }

    private boolean hasWriteCallLogPermission() {
        return ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    private void loadCallData() {
        if (binding == null) return;
        showProgressDialog("Updating contact information...");
//        binding.progressLoading.setVisibility(View.VISIBLE);

        repository.loadCallHistory(new CallLogRepository.LoadCallback() {
            @Override
            public void onLoaded(@NonNull List<CallLogItem> items) {
                dismissProgressDialog();
                if (binding == null) return;
                binding.progressLoading.setVisibility(View.GONE);
                if (items.isEmpty()) {
                    // No call records found on phone
                    binding.recyclerViewCalls.setVisibility(View.GONE);
                    binding.layoutEmptyState.setVisibility(View.VISIBLE);
                    binding.textEmptyMessage.setText("No call history found");
                    binding.buttonPermission.setVisibility(View.GONE);
                } else {
                    // Calls found! Show the list and feed the adapter
                    binding.layoutEmptyState.setVisibility(View.GONE);
                    binding.recyclerViewCalls.setVisibility(View.VISIBLE);
                    adapter.submitList(items);
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                dismissProgressDialog();
                if (binding == null) return;
                binding.progressLoading.setVisibility(View.GONE);
                binding.recyclerViewCalls.setVisibility(View.GONE);
                binding.layoutEmptyState.setVisibility(View.VISIBLE);
                binding.textEmptyMessage.setText("Failed to load call history");
                binding.buttonPermission.setVisibility(View.GONE);
            }
        });
    }

    // ===== CallHistoryAdapter.OnCallItemListener Callbacks =====

    @Override
    public void onItemClicked(@NonNull CallLogItem item) {
        if (adapter.isSelectionMode()) {
            // In selection mode: tapping anywhere on the row toggles selection
            toggleItemSelection(item.getId());
        } else {
            // Normal tap: preserve existing behavior
            String name = item.getName() != null ? item.getName() : item.getNumber();
            Toast.makeText(requireContext(), "Clicked: " + name, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onItemLongClicked(@NonNull CallLogItem item) {
        if (adapter.isSelectionMode()) {
            // If already in selection mode, long-pressing also toggles selection
            toggleItemSelection(item.getId());
        } else {
            // RESTORED: Long press on the row opens the Edit / Delete Dialog!
            showCallItemOptionsDialog(item);
        }
    }

    @Override
    public void onIconClicked(@NonNull CallLogItem item) {
        if (adapter.isSelectionMode()) {
            // In selection mode: tap icon toggles selection
            toggleItemSelection(item.getId());
        } else {
            // If not in selection mode, tapping the icon also starts selection mode!
            startSelectionMode(item.getId());
        }
    }
    @Override
    public void onIconLongClicked(@NonNull CallLogItem item) {
        // Long-pressing specifically on the icon enters Selection Mode!
        if (!adapter.isSelectionMode()) {
            startSelectionMode(item.getId());
        } else {
            toggleItemSelection(item.getId());
        }
    }

    private void startSelectionMode(long initialSelectedId) {
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        actionMode = activity.startSupportActionMode(actionModeCallback);
        toggleItemSelection(initialSelectedId);
    }
    private void toggleItemSelection(long id) {
        adapter.toggleSelection(id);
        updateActionModeTitle();
    }

    private void showCallItemOptionsDialog(@NonNull CallLogItem item) {
        String title = item.getName() != null && !item.getName().trim().isEmpty()
                ? item.getName() : item.getNumber();

        CharSequence[] options = new CharSequence[] {
                getString(R.string.action_edit),
                getString(R.string.action_delete)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // User chose "Edit"
                        onEditCallOptionSelected(item);
                    } else if (which == 1) {
                        // User chose "Delete"
                        onDeleteCallOptionSelected(item);
                    }

                })
                .show();
    }

    private void onEditCallOptionSelected(@NonNull CallLogItem item) {
        // 1. Inflate the custom dialog layout using our generated ViewBinding
        DialogEditCallContactBinding dialogBinding = DialogEditCallContactBinding.inflate(
                LayoutInflater.from(requireContext()));

        // 2. Pre-fill the input fields with existing data
        if (item.getName() != null && !item.getName().trim().isEmpty()) {
            dialogBinding.editTextName.setText(item.getName());
        }
        dialogBinding.editTextNumber.setText(item.getNumber());

        // 3. Build the Material Dialog
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Contact Information")
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton("Save Contact", (dialog, which) -> {
                    String updatedName = dialogBinding.editTextName.getText() != null
                            ? dialogBinding.editTextName.getText().toString().trim()
                            : "";
                    String updatedNumber = dialogBinding.editTextNumber.getText() != null
                            ? dialogBinding.editTextNumber.getText().toString().trim()
                            : "";

                    // Validation: phone number cannot be blank
                    if (updatedNumber.isEmpty()) {
                        Toast.makeText(requireContext(), "Phone number cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Save the contact into Android's Contact system
                    saveContactInfo(item, updatedName, updatedNumber);
                })
                .show();
    }

    private void saveContactInfo(@NonNull CallLogItem item,String newName, String newNumber) {
        Intent intent;

        if (item.getLookupUri() != null) {
            // This prevents duplicate creation!
            Uri contactUri = Uri.parse(item.getLookupUri());
            intent = new Intent(Intent.ACTION_EDIT);
            intent.setDataAndType(contactUri, ContactsContract.Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra(ContactsContract.Intents.Insert.NAME, newName);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, newNumber);
        } else {
            // SCENARIO 2: Unknown caller / New contact!
            intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra(ContactsContract.Intents.Insert.NAME, newName);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, newNumber);
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "No contacts app found to handle this action",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // 2nd delete dialog box
    private void onDeleteCallOptionSelected(@NonNull CallLogItem item) {
        String displayName = item.getName() != null && !item.getName().trim().isEmpty()
                ? item.getName()
                : item.getNumber();
        String message = getString(R.string.delete_call_message, displayName);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_call_title)
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null) // 'null' dismisses the dialog safely
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    // User confirmed deletion!
                    performDeleteCall(item);
                })
                .show();
    }
    private void performDeleteCall(@NonNull CallLogItem item) {
        if (!hasWriteCallLogPermission()) {
            Toast.makeText(requireContext(), "Write permission required to delete calls", Toast.LENGTH_SHORT).show();
            callLogPermissionLauncher.launch(CALL_PERMISSIONS);
            return;
        }

        if (binding == null) return;
        // Show spinner while deleting
//        binding.progressLoading.setVisibility(View.VISIBLE);
        repository.deleteCallRecord(item.getId(), new CallLogRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                if (binding == null) return;
                binding.progressLoading.setVisibility(View.GONE);
                Toast.makeText(requireContext(), R.string.call_deleted_success, Toast.LENGTH_SHORT).show();
                // Reload the updated list from the database
                loadCallData();
            }
            @Override
            public void onError(@NonNull Exception error) {
                if (binding == null) return;
                binding.progressLoading.setVisibility(View.GONE);
                Log.e("CallHistory", "Delete failed reason: ", error);
                Toast.makeText(requireContext(), R.string.call_delete_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            requireContext().getContentResolver().registerContentObserver(
                    ContactsContract.AUTHORITY_URI,
                    true, // true means notify for all descendant paths (sub-folders)
                    contactChangeObserver
            );
            requireContext().getContentResolver().registerContentObserver(
                    CallLog.CONTENT_URI,
                    true,
                    contactChangeObserver
            );
        } catch (Exception e) {
            // In case permissions were revoked while starting
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // Always unregister when not visible to prevent battery drain and memory leaks
        try {
            requireContext().getContentResolver().unregisterContentObserver(contactChangeObserver);
        } catch (Exception e) {
            // Ignore if already unregistered
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        checkAndRequestPermission();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (actionMode != null) {
            actionMode.finish();
            actionMode = null;
        }
        dismissProgressDialog(); // Dismiss to prevent window leak
        // Prevent memory leaks: views are destroyed when fragments are detached/hidden
        if (repository != null) {
            repository.close();
        }
        binding = null;
    }


}
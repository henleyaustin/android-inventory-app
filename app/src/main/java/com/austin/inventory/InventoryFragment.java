package com.austin.inventory;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.austin.inventory.databinding.DialogAddItemBinding;
import com.austin.inventory.databinding.DialogEditItemBinding;
import com.austin.inventory.databinding.FragmentInventoryBinding;
import com.austin.inventory.databinding.ItemDataBinding;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventoryFragment extends Fragment {

    private ExecutorService executorService;
    private Handler handler;
    private DatabaseHelper databaseHelper;
    private FragmentInventoryBinding binding;
    private InventoryItemAdapter adapter;
    private String currentUserEmail;
    SharedPreferences preferences;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentInventoryBinding.inflate(inflater, container, false);
        databaseHelper = new DatabaseHelper(getContext());

        // Retrieve the email of the currently logged-in user
        currentUserEmail = preferences.getString("logged_in_user_email", null);

        requireActivity().setTitle(getString(R.string.inventory_title));

        // Setup RecyclerView
        setupRecyclerView();

        // Refresh the item list
        refreshItemList();

        return binding.getRoot();
    }

    /** @noinspection deprecation*/
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executorService = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        setHasOptionsMenu(true);
        preferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE); // Initialize here
    }

    /** @noinspection deprecation*/
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the sort menu
        inflater.inflate(R.menu.menu_sort, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_sort) {
            // Handle sort option
            showSortOptionsDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        binding.itemsList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new InventoryItemAdapter(new ArrayList<>());
        binding.itemsList.setAdapter(adapter);

        DividerItemDecoration divider = new DividerItemDecoration(binding.itemsList.getContext(), DividerItemDecoration.VERTICAL);
        binding.itemsList.addItemDecoration(divider);

        binding.fabAddItem.setOnClickListener(view -> showAddItemDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshItemList();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    /**
     * Refresh item list in recycler view
     */
    private void refreshItemList() {
        if (currentUserEmail != null) {
            executorService.execute(() -> {
                List<InventoryItem> items = databaseHelper.getInventoryItemsForUser(currentUserEmail);
                handler.post(() -> {
                    adapter.updateItems(items);
                    // Update visibility based on list size
                    binding.emptyMessage.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                });
            });
        }
    }



    private void checkAndSendSmsNotification(InventoryItem item) {
        boolean isSmsEnabled = preferences.getBoolean("sms_notifications_enabled", false);
        int minInventoryValue = preferences.getInt("minimum_inventory_value", 2);
        boolean notifyWhenZero = preferences.getBoolean("notify_inventory_zero", false);

        if ((isSmsEnabled && item.getQuantity() == minInventoryValue) ||
                (notifyWhenZero && isSmsEnabled && item.getQuantity() == 0)) {
            executorService.execute(() -> {
                String phoneNumber = databaseHelper.getUserPhoneNumber(currentUserEmail);
                String message = item.getQuantity() == 0 ?
                        "Out of inventory for " + item.getName() :
                        "Low inventory alert: " + item.getName() + " is down to " + minInventoryValue;
                sendSmsNotification(phoneNumber, message);
            });
        }
    }

    private void sendSmsNotification(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Log.d("InventoryFragment", "SMS sent: " + message);
        } catch (Exception e) {
            Log.e("InventoryFragment", "SMS failed to send", e);
        }
    }


    private class InventoryItemAdapter extends RecyclerView.Adapter<InventoryItemAdapter.ItemHolder> {

        private final List<InventoryItem> mItems;

        InventoryItemAdapter(List<InventoryItem> items) {
            this.mItems = new ArrayList<>(items);
        }

        @NonNull
        @Override
        public ItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            ItemDataBinding itemBinding = ItemDataBinding.inflate(layoutInflater, parent, false);
            return new ItemHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(ItemHolder holder, int position) {
            InventoryItem item = mItems.get(position);
            holder.bind(item);
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        void updateItems(List<InventoryItem> newItems) {
            mItems.clear();
            mItems.addAll(newItems);
            notifyDataSetChanged();
            binding.emptyMessage.setVisibility(mItems.isEmpty() ? View.VISIBLE : View.GONE);
        }

        class ItemHolder extends RecyclerView.ViewHolder {

            private final ItemDataBinding binding;

            ItemHolder(ItemDataBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(InventoryItem item) {
                binding.itemName.setText(item.getName());
                binding.itemQuantity.setText(String.valueOf(item.getQuantity()));
                setupListeners(item);
            }

            private void setupListeners(InventoryItem item) {
                binding.deleteButton.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                        .setTitle("Delete Item")
                        .setMessage("This will delete this item from inventory completely. Are you sure?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            boolean deleteResult = databaseHelper.deleteInventoryItem(item.getId());
                            if (deleteResult) {
                                mItems.remove(getAdapterPosition());
                                adapter.notifyItemRemoved(getAdapterPosition());
                                showSnackbar("Item deleted successfully");
                            } else {
                                showSnackbar("Failed to delete item");
                            }
                        })
                        .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                        .show());


                binding.incrementButton.setOnClickListener(v -> {
                    databaseHelper.incrementItemQuantity(item.getId());
                    item.setQuantity(item.getQuantity() + 1);
                    binding.itemQuantity.setText(String.valueOf(item.getQuantity()));
                });

                binding.reduceButton.setOnClickListener(v -> {
                    if (item.getQuantity() > 0) {
                        databaseHelper.decrementItemQuantity(item.getId());
                        item.setQuantity(item.getQuantity() - 1);
                        binding.itemQuantity.setText(String.valueOf(item.getQuantity()));
                    }
                    checkAndSendSmsNotification(item);
                });

                binding.editButton.setOnClickListener(v -> showEditItemDialog(item));
            }
        }
    }

    private void showEditItemDialog(InventoryItem item) {
        DialogEditItemBinding dialogBinding = DialogEditItemBinding.inflate(LayoutInflater.from(getContext()));

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        // Set the item name and quantity in the dialog
        dialogBinding.editItemName.setText(item.getName());
        dialogBinding.editItemQuantity.setText(String.valueOf(item.getQuantity()));

        // Set the listeners for update and cancel buttons
        dialogBinding.buttonUpdate.setOnClickListener(v -> handleUpdateButtonClick(item, dialogBinding, dialog));
        dialogBinding.buttonCancel.setOnClickListener(v -> dialog.dismiss());

        // Show the dialog first
        dialog.show();

        // Then set the maximum width of the dialog
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        int dialogMaxWidth = getResources().getDimensionPixelSize(R.dimen.dialog_max_width); // Define this value in your dimens.xml
        layoutParams.width = Math.min(layoutParams.width, dialogMaxWidth);
        dialog.getWindow().setAttributes(layoutParams);
    }

    private void handleUpdateButtonClick(InventoryItem item, DialogEditItemBinding dialogBinding, AlertDialog dialog) {
        String newName = dialogBinding.editItemName.getText().toString();
        String quantityStr = dialogBinding.editItemQuantity.getText().toString();
        try {
            int newQuantity = Integer.parseInt(quantityStr);
            boolean updateResult = databaseHelper.updateInventoryItem(item.getId(), newName, newQuantity);
            if (updateResult) {
                showSnackbar("Item updated successfully");
                refreshItemList();
            } else {
                showSnackbar("Failed to update item");
            }
        } catch (NumberFormatException e) {
            showSnackbar("Invalid Quantity");
        }
        dialog.dismiss();
    }

    /**
     * Display dialog for adding item
     */
    private void showAddItemDialog() {
        DialogAddItemBinding dialogBinding = DialogAddItemBinding.inflate(LayoutInflater.from(getContext()));
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        dialogBinding.buttonAdd.setOnClickListener(v -> {
            String itemName = dialogBinding.addItemName.getText().toString();
            String quantityStr = dialogBinding.addItemQuantity.getText().toString();
            if (!itemName.isEmpty() && !quantityStr.isEmpty() && currentUserEmail != null) {
                try {
                    int quantity = Integer.parseInt(quantityStr);
                    boolean insertResult = databaseHelper.insertInventoryItem(itemName, quantity, currentUserEmail);
                    if (insertResult) {
                        showSnackbar("Item added successfully");
                        refreshItemList();
                    } else {
                        showSnackbar("Failed to add item");
                    }
                } catch (NumberFormatException e) {
                    showSnackbar("Invalid quantity");
                }
            } else {
                showSnackbar("Name, quantity, and user email are required");
            }
            dialog.dismiss();
        });

        dialogBinding.buttonCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showSortOptionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Sort by")
                .setItems(new String[]{"Quantity", "Name"}, (dialog, which) -> {
                    switch (which) {
                        case 0: // Sort by Quantity
                            sortInventoryByQuantity();
                            break;
                        case 1: // Sort by Name
                            sortInventoryByName();
                            break;
                    }
                });
        builder.show();
    }

    private void sortInventoryByQuantity() {
        // Sort the list by quantity in ascending order
        Collections.sort(adapter.mItems, new Comparator<InventoryItem>() {
            @Override
            public int compare(InventoryItem o1, InventoryItem o2) {
                return Integer.compare(o1.getQuantity(), o2.getQuantity());
            }
        });

        // Notify the adapter about the data change
        adapter.notifyDataSetChanged();
    }

    private void sortInventoryByName() {
        // Sort the list by name in alphabetical order
        Collections.sort(adapter.mItems, new Comparator<InventoryItem>() {
            @Override
            public int compare(InventoryItem o1, InventoryItem o2) {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });

        // Notify the adapter about the data change
        adapter.notifyDataSetChanged();
    }


    /**
     * Show snackbar notification in app
     * @param message message to be sent to user
     */
    private void showSnackbar(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }
}

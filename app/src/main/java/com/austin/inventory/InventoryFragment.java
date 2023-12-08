package com.austin.inventory;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.austin.inventory.databinding.DialogAddItemBinding;
import com.austin.inventory.databinding.DialogEditItemBinding;
import com.austin.inventory.databinding.FragmentInventoryBinding;
import com.austin.inventory.databinding.ItemDataBinding;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class InventoryFragment extends Fragment {

    private DatabaseHelper databaseHelper;
    private FragmentInventoryBinding binding;
    private InventoryItemAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentInventoryBinding.inflate(inflater, container, false);
        databaseHelper = new DatabaseHelper(getContext());

        requireActivity().setTitle(getString(R.string.inventory_title));

        binding.itemsList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new InventoryItemAdapter(new ArrayList<>()); // Initialize with empty list
        binding.itemsList.setAdapter(adapter);

        DividerItemDecoration divider = new DividerItemDecoration(binding.itemsList.getContext(), DividerItemDecoration.VERTICAL);
        binding.itemsList.addItemDecoration(divider);

        binding.fabAddItem.setOnClickListener(view -> showAddItemDialog());

        refreshItemList();

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshItemList();
    }

    /**
     * Refresh item list in recycler view
     */
    private void refreshItemList() {
        List<InventoryItem> mItems = databaseHelper.getAllInventoryItems();
        if (adapter == null) {
            adapter = new InventoryItemAdapter(mItems);
            binding.itemsList.setAdapter(adapter);
        } else {
            adapter.updateItems(mItems);
        }
    }

    private void checkAndSendSmsNotification(InventoryItem item) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean isSmsEnabled = preferences.getBoolean("sms_notifications_enabled", false);
        String userPhoneNumber = preferences.getString("user_phone_number", null);

        if (isSmsEnabled && userPhoneNumber != null && item.getQuantity() == 1) {
            sendSmsNotification(userPhoneNumber, "Low inventory alert: " + item.getName() + " is down to 1");
        }

        if (isSmsEnabled && userPhoneNumber != null && item.getQuantity() == 0) {
            sendSmsNotification(userPhoneNumber, "Out of inventory for " + item.getName());
        }
    }

    private void sendSmsNotification(String phoneNumber, String message) {
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
    }

    private class InventoryItemAdapter extends RecyclerView.Adapter<InventoryItemAdapter.ItemHolder> {

        private List<InventoryItem> mItems;

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
                binding.deleteButton.setOnClickListener(v -> {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Delete Item")
                            .setMessage("This will delete this item from inventory completely. Are you sure?")
                            .setPositiveButton("Yes", (dialog, which) -> {
                                databaseHelper.deleteInventoryItem(item.getId());
                                mItems.remove(getAdapterPosition());
                                showSnackbar("Item deleted successfully");
                                notifyItemRemoved(getAdapterPosition());
                            })
                            .setNegativeButton("No", (dialog, which) -> {
                                dialog.dismiss();
                            })
                            .show();
                });

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

        private void showEditItemDialog(InventoryItem item) {
            DialogEditItemBinding dialogBinding = DialogEditItemBinding.inflate(LayoutInflater.from(getContext()));

            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setView(dialogBinding.getRoot());
            AlertDialog dialog = builder.create();

            dialogBinding.editItemName.setText(item.getName());
            dialogBinding.editItemQuantity.setText(String.valueOf(item.getQuantity()));

            dialogBinding.buttonUpdate.setOnClickListener(v -> {
                String newName = dialogBinding.editItemName.getText().toString();
                String quantityStr = dialogBinding.editItemQuantity.getText().toString();
                try {
                    int newQuantity = Integer.parseInt(quantityStr);
                    databaseHelper.updateInventoryItem(item.getId(), newName, newQuantity);
                    dialog.dismiss();
                    refreshItemList();
                } catch (NumberFormatException e) {
                    showSnackbar("Invalid Quantity");
                }
            });

            dialogBinding.buttonCancel.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        }
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
            if (!itemName.isEmpty() && !quantityStr.isEmpty()) {
                try {
                    int quantity = Integer.parseInt(quantityStr);
                    boolean insertResult = databaseHelper.insertInventoryItem(itemName, quantity);

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
                showSnackbar("Name and quantity are required");
            }
            dialog.dismiss();
        });

        dialogBinding.buttonCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * Show snackbar notification in app
     * @param message message to be sent to user
     */
    private void showSnackbar(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }
}

/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.LocaleController;
import org.telegram.messenger.TLRPC;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;
import org.telegram.safechat.R;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.ChatOrUserCell;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.ActionBar.BaseFragment;

public class SettingsBlockedUsersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ContactsActivity.ContactsActivityDelegate {
    private ListView listView;
    private ListAdapter listViewAdapter;
    private View progressView;
    private TextView emptyView;
    private int selectedUserId;

    private final static int block_user = 1;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.blockedUsersDidLoaded);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.blockedUsersDidLoaded);
        MessagesController.getInstance().getBlockedUsers(false);
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBarLayer.setDisplayHomeAsUpEnabled(true, R.drawable.ic_ab_back);
            actionBarLayer.setBackOverlay(R.layout.updating_state_layout);
            actionBarLayer.setTitle(LocaleController.getString("BlockedUsers", R.string.BlockedUsers));
            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == block_user) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlyUsers", true);
                        args.putBoolean("destroyAfterSelect", true);
                        args.putBoolean("usersAsSections", true);
                        args.putBoolean("returnAsResult", true);
                        ContactsActivity fragment = new ContactsActivity(args);
                        fragment.setDelegate(SettingsBlockedUsersActivity.this);
                        presentFragment(fragment);
                    }
                }
            });

            ActionBarMenu menu = actionBarLayer.createMenu();
            menu.addItem(block_user, R.drawable.plus);

            fragmentView = inflater.inflate(R.layout.settings_blocked_users_layout, container, false);
            listViewAdapter = new ListAdapter(getParentActivity());
            listView = (ListView)fragmentView.findViewById(R.id.listView);
            progressView = fragmentView.findViewById(R.id.progressLayout);
            emptyView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            emptyView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            emptyView.setText(LocaleController.getString("NoBlocked", R.string.NoBlocked));
            if (MessagesController.getInstance().loadingBlockedUsers) {
                progressView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
                listView.setEmptyView(null);
            } else {
                progressView.setVisibility(View.GONE);
                listView.setEmptyView(emptyView);
            }
            listView.setAdapter(listViewAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i < MessagesController.getInstance().blockedUsers.size()) {
                        Bundle args = new Bundle();
                        args.putInt("user_id", MessagesController.getInstance().blockedUsers.get(i));
                        presentFragment(new UserProfileActivity(args));
                    }
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i < 0 || i >= MessagesController.getInstance().blockedUsers.size() || getParentActivity() == null) {
                        return true;
                    }
                    selectedUserId = MessagesController.getInstance().blockedUsers.get(i);

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                    CharSequence[] items = new CharSequence[] {LocaleController.getString("Unblock", R.string.Unblock)};

                    builder.setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (i == 0) {
                                MessagesController.getInstance().unblockUser(selectedUserId);
                            }
                        }
                    });
                    showAlertDialog(builder);

                    return true;
                }
            });
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                updateVisibleRows(mask);
            }
        } else if (id == NotificationCenter.blockedUsersDidLoaded) {
            if (progressView != null) {
                progressView.setVisibility(View.GONE);
            }
            if (listView != null && listView.getEmptyView() == null) {
                listView.setEmptyView(emptyView);
            }
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
        }
    }

    private void updateVisibleRows(int mask) {
        if (listView == null) {
            return;
        }
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof ChatOrUserCell) {
                ((ChatOrUserCell) child).update(mask);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didSelectContact(final TLRPC.User user, String param) {
        if (user == null) {
            return;
        }
        MessagesController.getInstance().blockUser(user.id);
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return i != MessagesController.getInstance().blockedUsers.size();
        }

        @Override
        public int getCount() {
            if (MessagesController.getInstance().blockedUsers.isEmpty()) {
                return 0;
            }
            return MessagesController.getInstance().blockedUsers.size() + 1;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int type = getItemViewType(i);
            if (type == 0) {
                if (view == null) {
                    view = new ChatOrUserCell(mContext);
                    ((ChatOrUserCell)view).usePadding = false;
                    ((ChatOrUserCell)view).useSeparator = true;
                }
                TLRPC.User user = MessagesController.getInstance().getUser(MessagesController.getInstance().blockedUsers.get(i));
                ((ChatOrUserCell)view).setData(user, null, null, null, user.phone != null && user.phone.length() != 0 ? PhoneFormat.getInstance().format("+" + user.phone) : LocaleController.getString("NumberUnknown", R.string.NumberUnknown));
            } else if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_unblock_info_row_layout, viewGroup, false);
                    TextView textView = (TextView)view.findViewById(R.id.info_text_view);
                    textView.setText(LocaleController.getString("UnblockText", R.string.UnblockText));
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if(i == MessagesController.getInstance().blockedUsers.size()) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return MessagesController.getInstance().blockedUsers.isEmpty();
        }
    }
}

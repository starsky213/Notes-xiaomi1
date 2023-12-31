package net.micode.notes.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;

/*
 *该类功能：NotesPreferenceActivity，在小米便签中主要实现的是对背景颜色和字体大小的数据储存。
 *       继承了PreferenceActivity主要功能为对系统信息和配置进行自动保存的Activity
 */
public class NotesPreferenceActivity extends PreferenceActivity {
    public static final String PREFERENCE_NAME = "notes_preferences";
    //优先名
    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";
    //同步账号
    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";
    //同步时间
    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";

    private static final String PREFERENCE_SYNC_ACCOUNT_KEY = "pref_sync_account_key";
    //同步密码
    private static final String AUTHORITIES_FILTER_KEY = "authorities";
    //本地密码
    private PreferenceCategory mAccountCategory;
    //账户分组
    private GTaskReceiver mReceiver;
    //同步任务接收器
    private Account[] mOriAccounts;
    //账户
    private boolean mHasAddedAccount;
    //账户的hash标记

    @Override
    /*
     *函数功能：创建一个activity，在函数里要完成所有的正常静态设置
     *参数：Bundle icicle：存放了 activity 当前的状态
     *函数实现：如下注释
     */
    protected void onCreate(Bundle icicle) {
        //先执行父类的创建函数
        super.onCreate(icicle);

        /* using the app icon for navigation */
        getActionBar().setDisplayHomeAsUpEnabled(true);
        //给左上角图标的左边加上一个返回的图标 

        addPreferencesFromResource(R.xml.preferences);
        //添加xml来源并显示 xml
        mAccountCategory = (PreferenceCategory) findPreference(PREFERENCE_SYNC_ACCOUNT_KEY);
        //根据同步账户关键码来初始化分组
        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME);
        registerReceiver(mReceiver, filter);
        //初始化同步组件

        mOriAccounts = null;
        View header = LayoutInflater.from(this).inflate(R.layout.settings_header, null);
        //获取listvivew，ListView的作用:用于列出所有选择 
        getListView().addHeaderView(header, null, true);
        //在listview组件上方添加其他组件
    }

    @Override
    /*
     * 函数功能：activity交互功能的实现，用于接受用户的输入
     * 函数实现：如下注释
     */
    protected void onResume() {
        //先执行父类 的交互实现
        super.onResume();

        // need to set sync account automatically if user has added a new
        // account
        if (mHasAddedAccount) {
            //若用户新加了账户则自动设置同步账户
            Account[] accounts = getGoogleAccounts();
            //获取google同步账户
            if (mOriAccounts != null && accounts.length > mOriAccounts.length) {
                //若原账户不为空且当前账户有增加
                for (Account accountNew : accounts) {
                    boolean found = false;
                    for (Account accountOld : mOriAccounts) {
                        if (TextUtils.equals(accountOld.name, accountNew.name)) {
                            //更新账户
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        setSyncAccount(accountNew.name);
                        //若是没有找到旧的账户，那么同步账号中就只添加新账户
                        break;
                    }
                }
            }
        }

        refreshUI();
        //刷新标签界面
    }

    @Override
    /*
     * 函数功能：销毁一个activity
     * 函数实现：如下注释
     */
    protected void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            //注销接收器
        }
        super.onDestroy();
        //执行父类的销毁动作
    }

    /*
     * 函数功能：重新设置账户信息
     * 函数实现：如下注释
     */
    private void loadAccountPreference() {
        mAccountCategory.removeAll();
        //销毁所有的分组
        Preference accountPref = new Preference(this);
        //建立首选项
        final String defaultAccount = getSyncAccountName(this);
        accountPref.setTitle(getString(R.string.preferences_account_title));
        accountPref.setSummary(getString(R.string.preferences_account_summary));
        //设置首选项的大标题和小标题
        accountPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                //建立监听器
                if (!GTaskSyncService.isSyncing()) {
                    if (TextUtils.isEmpty(defaultAccount)) {
                        // the first time to set account
                        //若是第一次建立账户显示选择账户提示对话框
                        showSelectAccountAlertDialog();
                    } else {
                        // if the account has already been set, we need to promp
                        // user about the risk
                        //若是已经建立则显示修改对话框并进行修改操作
                        showChangeAccountConfirmAlertDialog();
                    }
                } else {
                    //若在没有同步的情况下，则在toast中显示不能修改
                    Toast.makeText(NotesPreferenceActivity.this,
                                    R.string.preferences_toast_cannot_change_account, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            }
        });

        //根据新建首选项编辑新的账户分组
        mAccountCategory.addPreference(accountPref);
    }

    /*
     *函数功能：设置按键的状态和最后同步的时间
     *函数实现：如下注释
     */
    private void loadSyncButton() {
        Button syncButton = (Button) findViewById(R.id.preference_sync_button);
        TextView lastSyncTimeView = (TextView) findViewById(R.id.prefenerece_sync_status_textview);
        //获取同步按钮控件和最终同步时间的的窗口
        // set button state
        //设置按钮的状态
        if (GTaskSyncService.isSyncing()) {
            //若是在同步状态下
            syncButton.setText(getString(R.string.preferences_button_sync_cancel));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.cancelSync(NotesPreferenceActivity.this);
                }
            });
            //设置按钮显示的文本为“取消同步”以及监听器
        } else {
            syncButton.setText(getString(R.string.preferences_button_sync_immediately));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.startSync(NotesPreferenceActivity.this);
                }
            });
            //若是不同步则设置按钮显示的文本为“立即同步”以及对应监听器
        }
        syncButton.setEnabled(!TextUtils.isEmpty(getSyncAccountName(this)));
        //设置按键可用还是不可用

        // set last sync time
        // 设置最终同步时间
        if (GTaskSyncService.isSyncing()) {
            //若是在同步的情况下
            lastSyncTimeView.setText(GTaskSyncService.getProgressString());
            lastSyncTimeView.setVisibility(View.VISIBLE);
            // 根据当前同步服务器设置时间显示框的文本以及可见性
        } else {
            //若是非同步情况
            long lastSyncTime = getLastSyncTime(this);
            if (lastSyncTime != 0) {
                lastSyncTimeView.setText(getString(R.string.preferences_last_sync_time,
                        DateFormat.format(getString(R.string.preferences_last_sync_time_format),
                                lastSyncTime)));
                lastSyncTimeView.setVisibility(View.VISIBLE);
                //则根据最后同步时间的信息来编辑时间显示框的文本内容和可见性
            } else {
                //若时间为空直接设置为不可见状态
                lastSyncTimeView.setVisibility(View.GONE);
            }
        }
    }
    /*
     *函数功能：刷新标签界面
     *函数实现：调用上文设置账号和设置按键两个函数来实现
     */
    private void refreshUI() {
        loadAccountPreference();
        loadSyncButton();
    }

    /*
     * 函数功能：显示账户选择的对话框并进行账户的设置
     * 函数实现：如下注释
     */
    private void showSelectAccountAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        //创建一个新的对话框

        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_select_account_title));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_select_account_tips));
        //设置标题以及子标题的内容
        dialogBuilder.setCustomTitle(titleView);
        dialogBuilder.setPositiveButton(null, null);
        //设置对话框的自定义标题，建立一个YES的按钮
        Account[] accounts = getGoogleAccounts();
        String defAccount = getSyncAccountName(this);
        //获取同步账户信息
        mOriAccounts = accounts;
        mHasAddedAccount = false;

        if (accounts.length > 0) {
            //若账户不为空
            CharSequence[] items = new CharSequence[accounts.length];
            final CharSequence[] itemMapping = items;
            int checkedItem = -1;
            int index = 0;
            for (Account account : accounts) {
                if (TextUtils.equals(account.name, defAccount)) {
                    checkedItem = index;
                    //在账户列表中查询到所需账户
                }
                items[index++] = account.name;
            }
            dialogBuilder.setSingleChoiceItems(items, checkedItem,
                    //在对话框建立一个单选的复选框
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setSyncAccount(itemMapping[which].toString());
                            dialog.dismiss();
                            //取消对话框
                            refreshUI();
                        }
                        //设置点击后执行的事件，包括检录新同步账户和刷新标签界面
                    });
            //建立对话框网络版的监听器
        }

        View addAccountView = LayoutInflater.from(this).inflate(R.layout.add_account_text, null);
        dialogBuilder.setView(addAccountView);
        //给新加账户对话框设置自定义样式

        final AlertDialog dialog = dialogBuilder.show();
        //显示对话框
        addAccountView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mHasAddedAccount = true;
                //将新加账户的hash置true
                Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
                //建立网络建立组件
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                        "gmail-ls"
                });
                startActivityForResult(intent, -1);
                //跳回上一个选项
                dialog.dismiss();
            }
        });
        //建立新加账户对话框的监听器
    }

    /*
     * 函数功能：显示账户选择对话框和相关账户操作
     * 函数实现：如下注释
     */
    private void showChangeAccountConfirmAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        //创建一个新的对话框
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_change_account_title,
                getSyncAccountName(this)));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_change_account_warn_msg));
        //根据同步修改的账户信息设置标题以及子标题的内容
        dialogBuilder.setCustomTitle(titleView);
        //设置对话框的自定义标题
        CharSequence[] menuItemArray = new CharSequence[] {
                getString(R.string.preferences_menu_change_account),
                getString(R.string.preferences_menu_remove_account),
                getString(R.string.preferences_menu_cancel)
        };
        //定义一些标记字符串
        dialogBuilder.setItems(menuItemArray, new DialogInterface.OnClickListener() {
            //设置对话框要显示的一个list，用于显示几个命令时,即change，remove，cancel
            public void onClick(DialogInterface dialog, int which) {
                //按键功能，由which来决定
                if (which == 0) {
                    //进入账户选择对话框
                    showSelectAccountAlertDialog();
                } else if (which == 1) {
                    //删除账户并且跟新便签界面
                    removeSyncAccount();
                    refreshUI();
                }
            }
        });
        dialogBuilder.show();
        //显示对话框
    }

    /*
     *函数功能：获取谷歌账户
     *函数实现：通过账户管理器直接获取
     */
    private Account[] getGoogleAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        return accountManager.getAccountsByType("com.google");
    }

    /*
     * 函数功能：设置同步账户
     * 函数实现：如下注释：
     */
    private void setSyncAccount(String account) {
        if (!getSyncAccountName(this).equals(account)) {
            //假如该账号不在同步账号列表中
            SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            //编辑共享的首选项
            if (account != null) {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, account);
            } else {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
            }
            //将该账号加入到首选项中

            editor.commit();
            //提交修改的数据


            setLastSyncTime(this, 0);
            //将最后同步时间清零

            // clean up local gtask related info
            new Thread(new Runnable() {
                public void run() {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.GTASK_ID, "");
                    values.put(NoteColumns.SYNC_ID, 0);
                    getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
                }
            }).start();
            //重置当地同步任务的信息

            Toast.makeText(NotesPreferenceActivity.this,
                    getString(R.string.preferences_toast_success_set_accout, account),
                    Toast.LENGTH_SHORT).show();
            //将toast的文本信息置为“设置账户成功”并显示出来
        }
    }
    /*
     * 函数功能：删除同步账户
     * 函数实现：如下注释：
     */
    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        //设置共享首选项

        if (settings.contains(PREFERENCE_SYNC_ACCOUNT_NAME)) {
            editor.remove(PREFERENCE_SYNC_ACCOUNT_NAME);
            //假如当前首选项中有账户就删除
        }
        if (settings.contains(PREFERENCE_LAST_SYNC_TIME)) {
            editor.remove(PREFERENCE_LAST_SYNC_TIME);
            //删除当前首选项中有账户时间
        }
        editor.commit();
        //提交更新后的数据

        // clean up local gtask related info
        new Thread(new Runnable() {
            public void run() {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");
                values.put(NoteColumns.SYNC_ID, 0);
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }
        }).start();
        //重置当地同步任务的信息
    }

    /*
     * 函数功能：获取同步账户名称
     * 函数实现：通过共享的首选项里的信息直接获取
     */
    public static String getSyncAccountName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }

    /*
     * 函数功能：设置最终同步的时间
     * 函数实现：如下注释
     */
    public static void setLastSyncTime(Context context, long time) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        // 从共享首选项中找到相关账户并获取其编辑器
        editor.putLong(PREFERENCE_LAST_SYNC_TIME, time);
        editor.commit();
        //编辑最终同步时间并提交更新
    }
    /*
     * 函数功能：获取最终同步时间
     * 函数实现：通过共享的首选项里的信息直接获取
     */
    public static long getLastSyncTime(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }

    /*
     * 函数功能：接受同步信息
     * 函数实现：继承BroadcastReceiver
     */
    private class GTaskReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUI();
            if (intent.getBooleanExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_IS_SYNCING, false)) {
                //获取随广播而来的Intent中的同步服务的数据
                TextView syncStatus = (TextView) findViewById(R.id.prefenerece_sync_status_textview);
                syncStatus.setText(intent
                        .getStringExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_PROGRESS_MSG));
                //通过获取的数据在设置系统的状态
            }

        }
    }

    /*
     * 函数功能：处理菜单的选项
     * 函数实现：如下注释
     * 参数:MenuItem菜单选项
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            //根据选项的id选择，这里只有一个主页
            case android.R.id.home:
                Intent intent = new Intent(this, NotesListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            //在主页情况下在创建连接组件intent，发出清空的信号并开始一个相应的activity
            default:
                return false;
        }
    }
}
 
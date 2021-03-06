package com.telerik.localnotifications;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.RemoteInput;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import static com.telerik.localnotifications.Action.CLICK_ACTION_ID;

/**
 * IntentService which is an entry point, whenever a notification from the bar is tapped and executed.
 * The activity fires, notifies the callback.
 */
public class NotificationActionReceiver extends IntentService {
  private static String TAG = "NotificationActionReceiver";

  // Hold a reference to the intent to handle.
  private Intent intent;

  public NotificationActionReceiver() {
    super("NotificationActionReceiver");
  }

  @Override
  protected void onHandleIntent(@Nullable Intent intent) {
    this.intent = intent;

    if (intent == null) {
      return;
    }

    Bundle bundle = intent.getExtras();

    if (bundle == null) {
      return;
    }

    try {
      onClick(intent.getAction(), bundle);
    } catch (JSONException e) {
      Log.e(TAG, e.getMessage(), e);
    }
  }

  private void onClick(String action, Bundle bundle) throws JSONException {
    final Context context = getApplicationContext();

    // Note that for the non-default action this will be empty:
    final JSONObject opts = Store.get(context, bundle.getInt(Builder.NOTIFICATION_ID), false);

    boolean isAppActive = LocalNotificationsPlugin.isActive;
    boolean doLaunch = intent.getBooleanExtra("NOTIFICATION_LAUNCH", true);

    Log.d(TAG, "doLaunch = " + doLaunch);

    if (!isAppActive && doLaunch) {
      forceMainActivityReload();
    }

    if (setTextInput(action, opts)) {
      opts.put("event", "input");
    } else if (!CLICK_ACTION_ID.equals(action)) {
      opts.put("event", "button");
      opts.put("response", action);
    } else {
      opts.put("event", "default");
    }

    opts.put("foreground", isAppActive);
    // opts.put("coldstart", !isPluginActive);

    LocalNotificationsPlugin.executeOnMessageReceivedCallback(opts);

    if (opts.has("id") && !opts.optBoolean("ongoing", false)) {
      int id = opts.getInt("id");

      // Clear the notification from the tray, unless it's marker as ongoing/sticky:
      ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(id);

      // And also unpersist it:
      Store.remove(context, id);
    }
  }

  private boolean setTextInput(String action, JSONObject data) throws JSONException {
    Bundle input = RemoteInput.getResultsFromIntent(intent);
    if (input != null) {
      data.put("response", input.getCharSequence(action));
      return true;
    }
    return false;
  }

  private void forceMainActivityReload() {
    PackageManager pm = getPackageManager();
    Intent launchIntent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());
    Log.d(TAG, "starting activity for package: " + getApplicationContext().getPackageName());
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    startActivity(launchIntent);
  }
}
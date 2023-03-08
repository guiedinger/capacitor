package com.getcapacitor;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;

import com.getcapacitor.android.R;
import com.getcapacitor.cordova.MockCordovaInterfaceImpl;
import com.getcapacitor.cordova.MockCordovaWebViewImpl;
import com.getcapacitor.plugin.App;

import org.apache.cordova.ConfigXmlParser;
import org.apache.cordova.CordovaPreferences;
import org.apache.cordova.PluginEntry;
import org.apache.cordova.PluginManager;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BridgeActivity extends AppCompatActivity {
  protected Bridge bridge;
  private WebView webView;
  protected MockCordovaInterfaceImpl cordovaInterface;
  protected boolean keepRunning = true;
  private ArrayList<PluginEntry> pluginEntries;
  private PluginManager pluginManager;
  private CordovaPreferences preferences;
  private MockCordovaWebViewImpl mockWebView;
  private JSONObject config;

  private int activityDepth = 0;

  private String lastActivityPlugin;

  private List<Class<? extends Plugin>> initialPlugins = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  protected void init(Bundle savedInstanceState, List<Class<? extends Plugin>> plugins) {
    this.init(savedInstanceState, plugins, null);
  }
  protected void init(Bundle savedInstanceState, List<Class<? extends Plugin>> plugins, JSONObject config) {
    this.initialPlugins = plugins;
    this.config = config;
    loadConfig(this.getApplicationContext(),this);

    getApplication().setTheme(getResources().getIdentifier("AppTheme_NoActionBar", "style", getPackageName()));
    setTheme(getResources().getIdentifier("AppTheme_NoActionBar", "style", getPackageName()));
    setTheme(R.style.AppTheme_NoActionBar);


    setContentView(R.layout.bridge_layout_main);

    this.load(savedInstanceState);
  }

  /**
   * Load the WebView and create the Bridge
   */
  protected void load(Bundle savedInstanceState) {
    Logger.debug("Starting BridgeActivity");

    webView = findViewById(R.id.webview);

    cordovaInterface = new MockCordovaInterfaceImpl(this);
    if (savedInstanceState != null) {
      cordovaInterface.restoreInstanceState(savedInstanceState);
    }

    mockWebView = new MockCordovaWebViewImpl(this.getApplicationContext());
    mockWebView.init(cordovaInterface, pluginEntries, preferences, webView);

    pluginManager = mockWebView.getPluginManager();
    cordovaInterface.onCordovaInit(pluginManager);
    bridge = new Bridge(this, webView, initialPlugins, cordovaInterface, pluginManager, preferences, this.config);

    if (savedInstanceState != null) {
      bridge.restoreInstanceState(savedInstanceState);
    }
    this.keepRunning = preferences.getBoolean("KeepRunning", true);
    this.onNewIntent(getIntent());
  }

  public Bridge getBridge() {
    return this.bridge;
  }

  /**
   * Notify the App plugin that the current state changed
   * @param isActive
   */
  private void fireAppStateChanged(boolean isActive) {
    PluginHandle handle = bridge.getPlugin("App");
    if (handle == null) {
      return;
    }

    App appState = (App) handle.getInstance();
    if (appState != null) {
      appState.fireChange(isActive);
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    bridge.saveInstanceState(outState);
  }

  @Override
  public void onStart() {
    super.onStart();

    activityDepth++;

    this.bridge.onStart();
    mockWebView.handleStart();
    askNotificationPermission();

    Logger.debug("App started");
  }

  @Override
  public void onRestart() {
    super.onRestart();
    this.bridge.onRestart();
    Logger.debug("App restarted");
  }

  @Override
  public void onResume() {
    super.onResume();

    fireAppStateChanged(true);

    this.bridge.onResume();

    mockWebView.handleResume(this.keepRunning);

    Logger.debug("App resumed");
  }

  @Override
  public void onPause() {
    super.onPause();

    this.bridge.onPause();
    if (this.mockWebView != null) {
      boolean keepRunning = this.keepRunning || this.cordovaInterface.getActivityResultCallback() != null;
      this.mockWebView.handlePause(keepRunning);
    }

    Logger.debug("App paused");
  }

  @Override
  public void onStop() {
    super.onStop();

    activityDepth = Math.max(0, activityDepth - 1);
    if (activityDepth == 0) {
      fireAppStateChanged(false);
    }

    this.bridge.onStop();

    if (mockWebView != null) {
      mockWebView.handleStop();
    }

    Logger.debug("App stopped");
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    this.bridge.onDestroy();
    if (this.mockWebView != null) {
      mockWebView.handleDestroy();
    }
    Logger.debug("App destroyed");
  }

  @Override
  public void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (webView != null) {
      webView.removeAllViews();
      webView.destroy();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (this.bridge == null) {
      return;
    }

    this.bridge.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (this.bridge == null) {
      return;
    }
    this.bridge.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (this.bridge == null || intent == null) {
      return;
    }

    this.bridge.onNewIntent(intent);
    mockWebView.onNewIntent(intent);
  }

  @Override
  public void onBackPressed() {
    if (this.bridge == null) {
      return;
    }

    this.bridge.onBackPressed();
  }

  public void loadConfig(Context context, Activity activity) {
    ConfigXmlParser parser = new ConfigXmlParser();
    parser.parse(context);
    preferences = parser.getPreferences();
    preferences.setPreferencesBundle(activity.getIntent().getExtras());
    pluginEntries = parser.getPluginEntries();
  }

// Declare the launcher at the top of your Activity/Fragment:
  private final ActivityResultLauncher<String> requestPermissionLauncher =
    registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
    });

  private void askNotificationPermission() {
      // This is only necessary for API level >= 33 (TIRAMISU)
      if (Build.VERSION.SDK_INT >= 33) {
          if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") ==
                  PackageManager.PERMISSION_GRANTED) {
              // FCM SDK (and your app) can post notifications.
          } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS")) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS");
            }
          }
          else {
            // Directly ask for the permission
            requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS");
          }
      }
  }

}

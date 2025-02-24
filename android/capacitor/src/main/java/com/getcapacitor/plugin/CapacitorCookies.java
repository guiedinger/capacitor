package com.getcapacitor.plugin;

import android.webkit.JavascriptInterface;
import androidx.annotation.Nullable;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginConfig;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.net.CookieHandler;
import java.net.HttpCookie;
import java.net.URI;

@CapacitorPlugin
public class CapacitorCookies extends Plugin {

    CapacitorCookieManager cookieManager;

    @Override
    public void load() {
        this.bridge.getWebView().addJavascriptInterface(this, "CapacitorCookiesAndroidInterface");
        this.cookieManager = new CapacitorCookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
        super.load();
    }

    @JavascriptInterface
    public boolean isEnabled() {
        PluginConfig pluginConfig = getBridge().getConfig().getPluginConfiguration("CapacitorCookies");
        return pluginConfig.getBoolean("enabled", false);
    }

    /**
     * Helper function for getting the serverUrl from the Capacitor Config. Returns an empty
     * string if it is invalid and will auto-reject through {@code call}
     * @param call the {@code PluginCall} context
     * @return the string of the server specified in the Capacitor config
     */
    private String getServerUrl(@Nullable PluginCall call) {
        String url = (call == null) ? this.bridge.getServerUrl() : call.getString("url", this.bridge.getServerUrl());

        if (url == null || url.isEmpty()) {
            url = this.bridge.getLocalUrl();
        }

        URI uri = getUri(url);
        if (uri == null) {
            if (call != null) {
                call.reject("Invalid URL. Check that \"server\" is passed in correctly");
            }

            return "";
        }

        return url;
    }

    /**
     * Try to parse a url string and if it can't be parsed, return null
     * @param url the url string to try to parse
     * @return a parsed URI
     */
    private URI getUri(String url) {
        try {
            return new URI(url);
        } catch (Exception ex) {
            return null;
        }
    }

    @JavascriptInterface
    public String getCookies() {
        try {
            String url = getServerUrl(null);
            if (!url.isEmpty()) {
                return cookieManager.getCookieString(url);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    @JavascriptInterface
    public void setCookie(String key, String value) {
        String url = getServerUrl(null);

        if (!url.isEmpty()) {
            cookieManager.setCookie(url, key, value);
        }
    }

    @PluginMethod
    public void setCookie(PluginCall call) {
        String key = call.getString("key");
        String value = call.getString("value");
        String url = getServerUrl(call);

        if (!url.isEmpty()) {
            cookieManager.setCookie(url, key, value);
            call.resolve();
        }
    }

    @PluginMethod
    public void deleteCookie(PluginCall call) {
        String key = call.getString("key");
        String url = getServerUrl(call);
        if (!url.isEmpty()) {
            cookieManager.setCookie(url, key + "=; Expires=Wed, 31 Dec 2000 23:59:59 GMT");
            call.resolve();
        }
    }

    @PluginMethod
    public void clearCookies(PluginCall call) {
        String url = getServerUrl(call);
        if (!url.isEmpty()) {
            HttpCookie[] cookies = cookieManager.getCookies(url);
            for (HttpCookie cookie : cookies) {
                cookieManager.setCookie(url, cookie.getName() + "=; Expires=Wed, 31 Dec 2000 23:59:59 GMT");
            }
            call.resolve();
        }
    }

    @PluginMethod
    public void clearAllCookies(PluginCall call) {
        cookieManager.removeAllCookies();
        call.resolve();
    }
}

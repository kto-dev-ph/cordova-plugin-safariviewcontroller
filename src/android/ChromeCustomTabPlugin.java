package com.customtabplugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.ColorInt;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import android.text.TextUtils;
import android.util.Log;
import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ChromeCustomTabPlugin extends CordovaPlugin {
    private static final String TAG = "ChromeCustomTabPlugin";
    private static final int CUSTOM_TAB_REQUEST_CODE = 1001;

    private CustomTabServiceHelper helper;
    private CallbackContext callbackContext;
    private Bundle animationBundle;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        helper = new CustomTabServiceHelper(cordova.getActivity());
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;

        if ("isAvailable".equals(action)) {
            boolean available = helper.isAvailable();
            callbackContext.success(available ? 1 : 0);
            return true;
        }

        if ("show".equals(action)) {
            JSONObject options = args.getJSONObject(0);
            String url = options.optString("url");
            String color = options.optString("toolbarColor", "#CCCCCC");
            boolean share = options.optBoolean("showDefaultShareMenuItem", false);
            boolean animated = options.optBoolean("animated", true);
            String transition = animated ? options.optString("transition", "slide") : "";

            if (TextUtils.isEmpty(url)) {
                callbackContext.error("URL is required.");
                return true;
            }

            try {
                openChromeCustomTab(url, Color.parseColor(color), share, transition);
            } catch (Exception e) {
                callbackContext.error("Failed to open Chrome Custom Tab: " + e.getMessage());
            }

            return true;
        }

        return false;
    }

    private void openChromeCustomTab(String url, @ColorInt int color, boolean share, String transition) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(helper.getSession());
        builder.setToolbarColor(color);
        if (share) builder.addDefaultShareMenuItem();
        if (!TextUtils.isEmpty(transition)) setupTransition(builder, transition);

        CustomTabsIntent intent = builder.build();

        // Force Chrome
        final String chromePackage = "com.android.chrome";
        try {
            cordova.getActivity().getPackageManager().getPackageInfo(chromePackage, 0);
        } catch (Exception e) {
            throw new RuntimeException("Chrome is not installed.");
        }

        intent.intent.setPackage(chromePackage); // this is key
        launchCustomTab(url, intent.intent);
    }

    private void setupTransition(CustomTabsIntent.Builder builder, String transition) {
        Activity activity = cordova.getActivity();
        switch (transition) {
            case "slide":
                animationBundle = ActivityOptionsCompat.makeCustomAnimation(
                        activity,
                        getIdentifier("slide_in_right", "anim"),
                        getIdentifier("slide_out_left", "anim")
                ).toBundle();
                builder.setExitAnimations(activity,
                        getIdentifier("slide_in_left", "anim"),
                        getIdentifier("slide_out_right", "anim")
                );
                break;
        }
    }

    private void launchCustomTab(String url, Intent intent) {
        intent.setData(Uri.parse(url));
        if (animationBundle != null) {
            ActivityCompat.startActivityForResult(
                    cordova.getActivity(), intent, CUSTOM_TAB_REQUEST_CODE, animationBundle);
        } else {
            cordova.getActivity().startActivityForResult(intent, CUSTOM_TAB_REQUEST_CODE);
        }
    }

    private int getIdentifier(String name, String defType) {
        Activity activity = cordova.getActivity();
        return activity.getResources().getIdentifier(name, defType, activity.getPackageName());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CUSTOM_TAB_REQUEST_CODE) {
            try {
                JSONObject result = new JSONObject();
                result.put("event", "closed");
                if (callbackContext != null) {
                    callbackContext.success(result);
                    callbackContext = null;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}

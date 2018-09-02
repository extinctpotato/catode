package in.shick.diode.browser;

import in.shick.diode.common.Constants;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebSettings.RenderPriority;
import android.webkit.WebView;

/**
 * Fixes the onWindowFocusChanged bug, by catching NullPointerException.
 * https://groups.google.com/d/topic/android-developers/ktbwY2gtLKQ/discussion
 * @author Andrew
 *
 */
public class WebViewFixed extends WebView {

    private static final String TAG = "WebView";

    public WebViewFixed(Context context) {
        super(context);
        // Performance tweak: http://stackoverflow.com/questions/7422427/android-webview-slow
        super.getSettings().setRenderPriority(RenderPriority.HIGH);
        super.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
    }

    public WebViewFixed(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // Performance tweak: http://stackoverflow.com/questions/7422427/android-webview-slow
        super.getSettings().setRenderPriority(RenderPriority.HIGH);
        super.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
    }

    public WebViewFixed(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Performance tweak: http://stackoverflow.com/questions/7422427/android-webview-slow
        super.getSettings().setRenderPriority(RenderPriority.HIGH);
        super.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        try {
            super.onWindowFocusChanged(hasWindowFocus);
        } catch (NullPointerException ex) {
            if (Constants.LOGGING) Log.e(TAG, "WebView.onWindowFocusChanged", ex);
        }
    }
}
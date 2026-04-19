package com.mirzakhanidehkordi.payrails_lib.ui

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import android.annotation.SuppressLint

/**
 * A Composable function that displays a WebView for handling checkout processes.
 * It's designed to load a given URL and trigger a callback when a specific
 * "success" pattern is detected in the page URL, indicating completion.
 *
 * @param url The URL to load in the WebView.
 * @param successRedirectUrl The expected URL to redirect to on successful completion.
 * @param failureRedirectUrl The expected URL to redirect to on failure/cancellation.
 * @param onComplete A lambda function to be invoked when the checkout process is considered complete (success).
 * @param onError A lambda function to be invoked when the checkout process ends with an error or cancellation.
 */
@Composable
fun CheckoutWebView(
    url: String,
    successRedirectUrl: String, // e.g., "https://yourapp.com/payrails/success" or a custom scheme
    failureRedirectUrl: String, // e.g., "https://yourapp.com/payrails/failure"
    onComplete: () -> Unit,
    onError: (String) -> Unit // Pass an error message
) {
    AndroidView(factory = { context ->
        WebView(context).apply {
            @SuppressLint("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true // Often needed for modern web apps
            settings.setSupportMultipleWindows(true) // If redirects open new windows

            // Optional: Enable debug for WebView (for testing only, remove in prod)
            // WebView.setWebContentsDebuggingEnabled(true)

            webViewClient = object : WebViewClient() {
                /**
                 * Intercept URL loading. This is the primary way to handle redirects.
                 */
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    url?.let {
                        val uri = Uri.parse(it)
                        // This is a more robust way to handle callbacks:
                        // 1. Using specific redirect URLs provided by Payrails or your own backend.
                        // 2. Potentially using deep links/custom schemes like "yourapp://payrails/success"
                        if (it.startsWith(successRedirectUrl)) {
                            onComplete()
                            return true // Indicate that we handled the URL
                        } else if (it.startsWith(failureRedirectUrl)) {
                            onError("Payment failed or cancelled.")
                            return true // Indicate that we handled the URL
                        }
                        // Add more specific handling if Payrails provides other redirect types
                        // For example, if it's a 3DS redirect, you might want to let the WebView load it.
                    }
                    return super.shouldOverrideUrlLoading(view, url) // Let the WebView load the URL normally
                }

                /**
                 * Called when a page finishes loading.
                 * This can be used for initial loading or fallback checks, but should not be the primary
                 * mechanism for success/failure callbacks from payment gateways due to redirects.
                 */
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    super.onPageFinished(view, pageUrl)
                    // You can add additional checks here if the payment flow involves
                    // JavaScript calls that set specific values that you can then read
                    // using evaluateJavascript, but URL redirection is usually more reliable.
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    onError("WebView error: $description (Code: $errorCode)")
                }

                // Add other overrides as needed, e.g., onReceivedSslError for SSL certificate issues.
            }
            loadUrl(url)
        }
    })
}
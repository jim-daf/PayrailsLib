package com.mirzakhanidehkordi.payrails_lib.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

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

            // Security hardening: disable file and content access (not needed for checkout)
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            // Security hardening: block mixed content (HTTP resources on HTTPS pages)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            webViewClient = object : WebViewClient() {
                /**
                 * Intercept URL loading. This is the primary way to handle redirects.
                 * Implements URL scheme whitelisting to block dangerous schemes.
                 */
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    url?.let {
                        // Check for success/failure redirect URLs first
                        if (it.startsWith(successRedirectUrl)) {
                            onComplete()
                            return true
                        } else if (it.startsWith(failureRedirectUrl)) {
                            onError("Payment failed or cancelled.")
                            return true
                        }

                        // URL scheme whitelisting: only allow https:// navigation
                        val uri = Uri.parse(it)
                        val scheme = uri.scheme?.lowercase()
                        if (scheme != "https") {
                            onError("Blocked insecure navigation: $scheme")
                            return true
                        }
                    }
                    return super.shouldOverrideUrlLoading(view, url)
                }

                /**
                 * Called when a page finishes loading.
                 */
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    super.onPageFinished(view, pageUrl)
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    onError("WebView error: $description (Code: $errorCode)")
                }

                /**
                 * Handle SSL errors — never proceed past SSL errors in a payment context.
                 */
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.cancel()
                    onError("SSL certificate error. Connection refused for security.")
                }
            }
            loadUrl(url)
        }
    })
}
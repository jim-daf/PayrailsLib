package com.mirzakhanidehkordi.payrails_lib.ui

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A Composable function that displays a WebView for handling checkout processes.
 * It loads a given URL and triggers a callback when the page navigates to one of
 * the configured success or failure redirect URLs.
 *
 * Hardening notes:
 *  - File access from WebView is disabled. The checkout flow is fully remote so
 *    there is no reason to expose local files to the page.
 *  - Navigations are restricted to an allow-list. Anything not on the list is
 *    routed back to the host app via [onExternalNavigation] so the host can
 *    decide whether to open the URL with an external intent.
 *  - The deprecated `shouldOverrideUrlLoading(view, url: String?)` overload is
 *    not used. The WebResourceRequest variant is the modern API and exposes
 *    main-frame information needed to make safe routing decisions.
 *
 * @param url The URL to load in the WebView.
 * @param successRedirectUrl Prefix that signals a successful checkout.
 * @param failureRedirectUrl Prefix that signals a failed or cancelled checkout.
 * @param allowedHosts Hosts that are allowed to render inside the WebView. The
 *   host of [url] is always added implicitly.
 * @param onComplete Invoked once when the success URL is reached.
 * @param onError Invoked when the failure URL is reached or a load error fires.
 * @param onExternalNavigation Invoked when a navigation targets a host that is
 *   not on the allow-list. The host app should typically launch an intent.
 */
@Composable
fun CheckoutWebView(
    url: String,
    successRedirectUrl: String,
    failureRedirectUrl: String,
    allowedHosts: Set<String> = emptySet(),
    onComplete: () -> Unit,
    onError: (String) -> Unit,
    onExternalNavigation: (String) -> Unit = {}
) {
    val initialHost = Uri.parse(url).host.orEmpty()
    val effectiveAllowed = (allowedHosts + initialHost).filter { it.isNotBlank() }.toSet()

    AndroidView(factory = { context ->
        WebView(context).apply {
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                setSupportMultipleWindows(false)
                allowFileAccess = false
                allowContentAccess = false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
                setGeolocationEnabled(false)
                javaScriptCanOpenWindowsAutomatically = false
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val target = request?.url?.toString() ?: return false
                    if (target.startsWith(successRedirectUrl)) {
                        onComplete()
                        return true
                    }
                    if (target.startsWith(failureRedirectUrl)) {
                        onError("Payment failed or cancelled.")
                        return true
                    }
                    val host = request.url.host.orEmpty()
                    val scheme = request.url.scheme.orEmpty().lowercase()
                    val isWebScheme = scheme == "https" || scheme == "http"
                    if (!isWebScheme || (effectiveAllowed.isNotEmpty() && host !in effectiveAllowed)) {
                        onExternalNavigation(target)
                        return true
                    }
                    return false
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    onError("WebView error: $description (code $errorCode)")
                }
            }
            loadUrl(url)
        }
    })
}

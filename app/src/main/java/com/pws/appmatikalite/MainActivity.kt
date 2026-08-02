package com.pws.appmatikalite

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pws.appmatikalite.ui.theme.AppmatikaliteTheme

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    
    // File upload callback
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    
    // Geolocation callbacks
    private var geolocationCallback: GeolocationPermissions.Callback? = null
    private var geolocationOrigin: String? = null
    
    // WebRTC Permission callback
    private var permissionRequest: PermissionRequest? = null

    // Register file chooser activity launcher
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val intent = result.data
        val results = if (result.resultCode == Activity.RESULT_OK) {
            if (intent?.dataString != null) {
                arrayOf(Uri.parse(intent.dataString))
            } else if (intent?.clipData != null) {
                val clip = intent.clipData!!
                Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
            } else {
                null
            }
        } else {
            null
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    // Register location permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        geolocationCallback?.invoke(geolocationOrigin, granted, false)
        geolocationCallback = null
        geolocationOrigin = null
    }

    // Register camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionRequest?.grant(permissionRequest?.resources ?: emptyArray())
        } else {
            permissionRequest?.deny()
        }
        permissionRequest = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle hardware back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webView
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        setContent {
            AppmatikaliteTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    var showSplashScreen by remember { mutableStateOf(true) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        WebViewScreen(
                            url = "https://siswa.appmatikalite.com/",
                            onWebViewCreated = { wv ->
                                webView = wv
                            },
                            onPageLoaded = {
                                showSplashScreen = false
                            },
                            onShowFileChooser = { callback, params ->
                                filePathCallback = callback
                                try {
                                    val intent = params.createIntent()
                                    fileChooserLauncher.launch(intent)
                                    true
                                } catch (e: Exception) {
                                    filePathCallback?.onReceiveValue(null)
                                    filePathCallback = null
                                    false
                                }
                            },
                            onRequestCameraPermission = { request ->
                                permissionRequest = request
                                val hasCameraPermission = ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                
                                if (hasCameraPermission) {
                                    request.grant(request.resources)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            onRequestLocationPermission = { origin, callback ->
                                geolocationOrigin = origin
                                geolocationCallback = callback
                                
                                val hasFineLocation = ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                val hasCoarseLocation = ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                
                                if (hasFineLocation || hasCoarseLocation) {
                                    callback.invoke(origin, true, false)
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        )

                        AnimatedVisibility(
                            visible = showSplashScreen,
                            exit = fadeOut(animationSpec = tween(durationMillis = 500))
                        ) {
                            SplashScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logoapk2),
                contentDescription = "Logo",
                modifier = Modifier.size(180.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color(0xFF1270E4), // Match the primary color of the logo (#1270E4)
                strokeWidth = 3.dp
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit,
    onPageLoaded: () -> Unit,
    onShowFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
    onRequestCameraPermission: (PermissionRequest) -> Unit,
    onRequestLocationPermission: (String, GeolocationPermissions.Callback) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isError by remember { mutableStateOf(false) }
    var errorDescription by remember { mutableStateOf("") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isFirstLoad by remember { mutableStateOf(true) }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (!isError) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Configure web settings
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            cacheMode = WebSettings.LOAD_DEFAULT
                            
                            // Custom App User Agent
                            userAgentString = "$userAgentString AppmatikaliteAndroid"
                        }

                        // Configure WebViewClient
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                isError = false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                if (isFirstLoad) {
                                    isFirstLoad = false
                                    onPageLoaded()
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                // Show custom error screen only if it's the main frame load failing
                                if (request?.isForMainFrame == true) {
                                    isError = true
                                    errorDescription = error?.description?.toString() ?: "No Internet Connection"
                                    isLoading = false
                                    if (isFirstLoad) {
                                        isFirstLoad = false
                                        onPageLoaded()
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val reqUrl = request?.url?.toString() ?: return false
                                
                                // Direct deep links (e.g. WhatsApp, phone, mail, external apps) to custom intent
                                if (reqUrl.startsWith("mailto:") ||
                                    reqUrl.startsWith("tel:") ||
                                    reqUrl.startsWith("whatsapp:") ||
                                    reqUrl.startsWith("intent:")
                                ) {
                                    try {
                                        val intent = Intent.parseUri(reqUrl, Intent.URI_INTENT_SCHEME)
                                        view?.context?.startActivity(intent)
                                        return true
                                    } catch (e: Exception) {
                                        if (reqUrl.startsWith("whatsapp:")) {
                                            try {
                                                val webIntent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://api.whatsapp.com/send?phone=" + reqUrl.substringAfter("phone="))
                                                )
                                                view?.context?.startActivity(webIntent)
                                                return true
                                            } catch (ex: Exception) {
                                                // ignore
                                            }
                                        }
                                        return true
                                    }
                                }
                                return false
                            }
                        }

                        // Configure WebChromeClient
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                progress = newProgress / 100f
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                if (filePathCallback != null && fileChooserParams != null) {
                                    return onShowFileChooser(filePathCallback, fileChooserParams)
                                }
                                return false
                            }

                            override fun onPermissionRequest(request: PermissionRequest?) {
                                if (request != null) {
                                    onRequestCameraPermission(request)
                                }
                            }

                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: GeolocationPermissions.Callback?
                            ) {
                                if (origin != null && callback != null) {
                                    onRequestLocationPermission(origin, callback)
                                }
                            }
                        }

                        // Cookie support
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webViewInstance = this
                        onWebViewCreated(this)
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    webViewInstance = it
                }
            )
        } else {
            // Elegant error screen when connection/loading fails
            ErrorScreen(
                errorMessage = errorDescription,
                onRetry = {
                    isError = false
                    isLoading = true
                    webViewInstance?.reload()
                }
            )
        }

        // Linear Progress bar at the top of the WebView
        if (isLoading && !isError) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
fun ErrorScreen(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sleek loading failure illustration / icon placeholder
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.error,
                strokeWidth = 3.dp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Koneksi Bermasalah",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Gagal memuat halaman: $errorMessage. Pastikan perangkat Anda terhubung ke internet.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = "Coba Lagi",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
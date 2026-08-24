package com.grabvid.downloader;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ProgressBar progressBar;
    private TextView loadingText;
    private PHPWebServer phpServer;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        loadingText = findViewById(R.id.loadingText);

        // طلب الأذونات
        checkPermissions();

        // إظهار شاشة التحميل
        showLoading("جاري تهيئة التطبيق...");

        // نسخ الملفات في خلفية
        new Thread(this::extractAssets).start();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET
            };

            boolean allGranted = true;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void extractAssets() {
        try {
            String[] folders = {
                "www",
                "www/css",
                "www/js",
                "bin/" + getArchitecture()
            };

            for (String folder : folders) {
                File dir = new File(getFilesDir(), folder);
                dir.mkdirs();
            }

            // نسخ ملفات الموقع
            String[] wwwFiles = {
                "www/index.html",
                "www/api.php",
                "www/css/style.css",
                "www/js/script.js"
            };

            for (String file : wwwFiles) {
                copyAsset(file);
            }

            // نسخ الملفات الثنائية حسب المعمارية
            String arch = getArchitecture();
            String[] binFiles = {
                "php",
                "ffmpeg",
                "yt-dlp"
            };

            for (String file : binFiles) {
                String assetPath = "bin/" + arch + "/" + file;
                String destPath = getFilesDir() + "/bin/" + file;
                copyBinary(assetPath, destPath);
            }

            // نسخ php.ini
            copyAsset("php.ini");

            runOnUiThread(this::setupWebView);

        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> {
                Toast.makeText(this, "خطأ في تهيئة الملفات", Toast.LENGTH_LONG).show();
                finish();
            });
        }
    }

    private void copyAsset(String assetPath) {
        try {
            File outFile = new File(getFilesDir(), assetPath);
            outFile.getParentFile().mkdirs();

            if (!outFile.exists()) {
                InputStream in = getAssets().open(assetPath);
                OutputStream out = new FileOutputStream(outFile);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                in.close();
                out.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyBinary(String assetPath, String destPath) {
        try {
            File outFile = new File(destPath);
            outFile.getParentFile().mkdirs();

            InputStream in = getAssets().open(assetPath);
            OutputStream out = new FileOutputStream(outFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();

            // جعل الملف قابلاً للتنفيذ
            outFile.setExecutable(true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getArchitecture() {
        String arch = Build.SUPPORTED_ABIS[0];
        if (arch.contains("arm64")) {
            return "arm64-v8a";
        } else if (arch.contains("armeabi")) {
            return "armeabi-v7a";
        } else if (arch.contains("x86_64")) {
            return "x86_64";
        } else {
            return "x86";
        }
    }

    private void setupWebView() {
        showLoading("جاري تشغيل الخادم...");

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAppCacheEnabled(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                hideLoading();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                hideLoading();
                Toast.makeText(MainActivity.this, "خطأ في تحميل الصفحة", Toast.LENGTH_SHORT).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    showLoading("جاري التحميل " + newProgress + "%");
                }
            }
        });

        // تشغيل خادم PHP
        startPHPServer();
    }

    private void startPHPServer() {
        String documentRoot = getFilesDir().getAbsolutePath();
        String arch = getArchitecture();
        String phpPath = documentRoot + "/bin/" + arch + "/php";

        showLoading("بدء تشغيل خادم PHP...");

        phpServer = new PHPWebServer(documentRoot, phpPath);
        phpServer.start();

        // انتظار الخادم
        mainHandler.postDelayed(() -> {
            // تحميل الموقع
            webView.loadUrl("http://127.0.0.1:8080/index.html");
        }, 3000);
    }

    private void showLoading(String message) {
        runOnUiThread(() -> {
            progressBar.setVisibility(ProgressBar.VISIBLE);
            loadingText.setVisibility(TextView.VISIBLE);
            loadingText.setText(message);
        });
    }

    private void hideLoading() {
        runOnUiThread(() -> {
            progressBar.setVisibility(ProgressBar.GONE);
            loadingText.setVisibility(TextView.GONE);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (phpServer != null) {
            phpServer.stopServer();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
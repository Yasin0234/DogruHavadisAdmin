package com.korkutsoftware.doruhavadisadmin;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.korkutsoftware.doruhavadisadmin.login.AddProfileActivity;
import com.korkutsoftware.doruhavadisadmin.login.LoginActivity;
import com.korkutsoftware.doruhavadisadmin.login.MaintenanceActivity;
import com.korkutsoftware.doruhavadisadmin.login.UpdateActivity;

public class MainActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ProgressBar progressBar;
    private TextView footerText;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance("dogruhavadis");
        mAuth = FirebaseAuth.getInstance();
        progressBar = findViewById(R.id.progressBar);
        footerText = findViewById(R.id.footerText);

        setupFooter();
        checkAppStatus();
    }

    private void setupFooter() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            String copyrightText = "© 2026 Korkut Yazılım ve Medya A.Ş. Doğru Havadis Admin Paneli Tüm Hakları Saklıdır. V" + version;
            footerText.setText(copyrightText);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void checkAppStatus() {
        animateProgress(70, 1000);
        db.collection("configAdm").document("adminBakim")
                .get()
                .addOnCompleteListener(task -> {
                    animateProgress(100, 500);
                    // Kısa bir gecikme ile ekran geçişi yapıyoruz ki 100% görülsün
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                boolean bakimDurumu = document.getBoolean("bakimDurumu") != null && document.getBoolean("bakimDurumu");
                                String minVersiyon = document.getString("minVersiyon");
                                String guncellemeUrl = document.getString("guncellemeUrl");
                                String bakimBaslik = document.getString("bakimBaslik");
                                String bakimDetay = document.getString("bakimDetay");
                                String bakimTarih = document.getString("bakimTarih");

                                // Bakım Kontrolü
                                if (bakimDurumu) {
                                    goToMaintenance(bakimBaslik, bakimDetay, bakimTarih);
                                    return;
                                }

                                // Versiyon Kontrolü
                                if (minVersiyon != null && isUpdateRequired(minVersiyon)) {
                                    goToUpdate(minVersiyon, guncellemeUrl);
                                    return;
                                }

                                // Normal Akış - Oturum Kontrolü
                                checkUserAuth();
                            } else {
                                Log.e(TAG, "Belge bulunamadı");
                                checkUserAuth();
                            }
                        } else {
                            Exception e = task.getException();
                            if (e instanceof FirebaseFirestoreException &&
                                    ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.UNAVAILABLE) {
                                Log.w(TAG, "Cihaz çevrimdışı veya sunucuya ulaşılamıyor, giriş ekranına yönlendiriliyor.");
                            } else {
                                Log.e(TAG, "Firestore hatası: ", e);
                                Toast.makeText(this, "Bağlantı hatası!", Toast.LENGTH_SHORT).show();
                            }
                            checkUserAuth();
                        }
                    }, 600);
                });
    }

    private void checkUserAuth() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            goToLogin();
        } else {
            checkUserProfile(currentUser.getUid());
        }
    }

    private void checkUserProfile(String uid) {
        db.collection("users").document(uid).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            Boolean hasProfile = document.getBoolean("hasProfile");
                            if (hasProfile != null && hasProfile) {
                                goToDashboard();
                            } else {
                                goToAddProfile();
                            }
                        } else {
                            // Kullanıcı dökümanı yoksa profil oluşturmaya gönder
                            goToAddProfile();
                        }
                    } else {
                        // Hata durumunda (offline vb.) Login'e düşür veya Dashboard'a güvenle yönlendir
                        goToLogin();
                    }
                });
    }

    private void goToDashboard() {
        Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
        startActivity(intent);
        finish();
    }

    private void goToAddProfile() {
        Intent intent = new Intent(MainActivity.this, AddProfileActivity.class);
        startActivity(intent);
        finish();
    }

    private void animateProgress(int toProgress, int duration) {
        ObjectAnimator.ofInt(progressBar, "progress", progressBar.getProgress(), toProgress)
                .setDuration(duration)
                .start();
    }

    private boolean isUpdateRequired(String minVersion) {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String currentVersion = pInfo.versionName;
            
            // Basit string karşılaştırması (daha karmaşık versiyonlama için geliştirilebilir)
            return currentVersion.compareTo(minVersion) < 0;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void goToMaintenance(String title, String detail, String date) {
        Intent intent = new Intent(MainActivity.this, MaintenanceActivity.class);
        intent.putExtra("title", title);
        intent.putExtra("detail", detail);
        intent.putExtra("date", date);
        startActivity(intent);
        finish();
    }

    private void goToUpdate(String minVersion, String updateUrl) {
        Intent intent = new Intent(MainActivity.this, UpdateActivity.class);
        intent.putExtra("minVersion", minVersion);
        if (updateUrl != null && !updateUrl.isEmpty()) {
            intent.putExtra("updateUrl", updateUrl);
        }
        startActivity(intent);
        finish();
    }

    private void goToLogin() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }, 1000);
    }
}

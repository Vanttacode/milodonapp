package com.milodon.vmc;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.milodon.vmc.hardware.MotorManager;
import com.milodon.vmc.hardware.PaymentManager;
import com.milodon.vmc.services.FloatService;
import com.squareup.picasso.Picasso;

import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "MAIN_ACTIVITY";

    private TextView tvStatus;
    private GridView gvProducts;
    private ProductAdapter productAdapter;
    private SupabaseClient supabaseClient;

    private ImageView ivLoadingStickers;
    private AnimationDrawable stickerAnimation;

    private int hiddenTapCount = 0;
    private long lastTapTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // CORRECCIÓN PANTALLA COMPLETA: Forzar remoción de barra de título a nivel hardware antes del layout
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setupKioskMode();
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        gvProducts = findViewById(R.id.gvProducts);
        ivLoadingStickers = findViewById(R.id.ivLoadingStickers);

        supabaseClient = new SupabaseClient();

        productAdapter = new ProductAdapter(new ArrayList<>());
        gvProducts.setAdapter(productAdapter);

        setupHiddenAdminButton();
        initHardwareInBackground();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (android.provider.Settings.canDrawOverlays(this)) {
                startService(new Intent(this, FloatService.class));
            } else {
                Log.w(TAG, "Permiso de superposición ausente. Registrando salto a la configuración.");
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } else {
            startService(new Intent(this, FloatService.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupKioskMode();

        if (stickerAnimation != null && !stickerAnimation.isRunning()) {
            stickerAnimation.start();
        }

        loadInventory();
    }

    private void initHardwareInBackground() {
        new Thread(() -> {
            Process suProcess = null;
            DataOutputStream os = null;
            try {
                suProcess = Runtime.getRuntime().exec("su");
                os = new DataOutputStream(suProcess.getOutputStream());
                os.writeBytes("pm disable com.vendor.self.system\n");
                os.writeBytes("setenforce 0\n");
                os.writeBytes("exit\n");
                os.flush();
                suProcess.waitFor();

                Log.i(TAG, "Privilegios SElinux y desactivación de Vendor completados.");
                MotorManager.getInstance().init();
                PaymentManager.getInstance().init(getApplicationContext());

            } catch (Exception e) {
                Log.e(TAG, "Fallo crítico en la inicialización nativa de hardware/root", e);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed() && tvStatus != null) {
                        tvStatus.setText("ERROR CRÍTICO: Hardware Inaccesible");
                        tvStatus.setTextColor(Color.RED);
                    }
                });
            } finally {
                try {
                    if (os != null) os.close();
                    if (suProcess != null) suProcess.destroy();
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void setupHiddenAdminButton() {
        ImageView ivLogo = findViewById(R.id.ivLogo);
        if (ivLogo == null) return;

        ivLogo.setOnClickListener(v -> {
            long currentTime = SystemClock.elapsedRealtime();
            if (currentTime - lastTapTime > 1000) hiddenTapCount = 0;
            lastTapTime = currentTime;
            hiddenTapCount++;

            if (hiddenTapCount >= 5) {
                hiddenTapCount = 0;
                startActivity(new Intent(MainActivity.this, com.milodon.vmc.admin.AdminLoginActivity.class));
            }
        });
    }

    private void setupKioskMode() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    private void loadInventory() {
        if (tvStatus != null) {
            tvStatus.setText("Sincronizando inventario...");
            tvStatus.setTextColor(Color.WHITE);
        }

        if (ivLoadingStickers != null) {
            ivLoadingStickers.setVisibility(View.VISIBLE);
            stickerAnimation = (AnimationDrawable) ivLoadingStickers.getDrawable();
            if (stickerAnimation != null) {
                ivLoadingStickers.post(() -> stickerAnimation.start());
            }
        }

        supabaseClient.fetchProducts(new SupabaseClient.ProductsCallback() {
            @Override
            public void onSuccess(List<SupabaseClient.Product> products) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (tvStatus != null) {
                        tvStatus.setText("SELECCIONE UN PRODUCTO");
                    }
                    if (ivLoadingStickers != null && stickerAnimation != null) {
                        stickerAnimation.stop();
                        ivLoadingStickers.setVisibility(View.GONE);
                    }

                    productAdapter.updateProducts(products);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (tvStatus != null) {
                        tvStatus.setText("Error al cargar catálogo remoto");
                        tvStatus.setTextColor(Color.RED);
                    }
                    if (ivLoadingStickers != null && stickerAnimation != null) {
                        stickerAnimation.stop();
                        ivLoadingStickers.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void purchaseProduct(SupabaseClient.Product product) {
        try {
            int finalPrice = product.price;
            Log.i(TAG, "Gatillando solicitud de compra. SKU: " + product.sku + " -> Precio Int: " + finalPrice);

            Intent paymentIntent = new Intent(MainActivity.this, PaymentActivity.class);
            paymentIntent.putExtra("AMOUNT", finalPrice);
            paymentIntent.putExtra("SKU", product.sku);
            startActivity(paymentIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error fatal en el puente de Intent", e);
            Toast.makeText(this, "Fallo al procesar la selección del producto", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (stickerAnimation != null && stickerAnimation.isRunning()) {
            stickerAnimation.stop();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setupKioskMode();
    }

    private class ProductAdapter extends BaseAdapter {
        private List<SupabaseClient.Product> products;

        public ProductAdapter(List<SupabaseClient.Product> products) {
            this.products = products;
        }

        public void updateProducts(List<SupabaseClient.Product> products) {
            this.products = products;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() { return products.size(); }
        @Override public Object getItem(int position) { return products.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_product, parent, false);
                holder = new ViewHolder();
                holder.ivImage = convertView.findViewById(R.id.ivProductImage);
                holder.tvName = convertView.findViewById(R.id.tvProductName);
                holder.tvPrice = convertView.findViewById(R.id.tvProductPrice);
                holder.btnBuy = convertView.findViewById(R.id.btnBuy);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final SupabaseClient.Product product = products.get(position);

            if (holder.tvName != null) {
                holder.tvName.setText(product.name);
            }
            if (holder.tvPrice != null) {
                holder.tvPrice.setText("$" + product.price);
            }

            if (holder.ivImage != null && product.imageUrl != null && !product.imageUrl.isEmpty()) {
                Picasso.get().load(product.imageUrl).placeholder(android.R.color.transparent).into(holder.ivImage);
            }

            if (holder.btnBuy != null) {
                boolean canBuy = product.isActive && product.stock > 0;
                holder.btnBuy.setEnabled(canBuy);

                if (!canBuy) {
                    holder.btnBuy.setText("Agotado");
                    holder.btnBuy.setOnClickListener(null);
                } else {
                    holder.btnBuy.setText("Comprar");
                    holder.btnBuy.setOnClickListener(v -> {
                        if (SystemClock.elapsedRealtime() - lastTapTime < 1500) {
                            return;
                        }
                        lastTapTime = SystemClock.elapsedRealtime();
                        holder.btnBuy.setText("Espere...");
                        purchaseProduct(product);
                    });
                }
            }
            return convertView;
        }

        private class ViewHolder {
            ImageView ivImage;
            TextView tvName;
            TextView tvPrice;
            Button btnBuy;
        }
    }
}
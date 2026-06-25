package com.milodon.vmc;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseClient {

    private static final String TAG = "SupabaseClient";
    private final OkHttpClient client;
    private final Handler mainHandler;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public SupabaseClient() {
        this.client = getUnsafeOkHttpClient();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                        @Override
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // --- CLASE RESTAURADA: Resuelve el error 'cannot find symbol class Product' ---
    public static class Product {
        public String sku;
        public String name;
        public int price;
        public int stock;
        public boolean isActive;
        public String imageUrl;
    }

    public interface ProductsCallback {
        void onSuccess(List<Product> products);
        void onError(Exception e);
    }

    public interface TransactionCallback {
        void onSuccess();
        void onError(Exception e);
    }

    /**
     * Descarga el inventario plano directo desde tu tabla milodon.products
     */
    public void fetchProducts(final ProductsCallback callback) {
        String finalUrl = BuildConfig.SUPABASE_URL + "/rest/v1/products?select=*&stock=gt.0&is_active=eq.true&order=sku";

        Request request = new Request.Builder()
                .url(finalUrl)
                .addHeader("apikey", BuildConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_KEY)
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Profile", "milodon")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Excepción de red al solicitar catálogo plano", e);
                mainHandler.post(() -> callback.onError(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Supabase denegó el catálogo plano. Código HTTP: " + response.code());
                    mainHandler.post(() -> callback.onError(new IOException("Error de servidor: " + response.code())));
                    return;
                }

                try {
                    String responseData = response.body().string();
                    JSONArray jsonArray = new JSONArray(responseData);
                    List<Product> productList = new ArrayList<>();

                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);

                        Product product = new Product();
                        product.sku = obj.optString("sku");
                        product.name = obj.optString("name");
                        product.price = obj.optInt("price", 0);
                        product.stock = obj.optInt("stock", 0);
                        product.isActive = obj.optBoolean("is_active", true);
                        product.imageUrl = obj.optString("image_url", null);

                        productList.add(product);
                    }

                    mainHandler.post(() -> callback.onSuccess(productList));

                } catch (Exception e) {
                    Log.e(TAG, "Fallo al deserializar el JSON de productos", e);
                    mainHandler.post(() -> callback.onError(e));
                }
            }
        });
    }

    /**
     * Descuenta una unidad del stock local de la tabla products
     */
    public void descontarStock(String sku, final TransactionCallback callback) {
        String finalUrl = BuildConfig.SUPABASE_URL + "/rest/v1/rpc/subtract_product_stock";

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("target_sku", sku);
        } catch (Exception e) {
            callback.onError(e);
            return;
        }

        RequestBody body = RequestBody.create(JSON, jsonBody.toString());
        Request request = new Request.Builder()
                .url(finalUrl)
                .addHeader("apikey", BuildConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Content-Profile", "milodon")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e));
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "Stock del producto decrementado en Supabase para el SKU: " + sku);
                    mainHandler.post(callback::onSuccess);
                } else {
                    mainHandler.post(() -> callback.onError(new IOException("Error de persistencia: " + response.code())));
                }
                response.close();
            }
        });
    }

    /**
     * Inserta logs financieros directos en la tabla transactions
     */
    public void insertTransaction(int amount, String status, String sku, final TransactionCallback callback) {
        String finalUrl = BuildConfig.SUPABASE_URL + "/rest/v1/transactions";

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("sku", sku);
            jsonObject.put("amount", amount);
            jsonObject.put("status", status);
            jsonObject.put("payment_method", "MDB_CASHLESS");
        } catch (Exception e) {
            callback.onError(e);
            return;
        }

        RequestBody body = RequestBody.create(JSON, jsonObject.toString());
        Request request = new Request.Builder()
                .url(finalUrl)
                .addHeader("apikey", BuildConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .addHeader("Content-Profile", "milodon")
                .addHeader("Accept-Profile", "milodon")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e));
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "Venta guardada exitosamente en la telemetría central.");
                    mainHandler.post(callback::onSuccess);
                } else {
                    mainHandler.post(() -> callback.onError(new IOException("HTTP " + response.code())));
                }
                response.close();
            }
        });
    }
}
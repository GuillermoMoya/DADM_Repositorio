package co.edu.unal.reto9_gps.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente mínimo de la Overpass API de OpenStreetMap.
 *
 * <p>Construye una consulta {@code around:radio} para las categorías activas y
 * devuelve la lista de {@link Poi} encontrados. Todo el trabajo de red corre en
 * un hilo de fondo; el resultado se entrega en el hilo principal mediante
 * {@link Callback}.</p>
 */
public class OverpassClient {

    private static final String TAG = "OverpassClient";

    /** Endpoint público de Overpass (sin API key). */
    private static final String ENDPOINT = "https://overpass-api.de/api/interpreter";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    /** Resultado de la consulta, siempre invocado en el hilo principal. */
    public interface Callback {
        void onSuccess(@NonNull List<Poi> pois);

        void onError(@NonNull Exception error);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Busca los POI de las categorías indicadas alrededor de (lat, lon).
     *
     * @param lat          latitud del centro de búsqueda
     * @param lon          longitud del centro de búsqueda
     * @param radiusMeters radio de búsqueda en metros
     * @param categories   categorías a incluir (si está vacío, no se consulta)
     * @param callback     entrega el resultado en el hilo principal
     */
    public void search(double lat, double lon, int radiusMeters,
                       @NonNull List<PoiCategory> categories,
                       @NonNull Callback callback) {

        if (categories.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            return;
        }

        executor.execute(() -> {
            try {
                String query = buildQuery(lat, lon, radiusMeters, categories);
                String json = post(query);
                List<Poi> pois = parse(json);
                mainHandler.post(() -> callback.onSuccess(pois));
            } catch (Exception e) {
                Log.e(TAG, "Fallo consultando Overpass", e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    // ------------------------------------------------------------- consulta

    /**
     * Arma una consulta Overpass QL. Se usa {@code nwr} (node/way/relation) con
     * {@code out center} para que ways y relations también traigan coordenadas.
     */
    private String buildQuery(double lat, double lon, int radiusMeters,
                              @NonNull List<PoiCategory> categories) {
        StringBuilder sb = new StringBuilder();
        sb.append("[out:json][timeout:25];(");
        for (PoiCategory c : categories) {
            sb.append(String.format(Locale.US,
                    "nwr[\"amenity\"=\"%s\"](around:%d,%f,%f);",
                    c.getAmenityTag(), radiusMeters, lat, lon));
        }
        sb.append(");out center tags;");
        return sb.toString();
    }

    private String post(@NonNull String query) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            byte[] body = ("data=" + query).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 400
                    ? conn.getInputStream() : conn.getErrorStream();
            String response = readAll(in);

            if (code < 200 || code >= 400) {
                throw new IllegalStateException("HTTP " + code + ": " + response);
            }
            return response;
        } finally {
            conn.disconnect();
        }
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------- parseo

    private List<Poi> parse(@NonNull String json) throws Exception {
        List<Poi> pois = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray elements = root.optJSONArray("elements");
        if (elements == null) {
            return pois;
        }

        for (int i = 0; i < elements.length(); i++) {
            JSONObject el = elements.optJSONObject(i);
            if (el == null) {
                continue;
            }

            JSONObject tags = el.optJSONObject("tags");
            String amenity = tags == null ? null : tags.optString("amenity", null);
            PoiCategory category = PoiCategory.fromAmenity(amenity);
            if (category == null) {
                continue;
            }

            // Nodos traen lat/lon directo; ways/relations traen "center".
            double lat;
            double lon;
            if (el.has("lat") && el.has("lon")) {
                lat = el.optDouble("lat");
                lon = el.optDouble("lon");
            } else {
                JSONObject center = el.optJSONObject("center");
                if (center == null) {
                    continue;
                }
                lat = center.optDouble("lat");
                lon = center.optDouble("lon");
            }
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                continue;
            }

            String name = tags == null ? "" : tags.optString("name", "");
            pois.add(new Poi(name, category, lat, lon));
        }
        return pois;
    }

    /** Libera el executor. Llamar en {@code onDestroy}. */
    public void shutdown() {
        executor.shutdownNow();
    }
}

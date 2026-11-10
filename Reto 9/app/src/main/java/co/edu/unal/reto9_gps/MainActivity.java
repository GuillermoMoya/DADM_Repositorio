package co.edu.unal.reto9_gps;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.List;
import java.util.Locale;

import co.edu.unal.reto9_gps.data.OverpassClient;
import co.edu.unal.reto9_gps.data.Poi;
import co.edu.unal.reto9_gps.data.PoiCategory;
import co.edu.unal.reto9_gps.data.Prefs;
import co.edu.unal.reto9_gps.ui.MarkerIcons;

/**
 * Pantalla principal del Reto 9.
 *
 * <p>Muestra un mapa OpenStreetMap (osmdroid) centrado en la ubicación GPS del
 * usuario y coloca marcadores con el nombre de los puntos de interés cercanos
 * (hospitales, farmacias, universidades) dentro del radio configurado en
 * preferencias. Los POI se obtienen de la Overpass API de OSM.</p>
 */
public class MainActivity extends AppCompatActivity {

    private static final double DEFAULT_ZOOM = 15.0;

    private MapView map;
    private ProgressBar progress;
    private TextView radiusChip;

    private FusedLocationProviderClient locationClient;
    private final OverpassClient overpass = new OverpassClient();

    /** Marcador de la ubicación actual del usuario. */
    private Marker userMarker;
    /** Última ubicación conocida del usuario. */
    private GeoPoint lastLocation;

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // osmdroid debe configurarse ANTES de inflar la vista del mapa.
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(
                ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_main);

        map = findViewById(R.id.map);
        progress = findViewById(R.id.progress);
        radiusChip = findViewById(R.id.radiusChip);

        setupMap();

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = Boolean.TRUE.equals(
                            result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                            || Boolean.TRUE.equals(
                            result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (granted) {
                        fetchLocationAndSearch(true);
                    } else {
                        toast(getString(R.string.perm_denied));
                    }
                });

        findViewById(R.id.fabSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        ((FloatingActionButton) findViewById(R.id.fabMyLocation))
                .setOnClickListener(v -> ensureLocationThen(() -> fetchLocationAndSearch(true)));

        ((ExtendedFloatingActionButton) findViewById(R.id.fabSearch))
                .setOnClickListener(v -> ensureLocationThen(() -> fetchLocationAndSearch(false)));
    }

    private void setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(DEFAULT_ZOOM);
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        updateRadiusChip();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        overpass.shutdown();
    }

    // ----------------------------------------------------------- permisos

    /** Si ya hay permiso, ejecuta la acción; si no, lo solicita. */
    private void ensureLocationThen(@NonNull Runnable action) {
        if (hasLocationPermission()) {
            action.run();
            return;
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.perm_rationale_title)
                    .setMessage(R.string.perm_rationale_message)
                    .setPositiveButton(android.R.string.ok, (d, w) -> requestLocationPermission())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            requestLocationPermission();
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        permissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    // -------------------------------------------------------- ubicación GPS

    /**
     * Obtiene la ubicación actual del usuario y luego busca los POI.
     *
     * @param recenter si {@code true}, mueve la cámara a la ubicación obtenida.
     */
    @SuppressWarnings("MissingPermission") // Se verifica en ensureLocationThen.
    private void fetchLocationAndSearch(boolean recenter) {
        if (!hasLocationPermission()) {
            ensureLocationThen(() -> fetchLocationAndSearch(recenter));
            return;
        }

        showLoading(true);
        toast(getString(R.string.locating));

        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build();

        locationClient.getCurrentLocation(request, null)
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        showLoading(false);
                        toast(getString(R.string.location_error));
                        return;
                    }
                    lastLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                    showUserMarker(lastLocation);
                    if (recenter) {
                        map.getController().animateTo(lastLocation);
                        map.getController().setZoom(DEFAULT_ZOOM);
                    }
                    searchPois();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    toast(getString(R.string.location_error));
                });
    }

    private void showUserMarker(@NonNull GeoPoint point) {
        if (userMarker == null) {
            userMarker = new Marker(map);
            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            userMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_user_location));
            userMarker.setTitle(getString(R.string.action_my_location));
            map.getOverlays().add(userMarker);
        }
        userMarker.setPosition(point);
        map.invalidate();
    }

    // --------------------------------------------------------- POI / búsqueda

    private void searchPois() {
        if (lastLocation == null) {
            showLoading(false);
            return;
        }

        List<PoiCategory> categories = Prefs.getEnabledCategories(this);
        int radiusMeters = Prefs.getRadiusMeters(this);
        double radiusKm = Prefs.getRadiusKm(this);

        showLoading(true);
        toast(getString(R.string.searching));

        overpass.search(lastLocation.getLatitude(), lastLocation.getLongitude(),
                radiusMeters, categories, new OverpassClient.Callback() {
                    @Override
                    public void onSuccess(@NonNull List<Poi> pois) {
                        showLoading(false);
                        renderPois(pois);
                        if (pois.isEmpty()) {
                            toast(getString(R.string.no_results));
                        } else {
                            toast(String.format(Locale.getDefault(),
                                    getString(R.string.results_found), pois.size(), radiusKm));
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        showLoading(false);
                        toast(getString(R.string.network_error));
                    }
                });
    }

    /** Limpia los marcadores de POI (conserva el del usuario) y pinta los nuevos. */
    private void renderPois(@NonNull List<Poi> pois) {
        map.getOverlays().clear();
        if (userMarker != null) {
            map.getOverlays().add(userMarker);
        }

        for (Poi poi : pois) {
            Marker marker = new Marker(map);
            marker.setPosition(new GeoPoint(poi.getLatitud(), poi.getLongitud()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(MarkerIcons.forCategory(this, poi.getCategoria()));

            String nombre = poi.getNombre().isEmpty()
                    ? getString(R.string.poi_unnamed) : poi.getNombre();
            // El nombre del POI es requisito del reto: se muestra como título.
            marker.setTitle(nombre);
            marker.setSubDescription(getString(poi.getCategoria().getLabelRes()));
            map.getOverlays().add(marker);
        }
        map.invalidate();
    }

    // ------------------------------------------------------------- helpers

    private void updateRadiusChip() {
        double radiusKm = Prefs.getRadiusKm(this);
        radiusChip.setText(String.format(Locale.getDefault(),
                getString(R.string.radius_chip), radiusKm));
    }

    private void showLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void toast(@NonNull String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

package org.noise_planet.noisecapture;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;


public class MapActivity extends MainActivity implements OnMapReadyCallback,
        GoogleMap.OnMapLoadedCallback {
    public static final String RESULTS_RECORD_ID = "RESULTS_RECORD_ID";
    private MeasurementManager measurementManager;
    private Storage.Record record;
    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        initDrawer();

        this.measurementManager = new MeasurementManager(getApplicationContext());
        Intent intent = getIntent();
        if(intent != null && intent.hasExtra(RESULTS_RECORD_ID)) {
            record = measurementManager.getRecord(intent.getIntExtra(RESULTS_RECORD_ID, -1));
        } else {
            // Read the last stored record
            List<Storage.Record> recordList = measurementManager.getRecords();
            if(!recordList.isEmpty()) {
                record = recordList.get(recordList.size() - 1);
            } else {
                // Message for starting a record
                Toast.makeText(getApplicationContext(), getString(R.string.no_results),
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        // Fill the spinner_map
        Spinner spinner = (Spinner) findViewById(R.id.spinner_map);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.choice_user_map, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Display the map
        setUpMapIfNeeded();
        //WebSettings webSettings = leaflet.getSettings();
        //webSettings.setJavaScriptEnabled(true);
        //leaflet.clearCache(true);
        //leaflet.setInitialScale(200);
        //leaflet.loadUrl("http://webcarto.orbisgis.org/noisemap.html");

    }

    @Override
    public void onMapReady(GoogleMap mMap) {
        // Initialize map options. For example:
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.setOnMapLoadedCallback(this);
        this.mMap = mMap;
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    @Override
    public void onMapLoaded() {
        // Add markers and move the camera.
        List<double[]> latLong = new ArrayList<double[]>();
        List<Double> leqs = new ArrayList<Double>();
        measurementManager.getRecordLocations(record.getId(), latLong, leqs);
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for(int idMarker = 0; idMarker < latLong.size(); idMarker++) {
            double[] p = latLong.get(idMarker);
            LatLng position = new LatLng(p[0], p[1]);
            MarkerOptions marker = new MarkerOptions();
            marker.position(position);
            marker.title(String.format("%.01f dB(A)", leqs.get(idMarker)));
            int nc=getNEcatColors(leqs.get(idMarker));    // Choose the color category in function of the sound level
            float[] hsv = new float[3];
            Color.colorToHSV(NE_COLORS[nc], hsv);  // Apply color category for the corresponding sound level
            marker.icon(BitmapDescriptorFactory.defaultMarker(hsv[0]));
            mMap.addMarker(marker);
            builder.include(position);
        }
        if(!latLong.isEmpty()) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 0));
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.no_gps_results),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void setUpMapIfNeeded() {
        SupportMapFragment mapFragment = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map));
        mapFragment.getMapAsync(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
}

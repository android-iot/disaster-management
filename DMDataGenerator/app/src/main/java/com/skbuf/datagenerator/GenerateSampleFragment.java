package com.skbuf.datagenerator;

import android.graphics.Color;
import android.location.Location;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class GenerateSampleFragment extends Fragment implements GoogleApiClient.ConnectionCallbacks,
        LocationListener,
        GoogleApiClient.OnConnectionFailedListener,
        GoogleMap.OnInfoWindowClickListener,
        GoogleMap.OnMapLongClickListener,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener {

    private static String TAG = "GenerateSampleFragment";

    /* logging */
    File sampleFile;
    BufferedWriter sampleFileWriter;
    FileWriter sampleOutStream;

    Button generateStart, generateSafe, generateRequest;
    MapView mapView;
    Boolean generateStarted = false;

    private static final long INTERVAL = 300; // 300ms
    private static final long FASTEST_INTERVAL = 100; // 100ms
    private static final float SMALLEST_DISPLACEMENT = 0.25F;

    private GoogleApiClient mGoogleApiClient;
    private Location mCurrentLocation;

    private final int[] MAP_TYPES = {
            GoogleMap.MAP_TYPE_NORMAL };
    private int curMapTypeIndex = 0;

    private LocationRequest mLocationRequest;
    private ArrayList<LatLng> points;
    Polyline line;
    private Boolean saveLocation = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //returning our layout file
        return inflater.inflate(R.layout.fragment_generate_sample, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //you can set the title for your toolbar here for different fragments different titles
        getActivity().setTitle(getText(R.string.title_generate_sample));

        generateStart = (Button) getView().findViewById(R.id.button_generate_start);
        generateSafe = (Button) getView().findViewById(R.id.button_generate_safe);
        generateRequest = (Button) getView().findViewById(R.id.button_generate_request);

        mapView = (MapView) view.findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        if (mapView != null) {
            mGoogleApiClient = new GoogleApiClient.Builder( getActivity() )
                    .addConnectionCallbacks( this )
                    .addOnConnectionFailedListener( this )
                    .addApi( LocationServices.API )
                    .build();
            mGoogleApiClient.connect();
            initListeners();
            points = new ArrayList<LatLng>();
            createLocationRequest();
            Log.d(TAG, "onViewCreated");
        }

        generateStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "generate started " + generateStarted);
                if (!generateStarted) {
                    createSampleFile();
                    openSampleFile();

                    try {
                        // write delete messages
                        Message deleteClients = new Message(Message.MSG_TYPE_DELETE_CLIENTS);
                        sampleFileWriter.write(deleteClients.toString() + "\n");

                        Message deleteSafeLocations = new Message(Message.MSG_TYPE_DELETE_SAFE_LOCATIONS);
                        sampleFileWriter.write(deleteSafeLocations.toString() + "\n");

                        // write message of type "friends"
                        Message friendMessage = new Message(Message.MSG_TYPE_FRIENDS,
                                GlobalData.getClientName(),
                                GlobalData.getFriends());
                        sampleFileWriter.write(friendMessage.toString() + "\n");

                        // write message of type "safe-location-preferences"
                        Message prefMessage = new Message(Message.MSG_TYPE_PREF,
                                GlobalData.getClientName(),
                                GlobalData.getCriteria(),
                                GlobalData.getPref());
                        sampleFileWriter.write(prefMessage.toString() + "\n");

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else {
                    try {
                        sampleFileWriter.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                updateState(generateStarted);
            }
        });

        generateSafe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateState(generateStarted);
                try {

                    // write message of type "safe-location"
                    Message safeMessage = new Message(Message.MSG_TYPE_SAFE,
                            GlobalData.getClientName(),
                            System.currentTimeMillis(),
                            GlobalData.getLocation());
                    sampleFileWriter.write(safeMessage.toString() + "\n");

                    sampleFileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        generateRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                try {
                    // write msg of type safe-location-request
                    Message requestMessage = new Message(Message.MSG_TYPE_REQUEST,
                            System.currentTimeMillis(),
                            GlobalData.getClientName());
                    sampleFileWriter.write(requestMessage.toString() + "\n");

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void createSampleFile() {

        try {
            String filePath = Environment.getExternalStorageDirectory() + "/DMDataGenerator-Samples/sample-" +
                    System.currentTimeMillis() + "-" + GlobalData.getClientName();

            sampleFile = new File(filePath);
            sampleFile.getParentFile().mkdirs();
            sampleFile.createNewFile();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openSampleFile() {
        try {
            sampleOutStream = new FileWriter(sampleFile, true);
            sampleFileWriter = new BufferedWriter(sampleOutStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateState(boolean generateStarted) {
        if (!generateStarted) {
            this.generateStart.setText("STOP");
            this.generateSafe.setEnabled(true);
            this.generateStarted = true;
            startLocationUpdates();
        } else {
            this.generateStart.setText("START");
            this.generateSafe.setEnabled(false);
            this.generateStarted = false;
            stopLocationUpdates();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");
        mCurrentLocation = LocationServices
                .FusedLocationApi
                .getLastLocation( mGoogleApiClient );

        if (mCurrentLocation != null )
            initCamera( mCurrentLocation );
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onLocationChanged(Location location) {
        GlobalData.setLocation(location);

        try {
            Message locationUpdateMessage = new Message(Message.MSG_TYPE_LOCATION,
                    GlobalData.getClientName(),
                    System.currentTimeMillis(),
                    location);
            sampleFileWriter.write(locationUpdateMessage.toString() + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        LatLng latLng = new LatLng(latitude, longitude);
        mCurrentLocation = location;

        points.add(latLng);
        redrawLine();
    }

    private void initCamera( Location location ) {
        CameraPosition position = CameraPosition.builder()
                .target( new LatLng( location.getLatitude(),
                        location.getLongitude() ) )
                .zoom( 16f )
                .bearing( 0.0f )
                .tilt( 0.0f )
                .build();

        mapView.getMap().animateCamera( CameraUpdateFactory
                .newCameraPosition( position ), null );

        mapView.getMap().setMapType( MAP_TYPES[curMapTypeIndex] );
        mapView.getMap().setTrafficEnabled( true );
        mapView.getMap().setMyLocationEnabled( true );
        mapView.getMap().getUiSettings().setZoomControlsEnabled( true );
    }

    protected void startLocationUpdates() {
        PendingResult<Status> pendingResult = LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
        mapView.getMap().clear();
    }

    private void redrawLine(){

        mapView.getMap().clear();

        PolylineOptions options = new PolylineOptions().width(10).color(Color.BLUE).geodesic(true);
        for (int i = 0; i < points.size(); i++) {
            LatLng point = points.get(i);
            options.add(point);
        }
        addMarker();
        line = mapView.getMap().addPolyline(options);
    }

    private void addMarker() {
        MarkerOptions options = new MarkerOptions();
        LatLng currentLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());

        options.position(currentLatLng);
        Marker mapMarker = mapView.getMap().addMarker(options);
    }

    @Override
    public void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        if( mGoogleApiClient != null && mGoogleApiClient.isConnected() ) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onResume() {
        mapView.onResume();
        super.onResume();
    }
    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    private void initListeners() {
        mapView.getMap().setOnMarkerClickListener(this);
        mapView.getMap().setOnMapLongClickListener(this);
        mapView.getMap().setOnInfoWindowClickListener( this );
        mapView.getMap().setOnMapClickListener(this);
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setSmallestDisplacement(SMALLEST_DISPLACEMENT);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onInfoWindowClick(Marker marker) {

    }

    @Override
    public void onMapClick(LatLng latLng) {

    }

    @Override
    public void onMapLongClick(LatLng latLng) {

    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        return false;
    }
}
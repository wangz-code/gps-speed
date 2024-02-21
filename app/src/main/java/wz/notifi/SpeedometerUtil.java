package wz.notifi;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

public class SpeedometerUtil implements LocationListener {
    private Context context;
    private LocationManager locationManager;
    private OnSpeedChangeListener speedChangeListener;

    public SpeedometerUtil(Context context) {
        this.context = context;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public void startListeningForSpeedUpdates() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        } else {
            // Handle permissions not granted
        }
    }

    public void stopListeningForSpeedUpdates() {
        locationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (speedChangeListener != null) {
            float speed = location.getSpeed(); // Speed in meters/second
            float speedKmH = speed * 3.6f; // Speed in km/h
            speedChangeListener.onSpeedChanged(speedKmH);
        }
    }

    // Set a listener to listen for speed changes
    public void setOnSpeedChangeListener(OnSpeedChangeListener listener) {
        this.speedChangeListener = listener;
    }

    public interface OnSpeedChangeListener {
        void onSpeedChanged(float speed);
    }

}
package co.mwater.clientapp.ui.map;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import co.mwater.clientapp.R;
import co.mwater.clientapp.db.MWaterContentProvider;
import co.mwater.clientapp.ui.SourceDetailActivity;
import co.mwater.clientapp.ui.map.SourceItemizedOverlay.SourceTapped;

import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;

public class SourceMapActivity extends MapActivity implements SourceTapped {
	MyLocationOverlay locationOverlay;
	MapController mapController;
	Cursor sourceCursor;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		MapView mapView = new MapView(this, "0ASvTqLNwKMHoI5MfnfFGA7QeD4HEzaC3oeyUQA");
		mapView.setClickable(true);
		mapView.setSatellite(true);

		mapController = mapView.getController();

		locationOverlay = new MyLocationOverlay(this, mapView);
		mapView.getOverlays().add(locationOverlay);

//		locationOverlay.runOnFirstFix(new Runnable() {
//			public void run() {
//				mapController.setZoom(16);
//				mapController.animateTo(locationOverlay.getMyLocation());
//			}
//		});

		// TODO move to loader
		sourceCursor = getContentResolver().query(MWaterContentProvider.SOURCES_URI, null, null, null, null);
		SourceItemizedOverlay sourceItemizedOverlay = new SourceItemizedOverlay(
				getResources().getDrawable(R.drawable.marker), sourceCursor, this);
		mapView.getOverlays().add(sourceItemizedOverlay);

		setContentView(mapView);
	}

	@Override
	protected void onDestroy() {
		sourceCursor.close();
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
		locationOverlay.disableMyLocation();
	}

	@Override
	protected void onResume() {
		super.onResume();
		locationOverlay.enableMyLocation();
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	public void onSourceTapped(long id) {
		// Launch source details
		Intent intent = new Intent(this, SourceDetailActivity.class);
		intent.putExtra("uri", Uri.withAppendedPath(MWaterContentProvider.SOURCES_URI, id + ""));
		startActivity(intent);
	}
}

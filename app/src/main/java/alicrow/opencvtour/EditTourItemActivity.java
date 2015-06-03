package alicrow.opencvtour;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.os.Bundle;
import android.app.ActionBar;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

/*
 * Activity to edit an item in a tour.
 */
public class EditTourItemActivity extends Activity implements View.OnClickListener {

	private static final String TAG = "EditTourItemActivity";

	public static int EDIT_TOUR_ITEM_REQUEST = 0x0002;
	public static int REQUEST_IMAGE_CAPTURE = 1;


	short _position_in_tour;    /// Position of this TourItem in the Tour.
	TourItem _tour_item;

	File _photoFile;




	private LocationService mBoundService;

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service.  Because we have bound to a explicit
			// service that we know is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			mBoundService = ((LocationService.LocationServiceBinder)service).getService();
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			// Because it is running in our same process, we should never
			// see this happen.
			mBoundService = null;
		}
	};

	boolean mIsBound = false;

	void doBindService() {
		// Establish a connection with the service.  We use an explicit
		// class name because we want a specific service implementation that
		// we know will be running in our own process (and thus won't be
		// supporting component replacement by other applications).
		bindService(new Intent(this, LocationService.class), mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;
	}

	void doUnbindService() {
		if (mIsBound) {
			// Detach our existing connection.
			unbindService(mConnection);
			mIsBound = false;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		doUnbindService();
	}


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		/// custom action bar
		final LayoutInflater inflater = getLayoutInflater();
		final View actionBarView = inflater.inflate(R.layout.actionbar_custom_view_done, null);

		final ActionBar actionBar = getActionBar();
		actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
		actionBar.setCustomView(actionBarView, new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		actionBarView.findViewById(R.id.actionbar_done).setOnClickListener(this);

		setContentView(R.layout.activity_edit_tour_item);

		/// Find the TourItem we're editing, and display its current values.
		Bundle bundle = getIntent().getExtras();
		_position_in_tour =  bundle.getShort("position");

		_tour_item = Tour.getCurrentTour().getTourItems().get(_position_in_tour);

		((EditText) findViewById(R.id.edit_tour_item_name)).setText(_tour_item.getName());
		((EditText) findViewById(R.id.edit_tour_item_description)).setText(_tour_item.getDescription());
		/// TODO: handle audio
		if(!_tour_item.getImageFilename().equals("")) {
			/// display the image
			Bitmap thumbnail = Utilities.decodeSampledBitmap(_tour_item.getImageFilename(), 128, 128);
			((ImageView) findViewById(R.id.tour_item_thumbnail)).setImageBitmap(thumbnail);
		}

		if(_tour_item.getLocation() != null) {
			Log.i(TAG, "loading GPS coordinates...");
			((TextView) findViewById(R.id.tour_item_location)).setText("location: " + _tour_item.getLocation().getLatitude() + ", " + _tour_item.getLocation().getLongitude());
		}

		findViewById(R.id.image_picker).setOnClickListener(this);
		findViewById(R.id.get_current_gps_location).setOnClickListener(this);

		doBindService();


		if(savedInstanceState != null && savedInstanceState.containsKey("_photoFile_uri")) {
			Log.i(TAG, "attempting to create _photoFile from URI in savedInstanceState");
			_photoFile = new File(URI.create(savedInstanceState.getString("_photoFile_uri")));
		}

	}



	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_edit_tour_item, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
		if (id == R.id.action_settings) {
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	public void applyChanges()
	{
		/// Apply changes and return to previous Activity.
		_tour_item.setName(((EditText) findViewById(R.id.edit_tour_item_name)).getText().toString());
		_tour_item.setDescription( ((EditText) findViewById(R.id.edit_tour_item_description)).getText().toString());
		/// TODO: handle audio and image

		/// finish this Activity
		Bundle data = new Bundle();
		data.putBoolean("item_edited", true);
		Intent intent = new Intent();
		intent.putExtras(data);
		setResult(RESULT_OK, intent);
		finish();
	}


	@Override
	public void onClick(View v) {
		switch(v.getId()) {
			case R.id.image_picker:
				Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
					// Create the File where the photo should go
					_photoFile = null;
					try {
						_photoFile = createImageFile();
					} catch (IOException ex) {
						// Error occurred while creating the File
						Toast.makeText(this, ex.toString(), Toast.LENGTH_SHORT).show();
						Log.e(TAG, ex.toString());
					}
					// Continue only if the File was successfully created
					if (_photoFile != null) {
						takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(_photoFile));
						startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
					} else {
						Log.e(TAG, "_photoFile is null");
					}
				}
				break;

			case R.id.get_current_gps_location:
			{
				Location location = mBoundService.getCurrentLocation();
				if (location == null) {
					Log.e(TAG, "got null current location");
					Toast.makeText(this, "Could not determine location. Make sure you have location enabled on your device, and/or wait a few seconds and try again.", Toast.LENGTH_LONG).show();
				}
				else {
					Toast.makeText(this, location.toString(), Toast.LENGTH_SHORT).show();
					_tour_item.setLocation(location);
					((TextView) findViewById(R.id.tour_item_location)).setText("location: " + _tour_item.getLocation().getLatitude() + ", " + _tour_item.getLocation().getLongitude());
				}
				break;
			}
			case R.id.actionbar_done:
				applyChanges();
		}
	}


	private File createImageFile() throws IOException {
		// Create an image file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = "JPEG_" + timeStamp + "_";
		//File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		return File.createTempFile(
				imageFileName,  /* prefix */
				".jpg",         /* suffix */
				//storageDir      /* directory */
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
		);
	}


	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if(resultCode == Activity.RESULT_OK && requestCode == EditTourItemActivity.REQUEST_IMAGE_CAPTURE)
		{
			_tour_item.setImage(_photoFile.toString());
			if(data != null) {
				data.getExtras();
				data.getExtras().get("data");
				Log.i(TAG, "got a bitmap from camera");
				((ImageView) findViewById(R.id.tour_item_thumbnail)).setImageBitmap((Bitmap) data.getExtras().get("data"));

			} else {
				Toast.makeText(this, "camera didn't return a thumbnail",Toast.LENGTH_SHORT).show();
				Log.i(TAG, "camera didn't return a thumbnail");
				Bitmap thumbnail = Utilities.decodeSampledBitmap(_photoFile.toString(), 128, 128);
				((ImageView) findViewById(R.id.tour_item_thumbnail)).setImageBitmap(thumbnail);
			}

			if(_photoFile == null)
				Log.e(TAG, "_photoFile is null");

			Log.i(TAG, "there should be a picture called " + _photoFile.toURI().toString());
		}
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		Log.i(TAG, "onSaveInstanceState called");

		if(_photoFile != null)
			outState.putString("_photoFile_uri", _photoFile.toURI().toString());
	}


}

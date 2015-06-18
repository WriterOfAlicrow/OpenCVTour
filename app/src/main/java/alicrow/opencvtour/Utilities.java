package alicrow.opencvtour;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * Created by daniel on 6/2/15.
 *
 * Various utility functions
 *
 * Most of the image-loading code was adapted from sample code at http://developer.android.com/training/displaying-bitmaps/index.html, which was licensed under the Apache 2.0 License.
 */
public class Utilities {

	private static final String TAG = "Utilities";

	private static final LruCache<String, Bitmap> _bitmap_cache = new LruCache<>(64);

	/**
	 * Creates and returns a Bitmap of the image at the given filepath, scaled down to fit the area the Bitmap will be displayed in
	 * @param image_file_path location of the image to sample
	 * @param reqWidth width at which the resultant Bitmap will be displayed
	 * @param reqHeight height at which the resultant Bitmap will be displayed
	 * @return a Bitmap large enough to cover the given area
	 */
	public static Bitmap decodeSampledBitmap(String image_file_path, int reqWidth, int reqHeight) {
		Log.v(TAG, "creating bitmap for " + image_file_path);

		final BitmapFactory.Options options = getBitmapBounds(image_file_path);

		options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight);
		options.inJustDecodeBounds = false;
		return fixOrientation(BitmapFactory.decodeFile(image_file_path, options), image_file_path);
	}

	/**
	 * Calculates the sample size to scale the image to be displayed at a given size
	 * @param raw_width raw width of the image
	 * @param raw_height raw height of the image
	 * @param reqWidth the width the image will be displayed at
	 * @param reqHeight the height the image will be displayed at
	 * @return the sample size
	 */
	private static int calculateInSampleSize(int raw_width, int raw_height, int reqWidth, int reqHeight) {
		int inSampleSize = 1;

		if (raw_height > reqHeight || raw_width > reqWidth) {

			final int halfHeight = raw_height / 2;
			final int halfWidth = raw_width / 2;

			// Calculate the largest inSampleSize value that is a power of 2 and keeps both height and width larger than the requested height and width.
			while ((halfHeight / inSampleSize) > reqHeight
					&& (halfWidth / inSampleSize) > reqWidth) {
				inSampleSize *= 2;
			}
		}

		return inSampleSize;
	}

	/**
	 * Retrieves the bounds of the given image
	 * @param filepath path to the image file
	 * @return a BitmapFactory.Options object where outWidth and outHeight are the width and height of the image
	 */
	public static BitmapFactory.Options getBitmapBounds(String filepath) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(filepath, options);

		return options;
	}

	/**
	 * creates a new Bitmap rotated according to the orientation specified in a source file
	 * @param image the Bitmap to rotate
	 * @param image_file_path the file containing the Bitmap's source
	 * @return a new Bitmap with the correct rotation
	 */
	public static Bitmap fixOrientation(Bitmap image, String image_file_path) {
		try {
			/// Retrieve orientation info from the file
			ExifInterface exif = new ExifInterface(image_file_path);
			int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
			float rotation;
			switch (orientation) {
				case ExifInterface.ORIENTATION_NORMAL:
					rotation = 0;
					break;
				case ExifInterface.ORIENTATION_ROTATE_90:
					rotation = 90;
					break;
				case ExifInterface.ORIENTATION_ROTATE_180:
					rotation = 180;
					break;
				case ExifInterface.ORIENTATION_ROTATE_270:
					rotation = 270;
					break;
				default:
					rotation = 0;
					break;
			}
			return rotateImage(image, rotation);
		} catch(IOException e) {
			Log.w(TAG, "Unable to open '" + image_file_path + "' to read orientation");
			return image;
		}
	}

	/**
	 * Returns a rotated version of a Bitmap
	 * @param image the image to rotate
	 * @param angle the amount to rotate the image by, in degrees
	 * @return a new Bitmap with the correct rotation
	 */
	public static Bitmap rotateImage(Bitmap image, float angle) {
		Matrix matrix = new Matrix();
		matrix.postRotate(angle);
		return Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);
	}

	/**
	 * Contains the parameters used to generate a smaller Bitmap from a larger image.
	 */
	public static class ReducedBitmapInfo {
		public String full_image_filename;
		public int width;
		public int height;

		public ReducedBitmapInfo(String filename, int width, int height) {
			this.full_image_filename = filename;
			this.width = width;
			this.height = height;
		}

		/// Using ReducedBitmapInfo as a key in our cache doesn't work (no clue why), so we use Strings instead.
		@Override
		public String toString() {
			return full_image_filename + " " + width + " " + height;
		}
	}

	public static void addToCache(ReducedBitmapInfo info, Bitmap bitmap) {
		if(_bitmap_cache.get(info.toString()) == null) {
			Log.v(TAG, "Adding bitmap to cache");
			_bitmap_cache.put(info.toString(), bitmap);
		}
	}

	public static void loadBitmap(ImageView view, String filename, int width, int height) {
		ReducedBitmapInfo info = new ReducedBitmapInfo(filename, width, height);
		Log.v(TAG, "loading bitmap");

		Bitmap bitmap = _bitmap_cache.get(info.toString());
		if(bitmap == null) {
			Utilities.BitmapWorkerTask task = new Utilities.BitmapWorkerTask(view, width, height);
			task.execute(filename);
			view.setImageResource(R.drawable.loading_thumbnail);
		} else {
			Log.v(TAG, "bitmap already created");
			view.setImageBitmap(bitmap);
		}
	}

	public static class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private static final HashMap<ImageView, AsyncTask> _tasks = new HashMap<>();
		private int _width, _height;

		public BitmapWorkerTask(ImageView imageView, int width, int height) {
			// Use a WeakReference to ensure the ImageView can be garbage collected
			imageViewReference = new WeakReference<>(imageView);
			Log.v(TAG, "Creating BitmapWorkerTask");
			_tasks.put(imageView, this);
			_width = width;
			_height = height;
		}

		// Decode image in background.
		@Override
		protected Bitmap doInBackground(String... params) {
			try {
				String image_filename = params[0];

				Bitmap image = Utilities.decodeSampledBitmap(image_filename, _width, _height);
				addToCache(new ReducedBitmapInfo(image_filename, _width, _height), image);
				return image;
			} catch(Exception e) {
				Log.e(TAG, e.toString());
				return null;
			}
		}

		// Once complete, see if ImageView is still around and set bitmap.
		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (isCancelled()) {
				bitmap = null;
			}

			if (imageViewReference != null) {
				final ImageView imageView = imageViewReference.get();
				final AsyncTask bitmapWorkerTask = _tasks.get(imageView);
				if (this == bitmapWorkerTask && imageView != null) {
					if(bitmap == null)
						imageView.setImageResource(R.drawable.image_not_found);
					else
						imageView.setImageBitmap(bitmap);
				}
			}
		}
	}

	/**
	 * Converts a size in density-independent pixels to the actual pixel value on this device.
	 * @param dp size in density-independent pixels
	 * @return actual pixel value on this device
	 */
	public static int dp_to_px(float dp) {
		final float scale = Resources.getSystem().getDisplayMetrics().density;
		return (int)( dp * scale + 0.5f);
	}

}

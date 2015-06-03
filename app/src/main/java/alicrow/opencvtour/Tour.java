package alicrow.opencvtour;

import android.os.Environment;
import android.util.Log;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by daniel on 5/26/15.
 *
 * Class representing a virtual tour.
 * Also a bit of a hack so that different Activities can access the data they need.
 */
public class Tour
{
	static Tour _currentTour;
	static ArrayList<Tour> _tours;
	static String TAG = "Tour";
	static File _tour_directory;  /// directory to same our Tours in

	private ArrayList<TourItem> _tour_items;
	private boolean _gps_enabled;
	private String _name;



	public static Tour getCurrentTour() {
		if(_currentTour == null)
			_currentTour = new Tour();
		return _currentTour;
	}
	public static void setSelectedTour(Tour tour) {
		_currentTour = tour;
	}

	public static ArrayList<Tour> getTours() {
		if(_tours == null)
			loadTours();

		return _tours;
	}

	public static void loadTours() {
		_tours = new ArrayList<>();

		String state = Environment.getExternalStorageState();
		Log.i(TAG, state);

		_tour_directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);   /// FIXME: "Pictures" isn't a good place for this, but there's no better option here, except DIRECTORY_DOCUMENTS, which is only available starting in Android 4.4
		Log.i(TAG, "got public directory");

		for(File file : _tour_directory.listFiles()) {
			if(file.isFile() && file.getName().endsWith(".yaml")) {
				Log.i(TAG, "loading tour from file '" + file.toURI());
				_tours.add(new Tour(file));
			}
		}
	}

	public static void addNewTour() {
		_tours.add(new Tour());
	}





	public Tour() {
		/// Create default list of TourItems.
		_tour_items = new ArrayList<TourItem>();
		_name = "Unnamed tour";
	}
	public Tour(File file) {
		_tour_items = new ArrayList<TourItem>();
		loadFromFile(file);
	}

	/**
	 * Saves this Tour to a Map so we can export it.
	 * @return
	 */
	public Map<String,Object> saveToMap() {
		Map<String, Object> data = new HashMap<>();
		data.put("gps_enabled", _gps_enabled);
		data.put("name", _name);

		ArrayList<Map<String, Object>> item_maps = new ArrayList<>();
		for(TourItem item : _tour_items)
			item_maps.add(item.saveToMap());
		data.put("items", item_maps);

		return data;
	}
	public void loadFromMap(Map<String,Object> data) {

		setGpsEnabled((Boolean) data.get("gps_enabled"));
		setName((String) data.get("name"));

		_tour_items.clear();
		for(Map<String,Object> map : (ArrayList<Map<String,Object>>) data.get("items"))
			try {
				_tour_items.add(new TourItem(map));
			} catch(Exception e) {
				Log.e(TAG, e.toString());
			}
	}

	public void saveToFile() {
		Map<String, Object> data = saveToMap();
		Yaml yaml = new Yaml();
		try {
			String state = Environment.getExternalStorageState();
			Log.i(TAG, state);
			File file = new File(_tour_directory, getName() + ".yaml");
			Log.i(TAG, "initialized file");
			FileWriter writer = new FileWriter(file);
			Log.i(TAG, "created FileWriter");
			yaml.dump(data, writer);
			Log.i(TAG, "made YAML dump");
			writer.close();
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		}
	}
	public void loadFromFile(File file) {
		try {
			Yaml yaml = new Yaml();

			FileReader reader = new FileReader(file);

			Map<String, Object> data = (Map<String, Object>) yaml.load(reader);
			loadFromMap(data);
			Log.i(TAG, "loaded '" + file + "'");
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		}
	}

	public String toString() {
		return _name;
	}



	public ArrayList<TourItem> getTourItems() {
		return _tour_items;
	}

	public void setGpsEnabled(boolean enabled) {
		_gps_enabled = enabled;
	}
	public boolean getGpsEnabled() {
		return _gps_enabled;
	}

	public String getName() {
		return _name;
	}
	public void setName(String name) {
		_name = name;
	}

}

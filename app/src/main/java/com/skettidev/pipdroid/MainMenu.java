package com.skettidev.pipdroid;

import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;

import android.view.animation.*;
import androidx.activity.OnBackPressedCallback;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.skettidev.pipdroid.radio.RadioFragment;
import com.skettidev.pipdroid.radio.RadioStation;

import com.skettidev.pipdroid.radio.RadioStationAdapter;
import com.skettidev.pipdroid.radio.RadioStationList;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.hardware.*;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.os.Bundle;
import android.hardware.Sensor;
import android.util.Log;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.ViewGroup.LayoutParams;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.CameraUpdateFactory;
import org.json.JSONObject;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static androidx.media3.common.MediaItem.fromUri;
import static com.skettidev.pipdroid.VarVault.*;


public class MainMenu extends AppCompatActivity implements OnMapReadyCallback, SurfaceHolder.Callback, View.OnClickListener, View.OnLongClickListener, RadioFragment.RadioCallback {
	enum Screen {
		STATS,
		ITEMS,
		DATA
	}

	private enum MapViewType {
		WORLD,
		LOCAL
	}

	// constants
	private static final float WORLD_MAP_ZOOM = VarVault.WORLD_MAP_ZOOM; // city-wide
	private static final float LOCAL_MAP_ZOOM = VarVault.LOCAL_MAP_ZOOM; // nearby streets
	//private vars
	private View mapView;
	private View radioView;
	private ViewGroup midPanel;
	private ViewGroup leftPanel;

	private boolean isCompassModeEnabled = false;
	//sloppy fix. refactor.
	private boolean isMapRotationEnabled; // add this as a field
	private boolean isLocationEnabled = false;

	private MapViewType currentMapView;

	private ImageButton compassToggle;
	private float lastCompassRotation = 0f;
	private SupportMapFragment mapFragment;

	private SensorManager sensorManager;
	private Sensor accelerometer;
	private Sensor magnetometer;

	private float zoomLevel = VarVault.WORLD_MAP_ZOOM;
	private boolean openWorldMapByDefault = true;
	// default to WORLD MAP

	private RadioStation currentStation;
	private boolean radioOn;
	private Handler nowPlayingHandler = new Handler(Looper.getMainLooper());
	private Runnable nowPlayingRunnable;
	private ImageButton radioPowerButton;
	private TextView nowPlayingText;
	private ExoPlayer exoPlayer;
	private RadioStationAdapter radioAdapter;
	private RecyclerView recyclerView;
	private SupportMapFragment cachedMapFragment = null;
//	public ImageView crtImage = findViewById(R.id.crt_image);
//	public TextView tvTitle = findViewById(R.id.crt_title);
//	public TextView tvAlternateText = findViewById(R.id.crt_alternate_text);

	//???
	private float[] rotationMatrix = new float[9];
	private float[] orientation = new float[3];
	private float azimuthDeg;
	public static GoogleMap mMap;
	private View cachedMapView = null;
	private View cachedRadioView = null;
	private final Map<View, Runnable> buttonActions = new HashMap<>();
	private final ActivityResultLauncher<String> requestPermissionLauncher =
			registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
				if (isGranted) {
					enableLocation();  // Enable location if permission is granted
				} else {
					Toast.makeText(MainMenu.this, "Permission denied", Toast.LENGTH_SHORT).show();
				}
			});


	// ########################
	// ## On app start ########
	// ########################
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Allow content behind system bars
//		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

		// Hide system bars
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

		// Hide system bars
		WindowInsetsControllerCompat controller =
				new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

		controller.hide(
				WindowInsetsCompat.Type.statusBars()
//						| WindowInsetsCompat.Type.navigationBars()
		);

		// Allow swipe to show bars temporarily
		controller.setSystemBarsBehavior(
				WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		);
		super.onCreate(savedInstanceState);

		initSensors();
		registerReceiver(mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		// ===== Load layout =====
		setContentView(R.layout.main);
		// init onbackpressed dispatcher

		getOnBackPressedDispatcher().addCallback(this,
			new OnBackPressedCallback(true) {
				@Override
				public void handleOnBackPressed() {
					Intent intent = new Intent(Intent.ACTION_MAIN);
					intent.addCategory(Intent.CATEGORY_HOME);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(intent);
				}
			}
		);

		// Init sound
		HandleSound.initSound(this.getApplicationContext());

		//crt wrapper
//		FrameLayout tvWrapper = findViewById(R.id.crt_screen_wrapper)
//		ImageView crtImage = findViewById(R.id.crt_image);
//		TextView tvTitle = findViewById(R.id.crt_title);
//		TextView tvAlternateText = findViewById(R.id.crt_alternate_text);
//		initAnimations();
//

		VarVault.font = Typeface.createFromAsset(getAssets(), "Monofonto.ttf");
		VarVault.curWG = new Stat();
		VarVault.maxWG = new Stat();
		VarVault.curCaps = new Stat();

		// Set flags and volume buttons
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


		// Initialize the three buttons
		initMainMenu();
//		VarVault.stats = (ImageView) findViewById(R.id.left_stats);
//		TextView x = (TextView) findViewById(R.id.left_button_stats);
//		if (x != null) {
//			x.setTypeface(VarVault.font);
//			x.setTextColor(Color.argb(100, 255, 225, 0));
//		}
//		VarVault.stats.setOnClickListener(this);
//
//		VarVault.items = (ImageView) findViewById(R.id.left_items);
//		TextView y = (TextView) findViewById(R.id.left_button_items);
//		if (y != null) {
//			y.setTypeface(VarVault.font);
//			y.setTextColor(Color.argb(100, 255, 225, 0));
//		}
//		VarVault.items.setOnClickListener(this);
//
//		VarVault.data = (ImageView) findViewById(R.id.left_data);
//		TextView z = (TextView) findViewById(R.id.left_button_data);
//		if (z != null) {
//			z.setTypeface(VarVault.font);
//			z.setTextColor(Color.argb(100, 255, 225, 0));
//		}

		VarVault.data.setOnClickListener(this);
		// trigger turn on animation
//		if (turnOnAnimation != null) {
//			turnOnAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
//		}
		// Get stats and fill arrays

		initSkills();
		initSpecial();
		InitializeArrays.all();

		onClick(VarVault.stats);
		initCaps();

		// TO-DO
		// refactor
//		// ===== Window setup =====
//		setupWindow();
//
//		fetchViews(findViewById(android.R.id.content));
//		// ===== Initialize views =====
//		initViews();
//
//		// ===== Initialize fonts, colors, and button click listeners =====
//		initButtonsAndFonts();  // leftPanel is your main button container
//
//		// ===== Initialize audio / media =====
//		initAudio();
//		initMediaPlayer();
//
//		// ===== Initialize stats / game data =====
//		initStats();
//		initGameData();
//
//		// ===== Show stats at startup (optional) =====

	}

	private void radioClicked(RadioStation station) {
		if (station == null) {
			// Just show the radio UI, no stream yet
			cachedMapView.setVisibility(View.GONE);
			cachedRadioView.setVisibility(View.VISIBLE);
			setupRadioView();   // only builds the list

			return;             // stop – no station to play
		}
		//		setupRadioView();
		//		// grab panel that will host radio screen
//		LinearLayout midPanel = findViewById(R.id.mid_panel);
//		midPanel.removeAllViews();
		ViewGroup midPanel = findViewById(R.id.mid_panel);
		if (midPanel == null) {
			Log.d("radioClicked", "No mid panel found");
			return;
		}

		View map = midPanel.findViewWithTag("MAP");
		if (map != null) {
			map.setVisibility(View.GONE);
		}

		View radio = midPanel.findViewWithTag("RADIO");
		if (radio == null) {
			Log.d("radioClicked", "No radio view found");
			return;
		}


		// inflate the radio layout into the panel
		View radioScreenView = LayoutInflater.from(this).inflate(R.layout.radio, midPanel, false);
		// optional, debug color is red
//		radioScreenView.setBackgroundColor(Color.RED); // debug color

		//make view part of the hierarchy
		midPanel.addView(radioScreenView); // <<< MUST do this
		// add tags to the views
		radioScreenView.setTag("RADIO");
		cachedMapView.setTag("MAP");


		recyclerView = radioScreenView.findViewById(R.id.radio_station_list); // initialize the field before setting its adapter
		nowPlayingText = radioScreenView.findViewById(R.id.now_Playing_Text);
		radioPowerButton = radioScreenView.findViewById(R.id.radio_power_button);
		if (station != null) {
			radioAdapter.setSelectedStation(station);
		}
// null check
		if (radioPowerButton != null) {
			radioPowerButton.setOnClickListener(v -> radioPowerToggle());
			radioPowerButton.setAlpha(radioOn ? 1f : 0.4f);
		}

		TextView hostLink = radioScreenView.findViewById(R.id.radio_host_link);
		// Setup host link
		hostLink.setText("streams by https://fallout.radio, support them here! https://buymeacoffee.com/beenreported");
		hostLink.setOnClickListener(v -> {
			Intent browserIntent = new Intent(Intent.ACTION_VIEW,
					Uri.parse("https://buymeacoffee.com/beenreported"));
			startActivity(browserIntent);
		});

		// Setup RecyclerView
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		radioAdapter = new RadioStationAdapter(RadioStationList.getStations(), this::radioClicked);
		recyclerView.setAdapter(radioAdapter);

		updateRadioPowerUi(radioOn);

		// Update selection safely — adapter is already initialized
		if (radioAdapter != null) {
			radioAdapter.setSelectedStation(station);
		}

		// Update UI
		if (nowPlayingText != null) {
			nowPlayingText.setText("Now Playing: " + station.getTitle());
		}
		if (station != null) {
			// set currentstation?

			currentStation = station;
			Log.d("radioClicked", "station =" + station);
			playStream(currentStation);
			Log.d("radioClicked", "Playing stream for " + currentStation);
			startNowPlayingPolling(station.getNowPlayingUrl());
			Log.d("radioClicked", "Starting now playing polling for current station.");
		} else {
			Log.d("radioClicked", "No radio station selected");

			return;
		}
		Log.d("radioClicked", "Radio clicked: Station = " + station);
		Log.d("radioClicked", "Showing radio screen.");

	}


	@Override
	public void onClick(View source) {
		Log.e("onClick()", "start");
		VarVault.curCaps.setValue(VarVault.curCaps.getValue() + 5);

		playClickSound(source);

		// Play the appropriate sound for the clicked view
//		Runnable action = buttonActions.get(source);
//		if (action != null) {
//			action.run();
//		} else {
//			Log.w("MainMenu", "No action bound for view: " + source);
//		}

		// Play a tune, dependent on source.
		if (VarVault.MAIN_BUTTONS.contains(source))
		HandleSound.playSound(HandleSound.aud_newTab);
		else if (source == VarVault.stimpak)
			HandleSound.playSound(HandleSound.aud_stimpak);
		else
			HandleSound.playSound(HandleSound.aud_selection);


		// Set the panels for future usage.
		ViewGroup midPanel = findViewById(R.id.mid_panel);

		ViewGroup bottomBar = findViewById(R.id.bottom_bar);

		ViewGroup topBar = findViewById(R.id.top_bar);  // Or whichever view you're targeting

		// Sort the source
		Log.e("onClick()", "sort source");

		if (source == VarVault.stats) {
			statsClicked();
			Log.e("onClick()", "statsClicked()");
		}
		else if (source == VarVault.statusLL) {
			statusClicked();
			Log.e("onClick()", "statusClicked()");
		}
		else if (source == VarVault.specialLL || source == VarVault.special) {
			specialClicked();
			Log.e("onClick()", "specialClicked()");
		}
		else if (source == VarVault.skillsLL || source == VarVault.skills) {
			skillsClicked();
			Log.e("onClick()", "skillsClicked()");
		}
		else if (VarVault.SUBMENU_SPECIAL.contains(source)) {
			specialStatClicked(source);
			Log.e("onClick()", "specialStatClicked()");
		}
		else if (VarVault.SUBMENU_SKILLS.contains(source)) {
			skillStatClicked(source);
			Log.e("onClick()", "skillStatClicked()");
		}
		else if (source == VarVault.flashlight) {
			flashlightClicked();
			Log.e("onClick()", "flashlightClicked()");
		}
		else if (source == VarVault.items) {
			itemsClicked();
			Log.e("onClick()", "itemsClicked()");
		}
		else if (source == VarVault.weaponsLL) {
			weaponsClicked();
			Log.e("onClick()", "weaponsClicked()");
		}
		else if (source == VarVault.apparelLL) {
			apparelClicked();
			Log.e("onClick()", "apparelClicked()");
		}
		else if (
				source == VarVault.aidLL ||
						source == VarVault.miscLL ||
						source == VarVault.ammoLL
		) {
			updateCAPS();
			Log.e("onClick()", "updateCAPS()");
		}
		else if (source == VarVault.data) {
			dataClicked();
			Log.e("onClick()", "dataClicked(source)");
		}

		Log.e("onClick()", "sort source DONE!");


//		// Execute action (action decides what state changes happen)
//		action.run();
	}



	private void flashlightClicked() {
		if (VarVault.mCamera == null) {
			VarVault.preview = (SurfaceView) findViewById(R.id.PREVIEW);
			VarVault.mHolder = VarVault.preview.getHolder();
			VarVault.mCamera = Camera.open();
			try {
				VarVault.mCamera.setPreviewDisplay(VarVault.mHolder);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
		}

		// If it's off, turn it on
		if (VarVault.isCamOn == false) {
			Parameters params = VarVault.mCamera.getParameters();
			params.setFlashMode(Parameters.FLASH_MODE_TORCH);
			VarVault.mCamera.setParameters(params);
			VarVault.mCamera.startPreview();
			VarVault.isCamOn = true;
		}

		// If it's on, turn it off
		else {
			Parameters params = VarVault.mCamera.getParameters();
			params.setFlashMode(Parameters.FLASH_MODE_OFF);
			VarVault.mCamera.setParameters(params);
			VarVault.mCamera.stopPreview();
			VarVault.mCamera.release();
			VarVault.mCamera = null;
			VarVault.isCamOn = false;
		}
	}
	private void itemsClicked() {
		initItemsView();

		if (cachedMapView != null) {
			midPanel.addView(cachedMapView);
			cachedMapView.setVisibility(View.GONE);
			Log.d("statsClicked", "Set cachedMapView GONE");
		} else {
			Log.d("statsClicked", "cachedMapView null, return");
			return;
		}

		//initialize textviews
		VarVault.title = (TextView) findViewById(R.id.title_items);
		if (VarVault.title != null) {
			VarVault.title.setTypeface(VarVault.font);
		}
		Log.d("itemsClicked", "Set typeface for title TextView.");
		VarVault.wg = (TextView) findViewById(R.id.wg_items);
		if (VarVault.wg != null) {
			VarVault.maxWG.setValue(150 + (10 * VarVault.strength.getValue()));
			updateWG();
		}
		Log.d("itemsClicked", "Set value for WG TextView and updated.");
		if (VarVault.wg != null) {
			VarVault.wg.setTypeface(VarVault.font);
		}
		Log.d("itemsClicked", "Set typeface for WG TextView.");
		VarVault.caps = (TextView) findViewById(R.id.caps_items);
		if (VarVault.caps != null) {
			updateCAPS();
		}
		Log.d("itemsClicked", "Updated CAPS value and set typeface.");
// Button-ize the buttons
		VarVault.weapons = (TextView) findViewById(R.id.btn_weapons);
		if (VarVault.weapons != null) {
			VarVault.weaponsLL = (LinearLayout) findViewById(R.id.btn_weapons_box);
			if (VarVault.weaponsLL != null) {
				VarVault.weapons.setTypeface(VarVault.font);
				VarVault.weaponsLL.setOnClickListener(this);
			}
		}
		Log.d("itemsClicked", "Bound weapon box and set typeface.");
		VarVault.apparel = (TextView) findViewById(R.id.btn_apparel);
		if (VarVault.apparel != null) {
			VarVault.apparelLL = (LinearLayout) findViewById(R.id.btn_apparel_box);
			if (VarVault.apparelLL != null) {
				VarVault.apparel.setTypeface(VarVault.font);
				VarVault.apparelLL.setOnClickListener(this);
			}
		}
		Log.d("itemsClicked", "Bound apparel box and set typeface.");
		VarVault.aid = (TextView) findViewById(R.id.btn_aid);
		if (VarVault.aid != null) {
			VarVault.aidLL = (LinearLayout) findViewById(R.id.btn_aid_box);
			if (VarVault.aidLL != null) {
				VarVault.aid.setTypeface(VarVault.font);
				VarVault.aidLL.setOnClickListener(this);
			}
		}
		Log.d("itemsClicked", "Bound aid box and set typeface.");
		VarVault.misc = (TextView) findViewById(R.id.btn_misc);
		if (VarVault.misc != null) {
			VarVault.miscLL = (LinearLayout) findViewById(R.id.btn_misc_box);
			if (VarVault.miscLL != null) {
				VarVault.misc.setTypeface(VarVault.font);
				VarVault.miscLL.setOnClickListener(this);
			}
		}
		Log.d("itemsClicked", "Bound misc box and set typeface.");
		VarVault.ammo = (TextView) findViewById(R.id.btn_ammo);
		if (VarVault.ammo != null) {
			VarVault.ammoLL = (LinearLayout) findViewById(R.id.btn_ammo_box);
			if (VarVault.ammoLL != null) {
				VarVault.ammo.setTypeface(VarVault.font);
				VarVault.ammoLL.setOnClickListener(this);
			}
		}
		Log.d("itemsClicked", "Bound ammo box and set typeface.");
// --- Bind dynamic weapons list container ---
		LinearLayout weaponsList = midPanel.findViewById(R.id.weapons_list);
		if (weaponsList != null) {
			populateOwnedWeapons(weaponsList); // add TextViews dynamically from DB
			Log.d("itemsClicked", "Bound weapons list and populated with owned weapons.");
		}
	}
private void initItemsView() {
	Log.d("itemsClicked", "Entering items clicked.");

	// Clear crap
	ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
	ViewGroup topBar = (ViewGroup) findViewById(R.id.top_bar);
	ViewGroup bottomBar = (ViewGroup) findViewById(R.id.bottom_bar);
	LayoutInflater inf = this.getLayoutInflater();
	Log.d("dataClicked", "Set cachedMapView visible, cachedRadioView gone");
	midPanel.removeAllViews();
	topBar.removeAllViews();
	bottomBar.removeAllViews();
	Log.d("itemsClicked", "Removed all views from main panel.");

	// Main screen turn on
	inf.inflate(R.layout.weapons_screen, midPanel);
	inf.inflate(R.layout.items_bar_top, topBar);
	inf.inflate(R.layout.items_bar_bottom, bottomBar);
	Log.d("itemsClicked", "Inflated weapon screen and bar views.");
}
//	private void bindTopBarButtons(ViewGroup topBar) {
//		VarVault.weaponsLL = topBar.findViewById(R.id.btn_weapons_box);
//		VarVault.apparelLL = topBar.findViewById(R.id.btn_apparel_box);
//		VarVault.aidLL     = topBar.findViewById(R.id.btn_aid_box);
//		VarVault.miscLL    = topBar.findViewById(R.id.btn_misc_box);
//		VarVault.ammoLL    = topBar.findViewById(R.id.btn_ammo_box);
//
//		if (VarVault.weaponsLL != null) VarVault.weaponsLL.setOnClickListener(this);
//		if (VarVault.apparelLL != null) VarVault.apparelLL.setOnClickListener(this);
//		if (VarVault.aidLL != null) VarVault.aidLL.setOnClickListener(this);
//		if (VarVault.miscLL != null) VarVault.miscLL.setOnClickListener(this);
//		if (VarVault.ammoLL != null) VarVault.ammoLL.setOnClickListener(this);
//	}
//
//	private void bindItemsViews() {
//		// Button-ize the buttons
//		VarVault.weapons = (TextView) findViewById(R.id.btn_weapons);
//		VarVault.weaponsLL = (LinearLayout) findViewById(R.id.btn_weapons_box);
//		VarVault.weapons.setTypeface(VarVault.font);
//		VarVault.weaponsLL.setOnClickListener(this);
//
//		VarVault.apparel = (TextView) findViewById(R.id.btn_apparel);
//		VarVault.apparelLL = (LinearLayout) findViewById(R.id.btn_apparel_box);
//		VarVault.apparel.setTypeface(VarVault.font);
//		VarVault.apparelLL.setOnClickListener(this);
//
//		VarVault.aid = (TextView) findViewById(R.id.btn_aid);
//		VarVault.aidLL = (LinearLayout) findViewById(R.id.btn_aid_box);
//		VarVault.aid.setTypeface(VarVault.font);
//		VarVault.aidLL.setOnClickListener(this);
//
//		VarVault.misc = (TextView) findViewById(R.id.btn_misc);
//		VarVault.miscLL = (LinearLayout) findViewById(R.id.btn_misc_box);
//		VarVault.misc.setTypeface(VarVault.font);
//		VarVault.miscLL.setOnClickListener(this);
//
//		VarVault.ammo = (TextView) findViewById(R.id.btn_ammo);
//		VarVault.ammoLL = (LinearLayout) findViewById(R.id.btn_ammo_box);
//		VarVault.ammo.setTypeface(VarVault.font);
//		VarVault.ammoLL.setOnClickListener(this);
//	}
private void populateOwnedWeapons(LinearLayout weaponsList) {
	if (weaponsList == null) {
		Log.e("ITEMS", "weaponsList is null – ITEMS layout not inflated correctly");
		return;
	}
	// Open Database
	dbHelper database = new dbHelper(MainMenu.this);
	Log.d("DB", "Getting a writable database...");
	SQLiteDatabase db = database.getWritableDatabase();
	Log.d("DB", "...writable database gotten!");
	// Get EVERYTHING from OwnedWeapons
	Cursor allWeapons = db.query("OwnedWeapons", new String[]{dbHelper.colName, dbHelper.colIsWearing}, null, null, null, null, "_id");
	allWeapons.moveToFirst();
	// Dynamically create TextViews and add them to the list
	for (int i = 0; i < 20 && !allWeapons.isAfterLast(); i++) {
		String tempName = allWeapons.getString(0); // Get weapon name
		int isWearing = allWeapons.getInt(1);      // Get "is wearing" flag
		// Define layout parameters for the new TextViews
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.setMargins(15, 0, 0, 15);
		// Create new TextView for each weapon
		TextView weaponTextView = new TextView(this);
		weaponTextView.setLayoutParams(lp);
		weaponTextView.setTypeface(VarVault.font);
		weaponTextView.setTextSize(22f);
		weaponTextView.setTextColor(Color.parseColor("#AAFFAA"));
		// Set the weapon's name with a bullet if wearing
		if (isWearing == 0) {
			Log.d("populateOwnedWeapons", "Creating new TextView for: " + tempName);
			weaponTextView.setText("  " + tempName);
		} else {
			Log.d("populateOwnedWeapons", "Creating new TextView for: " + tempName + " (wearing)");
			weaponTextView.setText("\u25a0 " + tempName); // Unicode bullet symbol
		}
		// Add long click listener if needed
		weaponTextView.setOnLongClickListener(this);
		// Add the new TextView to the weapons list
		Log.d("populateOwnedWeapons", "Adding new TextView to weaponsList");
		weaponsList.addView(weaponTextView);
		// Move to next weapon in the cursor
		Log.d("populateOwnedWeapons", "Moving to next weapon");
		allWeapons.moveToNext();
	}
	// Close the cursor after use
	allWeapons.close();
}


	private void populateOwnedApparel() {

		// Open Database
		dbHelper database = new dbHelper(MainMenu.this);

		Log.d("DB", "Getting a writable database...");
		SQLiteDatabase db = database.getWritableDatabase();
		Log.d("DB", "...writable database gotten!");

		// Get EVERYTHING from OwnedWeapons
		Cursor allApparel = db.query("OwnedApparel", new String[]{dbHelper.colName, dbHelper.colIsWearing}, null, null, null, null, "_id");
		allApparel.moveToFirst();
		LinearLayout apparelList = (LinearLayout) findViewById(R.id.apparelList);

		for (int i = 0; i < 20 && !allApparel.isAfterLast(); i++) {

			String tempName = "";
			int isWearing = 0;

			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
			lp.setMargins(15, 0, 0, 15);
			tempName = allApparel.getString(0);
			isWearing = allApparel.getInt(1);

			VarVault.Apparel.add(i, new TextView(this));

			VarVault.Apparel.get(i).setLayoutParams(lp);
			VarVault.Apparel.get(i).setTypeface(VarVault.font);
			VarVault.Apparel.get(i).setTextSize((float) 22.0);
			VarVault.Apparel.get(i).setTextColor(Color.parseColor("#AAFFAA"));

			// Are they wearing it?
			if (isWearing == 0)
				VarVault.Apparel.get(i).setText("  " + tempName);
			else
				VarVault.Apparel.get(i).setText("\u25a0 " + tempName);

			VarVault.Apparel.get(i).setOnLongClickListener(this);
			apparelList.addView(VarVault.Apparel.get(i));

			allApparel.moveToNext();
		}
	}
	private void weaponsClicked() {
		Log.d("weaponsClicked", "Entering weaponsClicked method");
		// Clear crap
		//		resetScreen();
		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		ViewGroup topBar = (ViewGroup) findViewById(R.id.top_bar);
		ViewGroup bottomBar = (ViewGroup) findViewById(R.id.bottom_bar);


		midPanel.removeAllViews();
		topBar.removeAllViews();
		bottomBar.removeAllViews();
		Log.d("weaponsClicked", "Removed all views from main panel.");

		LayoutInflater inf = this.getLayoutInflater();
		Log.d("weaponsClicked", "inflater found: " + inf);
		// Main screen turn on
		inf.inflate(R.layout.weapons_screen, midPanel, true);
		inf.inflate(R.layout.items_bar_top, topBar);
		inf.inflate(R.layout.items_bar_bottom, bottomBar);
		Log.d("weaponsClicked", "Inflated weapon screen and bar views.");

		// Initialize textviews
		VarVault.title = (TextView) findViewById(R.id.title_items);
		if (VarVault.title != null) {
			VarVault.title.setTypeface(VarVault.font);
		}
		Log.d("weaponsClicked", "Set typeface for title TextView.");
		VarVault.wg = (TextView) findViewById(R.id.wg_items);
		if (VarVault.wg != null) {
			VarVault.maxWG.setValue(150 + (10 * VarVault.strength.getValue()));
			updateWG();
		}
		Log.d("weaponsClicked", "Set value for WG TextView and updated.");
		if (VarVault.wg != null) {
			VarVault.wg.setTypeface(VarVault.font);
		}
		Log.d("weaponsClicked", "Set typeface for WG TextView.");
		VarVault.caps = (TextView) findViewById(R.id.caps_items);
		if (VarVault.caps != null) {
			updateCAPS();
		}
		Log.d("weaponsClicked", "Updated CAPS value and set typeface.");
// Button-ize the buttons
		VarVault.weapons = (TextView) findViewById(R.id.btn_weapons);
		if (VarVault.weapons != null) {
			VarVault.weaponsLL = (LinearLayout) findViewById(R.id.btn_weapons_box);
			if (VarVault.weaponsLL != null) {
				VarVault.weapons.setTypeface(VarVault.font);
				VarVault.weaponsLL.setOnClickListener(this);
			}
		}
		Log.d("weaponsClicked", "Bound weapon box and set typeface.");
		VarVault.apparel = (TextView) findViewById(R.id.btn_apparel);
		if (VarVault.apparel != null) {
			VarVault.apparelLL = (LinearLayout) findViewById(R.id.btn_apparel_box);
			if (VarVault.apparelLL != null) {
				VarVault.apparel.setTypeface(VarVault.font);
				VarVault.apparelLL.setOnClickListener(this);
			}
		}
		Log.d("weaponsClicked", "Bound apparel box and set typeface.");
		VarVault.aid = (TextView) findViewById(R.id.btn_aid);
		if (VarVault.aid != null) {
			VarVault.aidLL = (LinearLayout) findViewById(R.id.btn_aid_box);
			if (VarVault.aidLL != null) {
				VarVault.aid.setTypeface(VarVault.font);
				VarVault.aidLL.setOnClickListener(this);
			}
		}
		Log.d("weaponsClicked", "Bound aid box and set typeface.");
		VarVault.misc = (TextView) findViewById(R.id.btn_misc);
		if (VarVault.misc != null) {
			VarVault.miscLL = (LinearLayout) findViewById(R.id.btn_misc_box);
			if (VarVault.miscLL != null) {
				VarVault.misc.setTypeface(VarVault.font);
				VarVault.miscLL.setOnClickListener(this);
			}
		}
		Log.d("weaponsClicked", "Bound misc box and set typeface.");
		VarVault.ammo = (TextView) findViewById(R.id.btn_ammo);
		if (VarVault.ammo != null) {
			VarVault.ammoLL = (LinearLayout) findViewById(R.id.btn_ammo_box);
			if (VarVault.ammoLL != null) {
				VarVault.ammo.setTypeface(VarVault.font);
				VarVault.ammoLL.setOnClickListener(this);
			}
		}
		Log.d("weaponsClicked", "Bound ammo box and set typeface.");
// --- Bind dynamic weapons list container ---
		LinearLayout weaponsList = (LinearLayout) findViewById(R.id.weapons_list);
		if (weaponsList != null) {
			Log.d("weaponsClicked", "Bound weapons list and populated with owned weapons.");
		}
		VarVault.title = (TextView) findViewById(R.id.title_items);
		if (VarVault.title != null) {
			VarVault.title.setTypeface(VarVault.font);
		}
		Log.d("weaponsClicked", "Set typeface for title TextView.");

//  VarVault.weapons = findViewById(R.id.weapons_list);  // LinearLayout container
		populateOwnedWeapons(weaponsList);
		updateCAPS();
	}


	private void apparelClicked() {
		Log.d("apparelClicked", "Starting apparel clicked function.");

		// Get reference to mid_panel ViewGroup
		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		if (midPanel != null) {
			Log.d("apparelClicked", "Found mid_panel ViewGroup.");

			// Remove all views from mid_panel
			midPanel.removeAllViews();
			Log.d("apparelClicked", "Removed all views from mid_panel.");
		} else {
			Log.d("apparelClicked", "mid_panel ViewGroup not found, cannot remove views.");
		}

		// Inflate apparel screen layout into mid_panel
		if (midPanel != null) {
			LayoutInflater inf = this.getLayoutInflater();
			inf.inflate(R.layout.apparel_screen, midPanel);
			Log.d("apparelClicked", "Inflated apparel screen layout into mid_panel.");

			// Populate owned apparel list
			if (midPanel != null) {
				populateOwnedApparel();
				Log.d("apparelClicked", "Populated owned apparel list.");
			} else {
				Log.d("apparelClicked", "Cannot populate owned apparel list, mid_panel ViewGroup not found.");
			}
		} else {
			Log.d("apparelClicked", "Cannot inflate apparel screen layout into mid_panel, mid_panel ViewGroup not found.");
		}

		// Update CAPS
		updateCAPS();
		Log.d("apparelClicked", "Updated CAPS.");
	}

//	private void dataClicked() {
//		Log.d("dataClicked", "dataClicked() called");
//
//		// Reset dynamic screen content
////		resetScreen();
//
//		//add CRT to layout file
//		// Set the text and image for the TextViews and ImageView
////		crtImage.setImageResource(R.drawable.crt_screen);
////		tvTitle.setText("Welcome to the CRT");
////		tvAlternateText.setText("Turn on the CRT!");
//
//		// Add the views to the layout file
//		setContentView(R.layout.main);
//
//		// Start the turn off animation
////		if (turnOffAnimation != null) {
////			turnOffAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
////		}
//		// insert crt code here?
//		//or
//		// Start the turn off animation after 10 seconds
////		new Handler().postDelayed(() -> crtImage.startAnimation(turnOffAnimation), 10 * 1000);
//
//		Log.d("dataClicked", "resetScreen() completed");
//
//		LayoutInflater inf = LayoutInflater.from(this);
//		ViewGroup midPanel = findViewById(R.id.mid_panel);
//		LinearLayout topBar = findViewById(R.id.top_bar);
//		LinearLayout bottomBar = findViewById(R.id.bottom_bar);
//		// Clear dynamic panels
//		midPanel.removeAllViews();
//		topBar.removeAllViews();
//		bottomBar.removeAllViews();
//
////		initMainMenu();
//
//		Log.d("dataClicked", "Cleared midPanel, topBar, bottomBar");
//		if (midPanel == null || topBar == null || bottomBar == null) {
//			Log.w("dataClicked", "One or more panel views are null!");
//			return;
//		}
//		Log.d("dataClicked", "Panels found successfully");
//
//		// Inflate bottom bar
//		View bottomContent = inf.inflate(R.layout.data_bar_bottom, bottomBar, false);
//		bottomBar.addView(bottomContent);
//		Log.d("dataClicked", "data_bar_bottom inflated into bottomBar");
//
//
//		// Map initialization with caching
//		if (cachedMapView != null) {
//			Log.d("dataClicked", "cachedMapView not null");
//			FrameLayout mapContainer = cachedMapView.findViewById(R.id.map_container);
//
//			if (mapContainer!= null) {
//
//				Log.d("dataClicked", " mapContainer is not null");
//				midPanel.addView(mapContainer);
//				Log.d("dataClicked", "added FrameLayout mapContainer to midPanel");
//				midPanel.addView(cachedMapView);
//				Log.d("dataClicked", "added cachedMapView View to midPanel");
//				cachedMapFragment.getMapAsync(this);
//				Log.d("dataClicked", "async mapfragment update");
//				cachedMapView = inf.inflate(R.layout.map_screen, midPanel, false);
//				Log.d("dataClicked", "inflated layout map_screen into midPanel");
//				cachedMapView.setTag("MAP");
//				Log.d("dataClicked", "set view tag MAP");
//
//				if (openWorldMapByDefault) {
//
//					worldMapClicked();
//					openWorldMapByDefault = false;
//
//				} else {
//
//					mMap.moveCamera(
//							CameraUpdateFactory.newLatLngZoom(
//									VarVault.playerLocation, zoomLevel));
//				}
//
//			} else {
//
//				Log.w("dataClicked", "mapContainer not found in cachedMapView!");
//				cachedMapFragment = SupportMapFragment.newInstance();
//				getSupportFragmentManager()
//						.beginTransaction()
//						.add(mapContainer.getId(), cachedMapFragment, "MAP_FRAGMENT")
//						.commitNow();
//				Log.d("dataClicked", "cachedMapFragment created and added");
//			}
//
//			Log.d("dataClicked", "getMapAsync() called on cachedMapFragment");
//			return;
//		}
//
//		// radio view stuff
//		if (cachedRadioView != null) {
//			midPanel.addView(cachedRadioView);
//			cachedRadioView = inf.inflate(R.layout.radio, midPanel, false);
//			cachedRadioView.setTag("RADIO");
//			Log.d("dataClicked", "set view tag RADIO");
//		} else {
//			Log.d("dataClicked", "cachedRadioView is null!, return");
//			return;
//		}
//
//// check and set visibility
//
//		if (cachedMapView != null && cachedRadioView != null) {
//
//			cachedMapView.setVisibility(View.VISIBLE);
//			cachedRadioView.setVisibility(View.GONE);
//		}
//		Log.d("dataClicked", "Set cachedMapView visible, cachedRadioView gone");
//
//
//		VarVault.localMap = bottomContent.findViewById(R.id.btn_localmap);
//		VarVault.localMapLL = bottomContent.findViewById(R.id.btn_localmap_box);
//		if (VarVault.localMap != null) VarVault.localMap.setTypeface(VarVault.font);
//		if (VarVault.localMapLL != null) VarVault.localMapLL.setOnClickListener(v -> showLocalMap());
//
//		VarVault.worldMap = bottomContent.findViewById(R.id.btn_worldmap);
//		VarVault.worldMapLL = bottomContent.findViewById(R.id.btn_worldmap_box);
//		if (VarVault.worldMap != null) VarVault.worldMap.setTypeface(VarVault.font);
//		if (VarVault.worldMapLL != null) VarVault.worldMapLL.setOnClickListener(v -> worldMapClicked());
//
//		VarVault.quests = bottomContent.findViewById(R.id.btn_quests);
//		VarVault.questsLL = bottomContent.findViewById(R.id.btn_quests_box);
//		if (VarVault.quests != null) VarVault.quests.setTypeface(VarVault.font);
//		if (VarVault.questsLL != null) VarVault.questsLL.setOnClickListener(this);
//
//		VarVault.notes = bottomContent.findViewById(R.id.btn_notes);
//		VarVault.notesLL = bottomContent.findViewById(R.id.btn_notes_box);
//		if (VarVault.notes != null) VarVault.notes.setTypeface(VarVault.font);
//		if (VarVault.notesLL != null) VarVault.notesLL.setOnClickListener(this);
//
//		VarVault.radio = bottomContent.findViewById(R.id.btn_radio);
//		VarVault.radioLL = bottomContent.findViewById(R.id.btn_radio_box);
//		if (VarVault.radio != null) {
//			VarVault.radio.setTypeface(VarVault.font);
//		}
//		if (VarVault.radioLL != null) {
//			VarVault.radioLL.setOnClickListener(v -> radioClicked(currentStation));
//		}
//		//TO-DO refactor
//		// Bind bottom bar buttons
////		bindDataViews(bottomContent);
//		Log.d("dataClicked", "bindDataViews() called on bottomContent");
//	}
private void dataClicked() {
	Log.d("dataClicked", "dataClicked() called");

	// --- 1️⃣  Reset the layout  ------------------------------------
	setContentView(R.layout.main);
	LayoutInflater inflater = LayoutInflater.from(this);

	// --- 2️⃣  Grab the container panels  ---------------------------
	ViewGroup midPanel = findViewById(R.id.mid_panel);
	LinearLayout topBar = findViewById(R.id.top_bar);
	LinearLayout bottomBar = findViewById(R.id.bottom_bar);

	// Safety first – if any panel is missing just abort.
	if (midPanel == null || topBar == null || bottomBar == null) {
		Log.w("dataClicked", "One or more panel views are null – aborting");
		return;
	}

	// Clear previous dynamic content
	midPanel.removeAllViews();
	topBar.removeAllViews();
	bottomBar.removeAllViews();
	Log.d("dataClicked", "Cleared midPanel, topBar, bottomBar");

	// --- 3️⃣  Inflate the bottom bar (static part) ------------------
	View bottomContent = inflater.inflate(R.layout.data_bar_bottom, bottomBar, false);
	bottomBar.addView(bottomContent);
	Log.d("dataClicked", "data_bar_bottom inflated into bottomBar");

	// --- 4️⃣  Map view ------------------------------------------------
	if (cachedMapView == null) {                           // first‑time: create it
		cachedMapView = inflater.inflate(R.layout.map_screen, midPanel, false);
		cachedMapView.setTag("MAP");
	}
	// Re‑attach the map view to the current hierarchy
	midPanel.addView(cachedMapView);

	// Initialise/attach the map fragment only once
	if (cachedMapFragment == null) {
		cachedMapFragment = SupportMapFragment.newInstance();
		FrameLayout mapContainer = cachedMapView.findViewById(R.id.map_container);
		if (mapContainer == null) {
			Log.e("dataClicked", "map_container not found inside cachedMapView");
			return;   // cannot proceed without the container
		}
		getSupportFragmentManager()
				.beginTransaction()
				.add(mapContainer.getId(), cachedMapFragment, "MAP_FRAGMENT")
				.commitNow();
		cachedMapFragment.getMapAsync(this);
		Log.d("dataClicked", "cachedMapFragment created and attached");
	}

	// If we want to jump straight to the world map, do it now
	if (openWorldMapByDefault) {
		openWorldMapByDefault = false;
		showWorldMap();
	} else {
		if (mMap != null && VarVault.playerLocation != null) {
			mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
					VarVault.playerLocation, zoomLevel));
		}
	}

	// --- 5️⃣  Radio view ------------------------------------------------
	if (cachedRadioView == null) {                           // first‑time: create it
		cachedRadioView = inflater.inflate(R.layout.radio, midPanel, false);
		cachedRadioView.setTag("RADIO");
	}
	// Attach the radio view (but keep it hidden for now)
	midPanel.addView(cachedRadioView);
	cachedRadioView.setVisibility(View.GONE);

	// --- 6️⃣  Attach listeners & styles to bottom buttons ---------------
	bindBottomBarButtons(bottomContent);

	Log.d("dataClicked", "dataClicked() finished");
}
	private void bindBottomBarButtons(View bottomContent) {
		// Local map
		VarVault.localMap = bottomContent.findViewById(R.id.btn_localmap);
		VarVault.localMapLL = bottomContent.findViewById(R.id.btn_localmap_box);
		setButton(VarVault.localMap, VarVault.localMapLL, VarVault.font, v -> showLocalMap());

		// World map
		VarVault.worldMap = bottomContent.findViewById(R.id.btn_worldmap);
		VarVault.worldMapLL = bottomContent.findViewById(R.id.btn_worldmap_box);
		setButton(VarVault.worldMap, VarVault.worldMapLL, VarVault.font, v -> showWorldMap());

		// Quests
		VarVault.quests = bottomContent.findViewById(R.id.btn_quests);
		VarVault.questsLL = bottomContent.findViewById(R.id.btn_quests_box);
		setButton(VarVault.quests, VarVault.questsLL, VarVault.font,v -> showQuests());

		// Notes
		VarVault.notes = bottomContent.findViewById(R.id.btn_notes);
		VarVault.notesLL = bottomContent.findViewById(R.id.btn_notes_box);
		setButton(VarVault.notes, VarVault.notesLL, VarVault.font, v ->showNotes());

		// Radio
		VarVault.radio = bottomContent.findViewById(R.id.btn_radio);
		VarVault.radioLL = bottomContent.findViewById(R.id.btn_radio_box);
		setButton(VarVault.radio, VarVault.radioLL, VarVault.font, v -> radioClicked(currentStation));
	}

	private void setButton(TextView text, View box, Typeface font,
						   View.OnClickListener onClick) {
		if (text != null) text.setTypeface(font);
		if (box != null) box.setOnClickListener(onClick);
	}

	private void showWorldMap() {

		GoogleMap map = VarVault.mMap;
		LatLng player = VarVault.playerLocation;
		Log.d("showWorldMap", "source == VarVault.localMapLL");
		if (VarVault.mMap == null || VarVault.playerLocation == null) return;
		Log.d("showWorldMap", "VarVault.mMap != null, VarVault.playerLocation != null, continuing");
		Log.d("showWorldMap", "animate camera");
		if (map == null) return;
		if (player == null) {
			Log.w("Map", "Player location not ready yet");
			return;
		}
		//if (playerLocation != currentlocation)
		Log.d("showWorldMap", "Entering animate camera.");

		CameraUpdate update = CameraUpdateFactory.newLatLngZoom(player, zoomLevel);

		// Animate safely with duration + callback
		map.animateCamera(update, 600, new GoogleMap.CancelableCallback() {
			@Override
			public void onFinish() {
				Log.d("showWorldMap", "Animate camera finished.");
			}
			@Override
			public void onCancel() {
				Log.d("showWorldMap", "Animate camera cancelled.");
			}
		});
		cachedMapView.setVisibility(View.VISIBLE);
		Log.d("showWorldMap", "cachedMapView VISIBLE");
		cachedRadioView.setVisibility(View.GONE);
		Log.d("showWorldMap", "cachedRadioView GONE");
		Log.d("showWorldMap", "ending");
	}

	private void showLocalMap() {

		Log.d("showLocalMap", "source == VarVault.localMapLL");
		if (VarVault.mMap == null || VarVault.playerLocation == null) return;
		Log.d("showLocalMap", "VarVault.mMap != null, VarVault.playerLocation != null, continuing");


		VarVault.mMap.animateCamera(
				CameraUpdateFactory.newLatLngZoom(
						VarVault.playerLocation,
						LOCAL_MAP_ZOOM
				),
				600,
				null
		);
		Log.d("showLocalMap", "animate camera");

		cachedMapView.setVisibility(View.VISIBLE);
		Log.d("showLocalMap", "cachedMapView VISIBLE");
		cachedRadioView.setVisibility(View.GONE);
		Log.d("showLocalMap", "cachedRadioView GONE");
		Log.d("showLocalMap", "ending");
	}
	private void showQuests() {

	}

	private void showNotes() {

	}
	private void lockMapNorth() {
		Log.d("lockMapNorth", "starting");

		if (VarVault.mMap == null || VarVault.playerLocation == null) {
			Toast.makeText(this, "Map not ready", Toast.LENGTH_SHORT).show();
			Log.d("lockMapNorth", "VarVault.mMap null, VarVault.playerLocation null!");
			return;
		}

		Log.d("lockMapNorth", "VarVault.mMap != null, VarVault.playerLocation != null");
//		sensorEventListener.onSensorChanged(lastCompassRotation);
		CameraPosition camPos = new CameraPosition.Builder()
				.target(playerLocation)
				.zoom(zoomLevel)
				.tilt(0)
				.build();

		Log.d("lockMapNorth", "animate camera");
		mMap.animateCamera(
				CameraUpdateFactory.newCameraPosition(camPos),
				300,
				null
		);
	}

	public void initCompassToggle() {
		Log.d("initCompassToggle", "Entering initCompassToggle.");
		compassToggle = findViewById(R.id.btnCompassToggle);
		if (compassToggle == null) {
			Log.d("initCompassToggle", "Compasstoggle is null.");
			return;
		}
		// Safety
		compassToggle.setClickable(true);
		compassToggle.setLongClickable(true);
		Log.d("initCompassToggle", "Set compass toggle clickable to true.");
		compassToggle.setFocusable(true);
		Log.d("initCompassToggle", "Set compass toggle focusable to true.");
		compassToggle.setElevation(100f);
		Log.d("initCompassToggle", "Set compass toggle elevation to 100f.");
		// click listeners (short, long, release(optional))
		// TAP → center map on player (works! Don't touch :p)
		compassToggle.setOnLongClickListener(v -> {
			if (VarVault.mMap == null) return true;
			isMapRotationEnabled = !isMapRotationEnabled;
			UiSettings ui = VarVault.mMap.getUiSettings();
			ui.setRotateGesturesEnabled(isMapRotationEnabled);
			if (!isMapRotationEnabled) {
				Log.d("initCompassToggle", "Disabling map rotation.");
//				hideMapUI();
			} else {
				Log.d("initCompassToggle", "Enabling map rotation.");

				lockMapNorth(); // reset bearing to north
				updateCompassIcon(azimuthDeg);

//				hideMapUI();
			}
			v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
			Log.d("initCompassToggle", "haptic feedback for long press.");

			return true;
		});
		compassToggle.setOnClickListener(v -> {
			Log.d("initCompassToggle", "Compasstoggle was clicked.");
			toggleLocation();

//			hideMapUI();
		});

	}

	private void updateCompassIcon(float azimuthDeg) {
		if (compassToggle == null) {
			Log.d("updateCompassIcon", "compassToggle is null, can't continue");
			return;
		}
		// Only rotate the icon if map rotation is enabled
		if (isMapRotationEnabled) {
//			Log.d("updateCompassIcon", "isMapRotationEnabled true");
			// Animate the rotation
			RotateAnimation rotateAnim = new RotateAnimation(
					lastCompassRotation,
					azimuthDeg, // negative to rotate opposite to device heading
					Animation.RELATIVE_TO_SELF, 0.5f,
					Animation.RELATIVE_TO_SELF, 0.5f
			);
			rotateAnim.setDuration(250);
			rotateAnim.setFillAfter(true);
			rotateAnim.setInterpolator(new LinearInterpolator());
			compassToggle.startAnimation(rotateAnim);
			lastCompassRotation = azimuthDeg;
		} else {
//			Log.d("updateCompassIcon", "isMapRotationEnabled false");
		}
	}
	@SuppressLint("MissingPermission")
	@Override
	public void onMapReady(GoogleMap googleMap) {
		initCompassToggle();
		mMap = googleMap;
		VarVault.mMap = googleMap;
		// ===== Map visuals =====
		mMap.setMapType(googleMap.MAP_TYPE_NORMAL);
//		mMap.getUiSettings().setMapToolbarEnabled(false);
		mMap.setBuildingsEnabled(false);
		VarVault.mMap.getUiSettings().setCompassEnabled(false);
		mMap.getUiSettings().setMapToolbarEnabled(false);

		mMap.getUiSettings().setIndoorLevelPickerEnabled(false);

		//has to be last! otherwise it will appear again.
		VarVault.mMap.getUiSettings().setCompassEnabled(false);
		mMap.getUiSettings().setMapToolbarEnabled(false);
//		hideMapUI();
//		VarVault.mMap.getUiSettings().setMyLocationButtonEnabled(false);
		try {
			MapStyleOptions style = MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style);
			boolean success = mMap.setMapStyle(style);
			Log.d("MapStyle", "JSON Map style applied: " + success);
		} catch (Resources.NotFoundException e) {
			Log.e("MapStyle", "Style JSON not found!", e);
		}

		// ===== Location permissions =====
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
				== PackageManager.PERMISSION_GRANTED) {
			mMap.setMyLocationEnabled(VarVault.setMyLocationEnabledBool);
			//then
			mMap.getUiSettings().setMyLocationButtonEnabled(VarVault.setMyLocationButtonEnabledBool);

			// Get last known location safely
			FusedLocationProviderClient fusedLocationClient =
					LocationServices.getFusedLocationProviderClient(this);

			fusedLocationClient.getLastLocation()
					.addOnSuccessListener(location -> {
						if (location != null) {
							LatLng playerLatLng = new LatLng(
									location.getLatitude(),
									location.getLongitude()
							);
							VarVault.playerLocation = playerLatLng;
						}
						//hide location button again
//						hideMapUI();
//						VarVault.mMap.getUiSettings().setMyLocationButtonEnabled(false);
					});

		} else {
			requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
		}

		// Store map for later use
		VarVault.mMap = googleMap;
		// Now the map is ready, you can enable location safely
		enableLocation();
	}

	private boolean enableLocation() {
		Log.d("enableLocation", "Entering enableLocation.");

		// Permission check
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
			Log.d("enableLocation", "Requesting permission to access location.");
			return true;
		} else {
			Log.d("enableLocation", "Permission granted for accessing location.");
		}

		// Map must be ready
		if (VarVault.mMap == null) {
			Log.d("enableLocation", "Map is not ready.");
			return false;
		} else {
			Log.d("enableLocation", "Map is ready.");
		}

		// Enable location layer
		try {
			mMap.setMyLocationEnabled(true);
			mMap.getUiSettings().setMyLocationButtonEnabled(true);
			Log.d("enableLocation", "Enabled location layer and my location button.");

			// Get last known location
			FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
			fusedLocationClient.getLastLocation()
					.addOnSuccessListener(this, location -> {
						if (location == null) {
							Log.d("enableLocation", "Unable to get current location.");
							Toast.makeText(MainMenu.this, "Unable to get current location", Toast.LENGTH_SHORT).show();
							return;
						}

						LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
						Log.d("enableLocation", "Got current location: " + currentLocation);
						mMap.clear();
						// either
//				Marker playerStart = mMap.addMarker(new MarkerOptions()
//						.position(currentLocation)
//						.icon(createCustomMarker(Color.parseColor("#00FF6E"), Color.parseColor("#007803"), 24))
//						.anchor(0.5f, 0.5f)); // center the marker
//						.title("This is where you started!"));
						// or
//				mMap.addMarker(new MarkerOptions()
//						.position(currentLocation)
//						.icon(createCustomMarkerWithLabel(
//								Color.parseColor("#007803"),
//								Color.parseColor("#00FF6E"),
//								10,             // radius in dp
//								"Where you started"             // your label
//						))
//						.anchor(0.5f, 0.5f)); // center marker

						VarVault.playerLocation = currentLocation;


					});
			return true;

		} catch (SecurityException e) {
			Log.d("enableLocation", "Got security exception.");
			// Safety net
			return false;
		}
	}

	private void disableLocation() {
		if (VarVault.mMap == null) return;

		try {
			VarVault.mMap.setMyLocationEnabled(false);
			Log.d("disableLocation", "set location enabled FALSE");
		} catch (SecurityException ignored) {
		}
		Log.d("disableLocation", "security exception ignored?");
		isLocationEnabled = false;

		// Optional cleanup
		// mMap.clear();   // only if you want markers gone
	}

	private void toggleLocation() {
		if (isLocationEnabled) {
			Log.d("Compass", "Tap detected: disable location");
			disableLocation();

			hideMapUI();
		} else {
			enableLocation();
			mMap.moveCamera(
					CameraUpdateFactory.newLatLngZoom(
							VarVault.playerLocation, zoomLevel));
			hideMapUI();

		}
	}
	public void hideMapUI() {
		Log.d("hideMapUI", "Entering hideMapUI.");

		// has to be last! otherwise it will appear again.
		VarVault.mMap.getUiSettings().setCompassEnabled(false);
		mMap.getUiSettings().setMapToolbarEnabled(false);
//		hideMapUI();
		//		VarVault.mMap.getUiSettings().setMyLocationButtonEnabled(false);

		Log.d("hideMapUI", "Hiding map UI.");
	}
	private void showMap() {
		ViewGroup midPanel = findViewById(R.id.mid_panel);
		if (midPanel == null) return;

		View map = midPanel.findViewWithTag("MAP");
		View radio = midPanel.findViewWithTag("RADIO");

		if (radio != null) radio.setVisibility(View.GONE);
		if (map != null) map.setVisibility(View.VISIBLE);
	}








	// STATS clicked
	// -----------------------------
	// Stats screen
	// -----------------------------
	private void statsClicked() {
		initStatsView();
		// Format top bar text
		VarVault.title = (TextView) findViewById(R.id.title_stats);
		VarVault.title.setText("STATUS");
		VarVault.title.setTypeface(VarVault.font);

		VarVault.hp = (TextView) findViewById(R.id.hp_stats);
		VarVault.hp.setTypeface(VarVault.font);

		VarVault.ap = (TextView) findViewById(R.id.ap_stats);
		VarVault.ap.setTypeface(VarVault.font);

		VarVault.bat = (TextView) findViewById(R.id.bat_stats);
		VarVault.bat.setTypeface(VarVault.font);
		this.registerReceiver(VarVault.mBatInfoReceiver, new IntentFilter(
				Intent.ACTION_BATTERY_CHANGED));

		// Button-ize the buttons
		VarVault.status = (TextView) findViewById(R.id.btn_status);
		VarVault.statusLL = (LinearLayout) findViewById(R.id.btn_status_box);
		VarVault.status.setTypeface(VarVault.font);
		VarVault.statusLL.setOnClickListener(this);

		VarVault.special = (TextView) findViewById(R.id.btn_special);
		VarVault.specialLL = (LinearLayout) findViewById(R.id.btn_special_box);
		VarVault.special.setTypeface(VarVault.font);
		VarVault.specialLL.setOnClickListener(this);

		VarVault.skills = (TextView) findViewById(R.id.btn_skills);
		VarVault.skillsLL = (LinearLayout) findViewById(R.id.btn_skills_box);
		VarVault.skills.setTypeface(VarVault.font);
		VarVault.skillsLL.setOnClickListener(this);

		VarVault.perks = (TextView) findViewById(R.id.btn_perks);
		VarVault.perksLL = (LinearLayout) findViewById(R.id.btn_perks_box);
		VarVault.perks.setTypeface(VarVault.font);
		VarVault.perksLL.setOnClickListener(this);

		VarVault.general = (TextView) findViewById(R.id.btn_general);
		VarVault.generalLL = (LinearLayout) findViewById(R.id.btn_general_box);
		VarVault.general.setTypeface(VarVault.font);
		VarVault.generalLL.setOnClickListener(this);

		statusClicked();
		Log.d("statsClicked", "Statsclicked ended!");

//		TO-DO refactor
//		Log.e("statsClicked()", "statsClicked START");
//		// Clear crap
//
//		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
//		ViewGroup topBar = (ViewGroup) findViewById(R.id.top_bar);
//		ViewGroup bottomBar = (ViewGroup) findViewById(R.id.bottom_bar);
//
//		// Check if midPanel is null
//		if (midPanel != null) {
//			Log.e("StatsClicked", "midPanel is null! Cannot remove views.");
//		} else {
//			midPanel.removeAllViews();  // Proceed with removing all views safely
//		}
//
//		// Check if topBar is null
//		if (topBar != null) {
//			topBar.removeAllViews();  // Proceed with removing all views safely
//		} else {
//			Log.e("StatsClicked", "top_bar is null! Cannot remove views.");
//		}
//
//		// Check if bottomBar is null
//		if (bottomBar != null) {
//			bottomBar.removeAllViews();  // Proceed with removing all views safely
//		} else {
//			Log.e("StatsClicked", "bottom_bar is null! Cannot remove views.");
//		}
//
//		LayoutInflater inf = this.getLayoutInflater();
//
//		// Inflate new layouts
//		if (midPanel != null) {
//			inf.inflate(R.layout.status_screen, midPanel);
//		} else {
//			Log.e("StatsClicked", "midPanel is still null after check! Cannot inflate status_screen.");
//		}
//
//		if (topBar != null) {
//			inf.inflate(R.layout.stats_bar_top, topBar);
//		} else {
//			Log.e("StatsClicked", "topBar is still null after check! Cannot inflate stats_bar_top.");
//		}
//
//		if (bottomBar != null) {
//			inf.inflate(R.layout.stats_bar_bottom, bottomBar);
//		} else {
//			Log.e("StatsClicked", "bottomBar is still null after check! Cannot inflate stats_bar_bottom.");
//		}
//
//		// Format top bar text
//		VarVault.title = (TextView) findViewById(R.id.title_stats);
//		if (VarVault.title != null) {
//			VarVault.title.setText("STATUS");
//			VarVault.title.setTypeface(VarVault.font);
//		} else {
//			Log.e("StatsClicked", "title_stats is null! Cannot set title text.");
//		}
//
//		VarVault.hp = (TextView) findViewById(R.id.hp_stats);
//		if (VarVault.hp != null) {
//			VarVault.hp.setTypeface(VarVault.font);
//		}
//
//		VarVault.ap = (TextView) findViewById(R.id.ap_stats);
//		if (VarVault.ap != null) {
//			VarVault.ap.setTypeface(VarVault.font);
//		}
//
//		VarVault.bat = (TextView) findViewById(R.id.bat_stats);
//		if (VarVault.bat != null) {
//			VarVault.bat.setTypeface(VarVault.font);
//			this.registerReceiver(VarVault.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
//		}

// Button-ize the buttons
		VarVault.status = (TextView) findViewById(R.id.btn_status);
		VarVault.statusLL = (LinearLayout) findViewById(R.id.btn_status_box);
		if (VarVault.status != null && VarVault.statusLL != null) {
			VarVault.status.setTypeface(VarVault.font);
			VarVault.statusLL.setOnClickListener(this);
		} else {
			Log.e("statsClicked", "status or statusLL is null! Cannot set OnClickListener.");
		}

		VarVault.special = (TextView) findViewById(R.id.btn_special);
		VarVault.specialLL = (LinearLayout) findViewById(R.id.btn_special_box);
		if (VarVault.special != null && VarVault.specialLL != null) {
			VarVault.special.setTypeface(VarVault.font);
			VarVault.specialLL.setOnClickListener(this);
		} else {
			Log.e("statsClicked", "special or specialLL is null! Cannot set OnClickListener.");
		}

		VarVault.skills = (TextView) findViewById(R.id.btn_skills);
		VarVault.skillsLL = (LinearLayout) findViewById(R.id.btn_skills_box);
		if (VarVault.skills != null && VarVault.skillsLL != null) {
			VarVault.skills.setTypeface(VarVault.font);
			VarVault.skillsLL.setOnClickListener(this);
		} else {
			Log.e("statsClicked", "skills or skillsLL is null! Cannot set OnClickListener.");
		}

		VarVault.perks = (TextView) findViewById(R.id.btn_perks);
		VarVault.perksLL = (LinearLayout) findViewById(R.id.btn_perks_box);
		if (VarVault.perks != null && VarVault.perksLL != null) {
			VarVault.perks.setTypeface(VarVault.font);
			VarVault.perksLL.setOnClickListener(this);
		} else {
			Log.e("statsClicked", "perks or perksLL is null! Cannot set OnClickListener.");
		}

		VarVault.general = (TextView) findViewById(R.id.btn_general);
		VarVault.generalLL = (LinearLayout) findViewById(R.id.btn_general_box);
		if (VarVault.general != null && VarVault.generalLL != null) {
			VarVault.general.setTypeface(VarVault.font);
			VarVault.generalLL.setOnClickListener(this);
		} else {
			Log.e("statsClicked", "general or generalLL is null! Cannot set OnClickListener.");
		}


		statusClicked();

	}
private void initStatsView() {
	Log.d("statsClicked", "Entering statsClicked method");
	// Clear crap
	ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
	Log.d("statsClicked", "midPanel found: " + midPanel);
	ViewGroup topBar = (ViewGroup) findViewById(R.id.top_bar);
	Log.d("statsClicked", "topBar found: " + topBar);
	ViewGroup bottomBar = (ViewGroup) findViewById(R.id.bottom_bar);
	Log.d("statsClicked", "bottomBar found: " + bottomBar);
	LayoutInflater inf = this.getLayoutInflater();
	Log.d("statsClicked", "inflater found: " + inf);
	// Default visibility
//		midPanel.addView(cachedMapView);
//		if (cachedMapView != null) {
//			cachedMapView.setVisibility(View.GONE);
//			Log.d("statsClicked", "Set cachedMapView GONE");
//		} else {
//			Log.d("statsClicked", "cachedMapView null, return");
//			return;
//		}
//		cachedRadioView.setVisibility(View.GONE);
	Log.d("statsClicked", "Set cachedMapView visible, cachedRadioView gone");
	midPanel.removeAllViews();
	Log.d("statsClicked", "Removed all views from midPanel");
	topBar.removeAllViews();
	Log.d("statsClicked", "Removed all views from topBar");
	bottomBar.removeAllViews();
	Log.d("statsClicked", "Removed all views from bottomBar");

	// Main screen turn on
	Log.d("statsClicked", "Main screen turn on");
	inf.inflate(R.layout.status_screen, midPanel);
	Log.d("statsClicked", "Inflated status_screen layout into midPanel");
	inf.inflate(R.layout.stats_bar_top, topBar);
	Log.d("statsClicked", "Inflated stats_bar_top layout into topBar");
	inf.inflate(R.layout.stats_bar_bottom, bottomBar);
	Log.d("statsClicked", "Inflated stats_bar_bottom layout into bottomBar");
}


	// STATUS clicked
	// -----------------------------
	// Status tab click
	// -----------------------------
	private void statusClicked() {
		initStatusView();

		Log.d("statusClicked", "Inflated status screen view.");
		VarVault.title.setText("STATUS");
		Log.d("statusClicked", "Set title TextView to 'STATUS'.");
		VarVault.cnd = (TextView) findViewById(R.id.btn_cnd);
		VarVault.cnd.setTypeface(VarVault.font);
		Log.d("statusClicked", "Bound CND button and set typeface.");
		VarVault.rad = (TextView) findViewById(R.id.btn_rad);
		VarVault.rad.setTypeface(VarVault.font);
		Log.d("statusClicked", "Bound RAD button and set typeface.");
		VarVault.stimpak = (TextView) findViewById(R.id.btn_stimpak);
		VarVault.stimpak.setTypeface(VarVault.font);
		Log.d("statusClicked", "Bound Stimpak button and set typeface.");
		VarVault.flashlight = (TextView) findViewById(R.id.btn_flashlight);
		VarVault.flashlight.setTypeface(VarVault.font);
		Log.d("statusClicked", "Bound Flashlight button and set typeface.");
	}

	private void initStatusView() {
		Log.d("statusClicked", "Entering status clicked.");
		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		LayoutInflater inf = this.getLayoutInflater();
		midPanel.removeAllViews();
		Log.d("statusClicked", "Removed all views from main panel.");
		inf.inflate(R.layout.status_screen, midPanel);
	}
	private void specialClicked() {
		initSpecialView();
		VarVault.title.setText("SPECIAL");
		Log.d("specialClicked", "Set text to SPECIAL");
		VarVault.specialImage = (ImageView) findViewById(R.id.special_image);
		Log.d("specialClicked", "specialImage found: " + VarVault.specialImage);
		bindSpecialViews();
		InitializeArrays.submenu_special();
	}

	private void initSpecialView() {
		Log.d("specialClicked", "Entering specialClicked method");
//		resetScreen();
		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		Log.d("specialClicked", "midPanel found: " + midPanel);
		LayoutInflater inf = this.getLayoutInflater();
		Log.d("specialClicked", "inflater found: " + inf);
		midPanel.removeAllViews();
		Log.d("specialClicked", "Removed all views from midPanel");
		inf.inflate(R.layout.special_screen, midPanel);
	}

	private void bindSpecialViews() {
		VarVault.str = (TextView) findViewById(R.id.text_strength);
		VarVault.str.setTypeface(VarVault.font);
		VarVault.str.setOnClickListener(this);
		VarVault.strSTAT = (TextView) findViewById(R.id.strength_stat);
		VarVault.strSTAT.setText(String.valueOf(VarVault.strength.getValue()));
		VarVault.strSTAT.setTypeface(VarVault.font);

		VarVault.per = (TextView) findViewById(R.id.text_perception);
		VarVault.per.setTypeface(VarVault.font);
		VarVault.per.setOnClickListener(this);
		VarVault.perSTAT = (TextView) findViewById(R.id.perception_stat);
		VarVault.perSTAT.setText(String.valueOf(VarVault.perception.getValue()));
		VarVault.perSTAT.setTypeface(VarVault.font);

		VarVault.end = (TextView) findViewById(R.id.text_endurance);
		VarVault.end.setTypeface(VarVault.font);
		VarVault.end.setOnClickListener(this);
		VarVault.endSTAT = (TextView) findViewById(R.id.endurance_stat);
		VarVault.endSTAT.setText(String.valueOf(VarVault.endurance.getValue()));
		VarVault.endSTAT.setTypeface(VarVault.font);

		VarVault.chr = (TextView) findViewById(R.id.text_charisma);
		VarVault.chr.setTypeface(VarVault.font);
		VarVault.chr.setOnClickListener(this);
		VarVault.chrSTAT = (TextView) findViewById(R.id.charisma_stat);
		VarVault.chrSTAT.setText(String.valueOf(VarVault.charisma.getValue()));
		VarVault.chrSTAT.setTypeface(VarVault.font);

		VarVault.intel = (TextView) findViewById(R.id.text_intelligence);
		VarVault.intel.setTypeface(VarVault.font);
		VarVault.intel.setOnClickListener(this);
		VarVault.intelSTAT = (TextView) findViewById(R.id.intelligence_stat);
		VarVault.intelSTAT.setText(String.valueOf(VarVault.intelligence.getValue()));
		VarVault.intelSTAT.setTypeface(VarVault.font);

		VarVault.agi = (TextView) findViewById(R.id.text_agility);
		VarVault.agi.setTypeface(VarVault.font);
		VarVault.agi.setOnClickListener(this);
		VarVault.agiSTAT = (TextView) findViewById(R.id.agility_stat);
		VarVault.agiSTAT.setText(String.valueOf(VarVault.agility.getValue()));
		VarVault.agiSTAT.setTypeface(VarVault.font);

		VarVault.luk = (TextView) findViewById(R.id.text_luck);
		VarVault.luk.setTypeface(VarVault.font);
		VarVault.luk.setOnClickListener(this);
		VarVault.lukSTAT = (TextView) findViewById(R.id.luck_stat);
		VarVault.lukSTAT.setText(String.valueOf(VarVault.luck.getValue()));
		VarVault.lukSTAT.setTypeface(VarVault.font);
	}

	private void skillsClicked() {
		Log.d("skillsClicked", "Entering skillsClicked method");
		int allocatedpoints = (VarVault.barter.getValue() + VarVault.big_guns.getValue()
				+ VarVault.energy.getValue() + VarVault.explosives.getValue()
				+ VarVault.lockpick.getValue() + VarVault.medicine.getValue() + VarVault.melee.getValue()
				+ VarVault.repair.getValue() + VarVault.science.getValue()
				+ VarVault.small_guns.getValue() + VarVault.sneak.getValue() + VarVault.speech.getValue() + VarVault.unarmed
				.getValue());
		Log.d("allocatedpoints", "Allocated points: " + allocatedpoints);
		if ((VarVault.numContacts + 130) > allocatedpoints) {
			// Allocate unused points
			Intent i = new Intent(MainMenu.this, SetSkills.class);
			MainMenu.this.startActivityForResult(i, 1);
		} else {
			// Show skills screen
			Log.d("skillsScreen", "Showing skills screen");
			ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
			LayoutInflater inf = this.getLayoutInflater();
			midPanel.removeAllViews();
			inf.inflate(R.layout.skills_screen, midPanel);
			VarVault.skillImage = (ImageView) findViewById(R.id.skills_image);
			Log.d("VarVault.skillImage", "skillImage found: " + VarVault.skillImage);
			VarVault.title.setText("SKILLS");
			Log.d("VarVault.title", "Set text to SKILLS");
			VarVault.bart = (TextView) findViewById(R.id.text_barter);
			Log.d("VarVault.bart", "bart found: " + VarVault.bart);
			VarVault.barterSTAT = (TextView) findViewById(R.id.barter_stat);
			Log.d("VarVault.barterSTAT", "barterSTAT found: " + VarVault.barterSTAT);
			VarVault.bgns = (TextView) findViewById(R.id.text_big_guns);
			Log.d("VarVault.bgns", "bgns found: " + VarVault.bgns);
			VarVault.big_gunsSTAT = (TextView) findViewById(R.id.big_guns_stat);
			Log.d("VarVault.big_gunsSTAT", "big_gunsSTAT found: " + VarVault.big_gunsSTAT);
			VarVault.nrg = (TextView) findViewById(R.id.text_energy);
			Log.d("VarVault.nrg", "nrg found: " + VarVault.nrg);
			VarVault.energySTAT = (TextView) findViewById(R.id.energy_stat);
			Log.d("VarVault.energySTAT", "energySTAT found: " + VarVault.energySTAT);
			VarVault.expl = (TextView) findViewById(R.id.text_explosives);
			Log.d("VarVault.expl", "expl found: " + VarVault.expl);
			VarVault.explosivesSTAT = (TextView) findViewById(R.id.explosives_stat);
			Log.d("VarVault.explosivesSTAT", "explosivesSTAT found: " + VarVault.explosivesSTAT);
			VarVault.lock = (TextView) findViewById(R.id.text_lockpick);
			Log.d("VarVault.lock", "lock found: " + VarVault.lock);
			VarVault.lockpickSTAT = (TextView) findViewById(R.id.lockpick_stat);
			Log.d("VarVault.lockpickSTAT", "lockpickSTAT found: " + VarVault.lockpickSTAT);
			VarVault.medi = (TextView) findViewById(R.id.text_medicine);
			Log.d("VarVault.medi", "medi found: " + VarVault.medi);
			VarVault.medicineSTAT = (TextView) findViewById(R.id.medicine_stat);
			Log.d("VarVault.medicineSTAT", "medicineSTAT found: " + VarVault.medicineSTAT);
			VarVault.mlee = (TextView) findViewById(R.id.text_melee);
			Log.d("VarVault.mlee", "mlee found: " + VarVault.mlee);
			VarVault.meleeSTAT = (TextView) findViewById(R.id.melee_stat);
			Log.d("VarVault.meleeSTAT", "meleeSTAT found: " + VarVault.meleeSTAT);
			VarVault.rpar = (TextView) findViewById(R.id.text_repair);
			Log.d("VarVault.rpar", "rpar found: " + VarVault.rpar);
			VarVault.repairSTAT = (TextView) findViewById(R.id.repair_stat);
			Log.d("VarVault.repairSTAT", "repairSTAT found: " + VarVault.repairSTAT);
			VarVault.sci = (TextView) findViewById(R.id.text_science);
			Log.d("VarVault.sci", "sci found: " + VarVault.sci);
			VarVault.scienceSTAT = (TextView) findViewById(R.id.science_stat);
			Log.d("VarVault.scienceSTAT", "scienceSTAT found: " + VarVault.scienceSTAT);
			VarVault.sgns = (TextView) findViewById(R.id.text_small_guns);
			Log.d("VarVault.sgns", "sgns found: " + VarVault.sgns);
			VarVault.small_gunsSTAT = (TextView) findViewById(R.id.small_guns_stat);
			Log.d("VarVault.small_gunsSTAT", "small_gunsSTAT found: " + VarVault.small_gunsSTAT);
			VarVault.snek = (TextView) findViewById(R.id.text_sneak);
			Log.d("VarVault.snek", "snek found: " + VarVault.snek);
			VarVault.sneakSTAT = (TextView) findViewById(R.id.sneak_stat);
			Log.d("VarVault.sneakSTAT", "sneakSTAT found: " + VarVault.sneakSTAT);
			VarVault.spch = (TextView) findViewById(R.id.text_speech);
			Log.d("VarVault.spch", "spch found: " + VarVault.spch);
			VarVault.speechSTAT = (TextView) findViewById(R.id.speech_stat);
			Log.d("VarVault.speechSTAT", "speechSTAT found: " + VarVault.speechSTAT);
			VarVault.uarm = (TextView) findViewById(R.id.text_unarmed);
			Log.d("VarVault.uarm", "uarm found: " + VarVault.uarm);
			VarVault.unarmedSTAT = (TextView) findViewById(R.id.unarmed_stat);
			Log.d("VarVault.unarmedSTAT", "unarmedSTAT found: " + VarVault.unarmedSTAT);
			InitializeArrays.submenu_skills();
		}
	}
	private void skillStatClicked(View source) {

		if (source == VarVault.bart || source == VarVault.barterSTAT)
			VarVault.skillImage.setImageResource(R.drawable.barter);
		else if (source == VarVault.bgns || source == VarVault.big_gunsSTAT)
			VarVault.skillImage.setImageResource(R.drawable.big_guns);
		else if (source == VarVault.nrg || source == VarVault.energySTAT)
			VarVault.skillImage.setImageResource(R.drawable.energy);
		else if (source == VarVault.expl || source == VarVault.explosivesSTAT)
			VarVault.skillImage.setImageResource(R.drawable.explosives);
		else if (source == VarVault.lock || source == VarVault.lockpickSTAT)
			VarVault.skillImage.setImageResource(R.drawable.lockpick);
		else if (source == VarVault.medi || source == VarVault.medicineSTAT)
			VarVault.skillImage.setImageResource(R.drawable.medicine);
		else if (source == VarVault.mlee || source == VarVault.meleeSTAT)
			VarVault.skillImage.setImageResource(R.drawable.melee);
		else if (source == VarVault.rpar || source == VarVault.repairSTAT)
			VarVault.skillImage.setImageResource(R.drawable.repair);
		else if (source == VarVault.sci || source == VarVault.scienceSTAT)
			VarVault.skillImage.setImageResource(R.drawable.science);
		else if (source == VarVault.sgns || source == VarVault.small_gunsSTAT)
			VarVault.skillImage.setImageResource(R.drawable.small_guns);
		else if (source == VarVault.snek || source == VarVault.sneakSTAT)
			VarVault.skillImage.setImageResource(R.drawable.sneak);
		else if (source == VarVault.spch || source == VarVault.speechSTAT)
			VarVault.skillImage.setImageResource(R.drawable.speech);
		else if (source == VarVault.uarm || source == VarVault.unarmedSTAT)
			VarVault.skillImage.setImageResource(R.drawable.unarmed);
	}

	private void specialStatClicked(View source) {
		if (source == VarVault.str)
			VarVault.specialImage.setImageResource(R.drawable.strength);
		else if (source == VarVault.per)
			VarVault.specialImage.setImageResource(R.drawable.perception);
		else if (source == VarVault.end)
			VarVault.specialImage.setImageResource(R.drawable.endurance);
		else if (source == VarVault.chr)
			VarVault.specialImage.setImageResource(R.drawable.charisma);
		else if (source == VarVault.intel)
			VarVault.specialImage.setImageResource(R.drawable.intelligence);
		else if (source == VarVault.agi)
			VarVault.specialImage.setImageResource(R.drawable.agility);
		else if (source == VarVault.luk)
			VarVault.specialImage.setImageResource(R.drawable.luck);
	}

	private void initCaps() {
		SharedPreferences prefs = getSharedPreferences("STATS", 0);

		VarVault.curCaps.setValue(prefs.getInt("CAPS", 2000));
		Log.i("VALUE", "The cap value is " + VarVault.curCaps.getValue() + " at this time.");
	}

	private void initSpecial() {
		SharedPreferences prefs = getSharedPreferences("SPECIAL", 0);
		// prefs.edit().clear().commit();

		if (!prefs.contains("STRENGTH")) {
			Intent i = new Intent(MainMenu.this, SetSpecial.class);
			MainMenu.this.startActivityForResult(i, 0);
		} else {
			VarVault.strength.setValue(prefs.getInt("STRENGTH", -2));
			VarVault.perception.setValue(prefs.getInt("PERCEPTION", -2));
			VarVault.endurance.setValue(prefs.getInt("ENDURANCE", -2));
			VarVault.charisma.setValue(prefs.getInt("CHARISMA", -2));
			VarVault.intelligence.setValue(prefs.getInt("INTELLIGENCE", -2));
			VarVault.agility.setValue(prefs.getInt("AGILITY", -2));
			VarVault.luck.setValue(prefs.getInt("LUCK", -2));
		}
	}

	private void initSkills() {
		SharedPreferences prefs = getSharedPreferences("SKILLS", 0);
		// prefs.edit().clear().commit();

		if (!prefs.contains("BARTER")) {
			Intent i = new Intent(MainMenu.this, SetSkills.class);
			MainMenu.this.startActivityForResult(i, 1);
		} else {
			VarVault.barter.setValue(prefs.getInt("BARTER", -2));
			VarVault.big_guns.setValue(prefs.getInt("BIG_GUNS", -2));
			VarVault.energy.setValue(prefs.getInt("ENERGY", -2));
			VarVault.explosives.setValue(prefs.getInt("EXPLOSIVES", -2));
			VarVault.lockpick.setValue(prefs.getInt("LOCKPICK", -2));
			VarVault.medicine.setValue(prefs.getInt("MEDICINE", -2));
			VarVault.melee.setValue(prefs.getInt("MELEE", -2));
			VarVault.repair.setValue(prefs.getInt("REPAIR", -2));
			VarVault.science.setValue(prefs.getInt("SCIENCE", -2));
			VarVault.small_guns.setValue(prefs.getInt("SMALL_GUNS", -2));
			VarVault.sneak.setValue(prefs.getInt("SNEAK", -2));
			VarVault.speech.setValue(prefs.getInt("SPEECH", -2));
			VarVault.unarmed.setValue(prefs.getInt("UNARMED", -2));

			if ((VarVault.numContacts + 130) > (VarVault.barter.getValue() + VarVault.big_guns.getValue()
					+ VarVault.energy.getValue() + VarVault.explosives.getValue()
					+ VarVault.lockpick.getValue() + VarVault.medicine.getValue()
					+ VarVault.melee.getValue() + VarVault.repair.getValue() + VarVault.science.getValue()
					+ VarVault.small_guns.getValue() + VarVault.sneak.getValue()
					+ VarVault.speech.getValue() + VarVault.unarmed.getValue())) {
				Intent i = new Intent(Intent.ACTION_MAIN)
						.setClassName(MainMenu.this, "com.skettidev.pipdroid.Splash");
				MainMenu.this.startActivityForResult(i, 1);
			}

			onActivityResult(1, RESULT_OK, null);
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 0 && resultCode == RESULT_OK) {
			SharedPreferences prefs = getSharedPreferences("SPECIAL", 0);
			VarVault.strength.setValue(prefs.getInt("STRENGTH", -2));
			VarVault.endurance.setValue(prefs.getInt("ENDURANCE", -2));
			VarVault.charisma.setValue(prefs.getInt("CHARISMA", -2));
			VarVault.intelligence.setValue(prefs.getInt("INTELLIGENCE", -2));
			VarVault.agility.setValue(prefs.getInt("AGILITY", -2));
			VarVault.luck.setValue(prefs.getInt("LUCK", -2));
		}

		if (requestCode == 1 && resultCode == RESULT_OK) {
			SharedPreferences prefs = getSharedPreferences("SKILLS", 0);
			VarVault.barter.setValue(prefs.getInt("BARTER", -2));
			VarVault.big_guns.setValue(prefs.getInt("BIG_GUNS", -2));
			VarVault.energy.setValue(prefs.getInt("ENERGY", -2));
			VarVault.explosives.setValue(prefs.getInt("EXPLOSIVES", -2));
			VarVault.lockpick.setValue(prefs.getInt("LOCKPICK", -2));
			VarVault.medicine.setValue(prefs.getInt("MEDICINE", -2));
			VarVault.melee.setValue(prefs.getInt("MELEE", -2));
			VarVault.repair.setValue(prefs.getInt("REPAIR", -2));
			VarVault.science.setValue(prefs.getInt("SCIENCE", -2));
			VarVault.small_guns.setValue(prefs.getInt("SMALL_GUNS", -2));
			VarVault.sneak.setValue(prefs.getInt("SNEAK", -2));
			VarVault.speech.setValue(prefs.getInt("SPEECH", -2));
			VarVault.unarmed.setValue(prefs.getInt("UNARMED", -2));
		}

	}

	@Override
	protected void onPause() {
		// Screen flag
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Camera safety
		if (VarVault.isCamOn && VarVault.mCamera != null) {
			try {
				Parameters params = VarVault.mCamera.getParameters();
				params.setFlashMode(Parameters.FLASH_MODE_OFF);
				VarVault.mCamera.setParameters(params);
				VarVault.mCamera.stopPreview();
				VarVault.mCamera.release();
			} catch (Exception e) {
				// Camera might already be released — ignore safely
			}

			VarVault.mCamera = null;
			VarVault.isCamOn = false;
		}

		// Sensor safety (THIS fixes your crash)
		if (sensorManager != null) {
			sensorManager.unregisterListener(sensorEventListener);
		}

		super.onPause();
	}


	// onResume()
	protected void onResume() {
		super.onResume();
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Recheck permissions on activity resume
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			enableLocation();  // Enable location if permission is granted
		}

		if (sensorManager != null) {
			if (accelerometer != null) {
				sensorManager.registerListener(
						sensorEventListener,
						accelerometer,
						SensorManager.SENSOR_DELAY_UI
				);
			}

			if (magnetometer != null) {
				sensorManager.registerListener(
						sensorEventListener,
						magnetometer,
						SensorManager.SENSOR_DELAY_UI
				);
			}
		}


	}
	@Override
	protected void onDestroy() {
		// Clear screen-on flag
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Release camera safely
		if (VarVault.isCamOn && VarVault.mCamera != null) {
			try {
				Parameters params = VarVault.mCamera.getParameters();
				params.setFlashMode(Parameters.FLASH_MODE_OFF);
				VarVault.mCamera.setParameters(params);
				VarVault.mCamera.stopPreview();
				VarVault.mCamera.release();
			} catch (Exception e) {
				Log.w("MainMenu", "Camera cleanup failed", e);
			} finally {
				VarVault.mCamera = null;
				VarVault.isCamOn = false;
			}
		}


		VarVault.clearViews();

		super.onDestroy();
	}


	public void surfaceChanged(SurfaceHolder holder, int format, int width,
							   int height) {
	}

	public void surfaceCreated(SurfaceHolder holder) {
		VarVault.mHolder = holder;
		try {
			VarVault.mCamera.setPreviewDisplay(VarVault.mHolder);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		VarVault.mCamera.stopPreview();
		VarVault.mHolder = null;
	}



	public boolean onLongClick(View source) {
		boolean result = false;
		// TODO For Items, wear them.
		ContentValues values = new ContentValues();
		ContentValues NOTvalues = new ContentValues();

		if (VarVault.Weapons.contains(source)) {
			dbHelper database = new dbHelper(MainMenu.this);

			Log.d("DB", "Getting a writable database...");
			SQLiteDatabase db = database.getWritableDatabase();
			Log.d("DB", "...writable database gotten!");

			Log.d("DB", "Querying DB's current status...");

			String clipped = (String) VarVault.Weapons.get(VarVault.Weapons.indexOf(source)).getText();
			clipped = clipped.replace('\u25a0', ' ');
			clipped = clipped.replaceAll("^\\s+", "");


			Cursor currentWeapon = db.query("OwnedWeapons", new String[]{dbHelper.colName, dbHelper.colIsWearing}, "WeaponName='" + clipped + "'", null, null, null, "_id");

			if (currentWeapon.moveToFirst() != false) {
				int isWearing = currentWeapon.getInt(1);
				Log.d("DB", "...got the info!");
				if (isWearing == 1) {
					values.put(dbHelper.colIsWearing, 0);
					NOTvalues.put(dbHelper.colIsWearing, 0);
				} else {
					values.put(dbHelper.colIsWearing, 1);
					NOTvalues.put(dbHelper.colIsWearing, 0);
				}
				Log.d("DB", "Trying to update equip...");
				db.update("OwnedWeapons", values, dbHelper.colName + "='" + clipped + "'", null);
				db.update("OwnedWeapons", NOTvalues, dbHelper.colName + "<>'" + clipped + "'", null);
				Log.d("DB", "...updated equipped status!");
				Cursor allWeapons = db.query("OwnedWeapons", new String[]{dbHelper.colName, dbHelper.colIsWearing}, null, null, null, null, "_id");
				allWeapons.moveToFirst();
				for (int i = 0; !allWeapons.isAfterLast(); i++) {

					String temp = allWeapons.getString(0);
					int ruWear = allWeapons.getInt(1);

					if (ruWear == 1)
						VarVault.Weapons.get(i).setText("\u25a0 " + temp);
					else
						VarVault.Weapons.get(i).setText("  " + temp);

					allWeapons.moveToNext();
				}
				updateWG();
				result = true;
			}


		} else if (VarVault.Apparel.contains(source)) {
			dbHelper database = new dbHelper(MainMenu.this);

			Log.d("DB", "Getting a writable database...");
			SQLiteDatabase db = database.getWritableDatabase();
			Log.d("DB", "...writable database gotten!");

			Log.d("DB", "Querying DB's current status...");

			String clipped = (String) VarVault.Apparel.get(VarVault.Apparel.indexOf(source)).getText();
			clipped = clipped.replace('\u25a0', ' ');
			clipped = clipped.replaceAll("^\\s+", "");


			Cursor currentApparel = db.query("OwnedApparel", new String[]{dbHelper.colName, dbHelper.colType, dbHelper.colIsWearing}, "WeaponName='" + clipped + "'", null, null, null, "_id");

			if (currentApparel.moveToFirst() != false) {
				int type = currentApparel.getInt(1);
				int isWearing = currentApparel.getInt(2);
				Log.d("DB", "...got the info!");
				if (isWearing == 1) {
					values.put(dbHelper.colIsWearing, 0);
					NOTvalues.put(dbHelper.colIsWearing, 0);
				} else {
					values.put(dbHelper.colIsWearing, 1);
					NOTvalues.put(dbHelper.colIsWearing, 0);
				}
				Log.d("DB", "Trying to update wear...");// Update wear based on type
				switch (type) {

					case 1:
						db.update("OwnedApparel", values, dbHelper.colName + "='" + clipped + "' AND " + dbHelper.colType + "=" + 1, null);
						db.update("OwnedApparel", NOTvalues, dbHelper.colName + "<>'" + clipped + "' AND (" + dbHelper.colType + "=" + 1 + " OR " + dbHelper.colType + "=" + 3 + ")", null);
						break;
					case 2:
						db.update("OwnedApparel", values, dbHelper.colName + "='" + clipped + "' AND " + dbHelper.colType + "=" + 2, null);
						db.update("OwnedApparel", NOTvalues, dbHelper.colName + "<>'" + clipped + "' AND (" + dbHelper.colType + "=" + 2 + " OR " + dbHelper.colType + "=" + 3 + ")", null);
						break;
					case 3:
						db.update("OwnedApparel", values, dbHelper.colName + "='" + clipped + "' AND " + dbHelper.colType + "=" + 3, null);
						db.update("OwnedApparel", NOTvalues, dbHelper.colName + "<>'" + clipped + "' AND (" + dbHelper.colType + "=" + 1 + " OR " + dbHelper.colType + "=" + 2 + " OR " + dbHelper.colType + "=" + 3 + ")", null);
						break;
					case 4:
						db.update("OwnedApparel", values, dbHelper.colName + "='" + clipped + "' AND " + dbHelper.colType + "=" + 4, null);
						db.update("OwnedApparel", NOTvalues, dbHelper.colName + "<>'" + clipped + "' AND " + dbHelper.colType + "=" + 4, null);
						break;
				}
				Log.d("DB", "...updated wear status!");
				Cursor allApparel = db.query("OwnedApparel", new String[]{dbHelper.colName, dbHelper.colIsWearing}, null, null, null, null, "_id");
				allApparel.moveToFirst();
				for (int i = 0; !allApparel.isAfterLast(); i++) {

					String temp = allApparel.getString(0);
					int ruWear = allApparel.getInt(1);

					if (ruWear == 1)
						VarVault.Apparel.get(i).setText("\u25a0 " + temp);
					else
						VarVault.Apparel.get(i).setText("  " + temp);

					allApparel.moveToNext();
				}
				updateWG();
				result = true;
			}


		}

		return result;
	}

	private void updateWG() {

		VarVault.curWG.setValue(0);

		dbHelper database = new dbHelper(MainMenu.this);

		Log.d("DB", "Getting a writable database...");
		SQLiteDatabase db = database.getWritableDatabase();
		Log.d("DB", "...writable database gotten!");

		Log.d("DB", "Querying DB's current status...");
		Cursor apparel = db.query("OwnedApparel", new String[]{dbHelper.colWG}, null, null, null, null, "_id");

		apparel.moveToFirst();

		while (!apparel.isAfterLast()) {

			int weight = apparel.getInt(0);
			VarVault.curWG.setValue(VarVault.curWG.getValue() + weight);

			apparel.moveToNext();
		}

		Log.d("DB", "Querying DB's current status...");
		Cursor weapons = db.query("OwnedWeapons", new String[]{dbHelper.colWG}, null, null, null, null, "_id");

		weapons.moveToFirst();

		while (!weapons.isAfterLast()) {

			int weight = weapons.getInt(0);
			VarVault.curWG.setValue(VarVault.curWG.getValue() + weight);

			weapons.moveToNext();
		}

		VarVault.wg.setText("WG: " + VarVault.curWG.getValue() + "/" + VarVault.maxWG.getValue());

	}

	private void updateCAPS() {
		Log.i("UPDATE", "Going into update");
		SharedPreferences prefs = getSharedPreferences("STATS", 0);
		prefs.edit().putInt("CAPS", VarVault.curCaps.getValue()).commit();

		VarVault.caps.setText("Caps: " + VarVault.curCaps.getValue());
	}



	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == 1) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				// Permission granted, initialize the map again
				onMapReady(mMap);
			} else {
				Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
			}
		}
	}




	private final SensorEventListener sensorEventListener = new SensorEventListener() {
		@Override
		public void onSensorChanged(SensorEvent event) {
			if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD || event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				// Handle sensor changes (e.g., for compass or orientation)
			}

			if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR && mMap != null) {
				float[] rotationMatrix = new float[9];
				SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
				float[] orientation = new float[3];
				SensorManager.getOrientation(rotationMatrix, orientation);
				float azimuthRad = orientation[0];
				float azimuthDeg = (float) Math.toDegrees(azimuthRad);

//				Log.d("COMPASS", "Current bearing: " + azimuthDeg);

				if (isMapRotationEnabled) {
					// MAP ROTATION MODE → rotate map bearing
					CameraPosition current = mMap.getCameraPosition();
					CameraPosition newCam = new CameraPosition.Builder(current)
							.bearing(azimuthDeg)
							.build();
//					Log.d("MAP", "Rotating map to: " + newCam.bearing);
					mMap.moveCamera(CameraUpdateFactory.newCameraPosition(newCam));
				} else {
					// COMPASS MODE → rotate the icon only
					updateCompassIcon(0);
				}
			}
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// Handle accuracy changes, for example, you could display a message if accuracy is low
			if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
				// Handle unreliable sensor status
				Log.w("Sensor", "Sensor accuracy is unreliable!");
			}
		}

	};

	private BitmapDescriptor createCustomMarker(int fillColor, int strokeColor, int radiusDp) {
		int radiusPx = (int) (radiusDp * getResources().getDisplayMetrics().density);

		Paint fillPaint = new Paint();
		fillPaint.setColor(fillColor);
		fillPaint.setStyle(Paint.Style.FILL);
		fillPaint.setAntiAlias(true);

		Paint strokePaint = new Paint();
		strokePaint.setColor(strokeColor);
		strokePaint.setStyle(Paint.Style.STROKE);
		strokePaint.setStrokeWidth(4); // stroke width in pixels
		strokePaint.setAntiAlias(true);

		Bitmap bitmap = Bitmap.createBitmap(radiusPx * 2, radiusPx * 2, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);

		float cx = radiusPx;
		float cy = radiusPx;
		float r = radiusPx - 2; // leave space for stroke

		canvas.drawCircle(cx, cy, r, fillPaint);
		canvas.drawCircle(cx, cy, r, strokePaint);

		return BitmapDescriptorFactory.fromBitmap(bitmap);
	}

	private BitmapDescriptor createCustomMarkerWithLabel(int fillColor, int strokeColor, int radiusDp, String label) {
		int radiusPx = (int) (radiusDp * getResources().getDisplayMetrics().density);

		// Paint for fill
		Paint fillPaint = new Paint();
		fillPaint.setColor(fillColor);
		fillPaint.setStyle(Paint.Style.FILL);
		fillPaint.setAntiAlias(true);

		// Paint for stroke
		Paint strokePaint = new Paint();
		strokePaint.setColor(strokeColor);
		strokePaint.setStyle(Paint.Style.STROKE);
		strokePaint.setStrokeWidth(4);
		strokePaint.setAntiAlias(true);

		// Paint for text
		Paint textPaint = new Paint();
		textPaint.setColor(Color.BLACK);  // text color
		textPaint.setTextSize(radiusPx);  // adjust size
		textPaint.setTextAlign(Paint.Align.CENTER);
		textPaint.setAntiAlias(true);
		textPaint.setFakeBoldText(true);

		// Create bitmap
		Bitmap bitmap = Bitmap.createBitmap(radiusPx * 2, radiusPx * 2, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);

		float cx = radiusPx;
		float cy = radiusPx;
		float r = radiusPx - 2;

		// Draw circle
		canvas.drawCircle(cx, cy, r, fillPaint);
		canvas.drawCircle(cx, cy, r, strokePaint);

		// Draw text centered
		Rect textBounds = new Rect();
		textPaint.getTextBounds(label, 0, label.length(), textBounds);
		float textY = cy - (textBounds.height() / 2f); // vertically center text
		canvas.drawText(label, cx, textY, textPaint);

		return BitmapDescriptorFactory.fromBitmap(bitmap);
	}




	private void updateRadioPowerUi(boolean on) {
		radioPowerButton.setAlpha(on ? 1f : 0.4f);
		nowPlayingText.setText(on ? nowPlayingText.getText() : "Radio Off");
	}

	private void startNowPlayingPolling(String apiUrl) {

		if (apiUrl == null) return;

		if (nowPlayingRunnable != null) {
			nowPlayingHandler.removeCallbacks(nowPlayingRunnable);
		}

		nowPlayingRunnable = new Runnable() {
			@Override
			public void run() {
				fetchNowPlaying(apiUrl);
				nowPlayingHandler.postDelayed(this, 10_000);
			}
		};

		nowPlayingHandler.post(nowPlayingRunnable);
	}





	private void playStream(RadioStation station) {
		if (station == null) return;

		Log.d("Playing stream", "Creating ExoPlayer");
		exoPlayer = new ExoPlayer.Builder(this).build();

		Log.d("Playing stream", "Setting now playing text");
		if (nowPlayingText != null) {
			nowPlayingText.setText("Now Playing: " + station.getNowPlayingUrl());
		}

		Log.d("Playing stream", "Stopping exoPlayer");
		exoPlayer.stop();

		Log.d("Playing stream", "Clearing media items");
		exoPlayer.clearMediaItems();

		Log.d("Playing stream", "Preparing media item");
		MediaItem mediaItem = MediaItem.fromUri(station.getStreamUrl());

		Log.d("Playing stream", "Setting media item");
		exoPlayer.setMediaItem(mediaItem);

		Log.d("Playing stream", "Preparing player");
		exoPlayer.prepare();

		Log.d("Playing stream", "Playing player");
		exoPlayer.play();
	}

	@Override
	public void onStationClicked(RadioStation station) {
		Log.d("onStationClicked", "Playing stream for station");
		playStream(station);

		Log.d("onStationClicked", "Starting now playing polling for station");
		startNowPlayingPolling(station.getNowPlayingUrl());
	}

	@Override
	public void onPowerToggled() {
		radioPowerToggle();
	}

	private void fetchNowPlaying(String apiUrl) {
		if (apiUrl == null) return;

		Executors.newSingleThreadExecutor().execute(() -> {
			try {
				URL url = new URL(apiUrl);
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("GET");
				connection.connect();

				InputStream inputStream = connection.getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
				StringBuilder json = new StringBuilder();
				String line;

				while ((line = reader.readLine()) != null) {
					json.append(line);
				}

				reader.close();
				connection.disconnect();

				JSONObject root = new JSONObject(json.toString());
				JSONObject nowPlaying = root.getJSONObject("now_playing");
				JSONObject song = nowPlaying.getJSONObject("song");
				String title = song.getString("title");

				runOnUiThread(() -> {
					if (nowPlayingText != null) {
						nowPlayingText.setText("Now Playing: " + title);
					}
				});

			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}



	private void radioPowerToggle() {
		radioOn = !radioOn; // toggle state
		updateRadioPowerUi(radioOn); // update UI
	}
	private void setupRadioView() {
		// grab panel that will host radio screen
		LinearLayout midPanel = findViewById(R.id.mid_panel);
		midPanel.removeAllViews();
		// inflate the radio layout into the panel
		View radioScreenView = LayoutInflater.from(this).inflate(R.layout.radio, midPanel, false);
		// optional, debug color is red
		radioScreenView.setBackgroundColor(Color.RED); // debug color

		//make view part of the hierarchy
		midPanel.addView(radioScreenView); // <<< MUST do this
		// add tags to the views
		radioScreenView.setTag("RADIO");
		cachedMapView.setTag("MAP");


		recyclerView = findViewById(R.id.radio_station_list); // initialize the field before setting its adapter
		nowPlayingText = radioScreenView.findViewById(R.id.now_Playing_Text);
		radioPowerButton = radioScreenView.findViewById(R.id.radio_power_button);
// null check
		if (radioPowerButton != null) {
			radioPowerButton.setOnClickListener(v -> radioPowerToggle());
			radioPowerButton.setAlpha(radioOn ? 1f : 0.4f);
		}

		TextView hostLink = radioScreenView.findViewById(R.id.radio_host_link);
		// Setup host link
		hostLink.setText("streams by https://fallout.radio, support them here! https://buymeacoffee.com/beenreported");
		hostLink.setOnClickListener(v -> {
			Intent browserIntent = new Intent(Intent.ACTION_VIEW,
					Uri.parse("https://buymeacoffee.com/beenreported"));
			startActivity(browserIntent);
		});

		// Setup RecyclerView
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		radioAdapter = new RadioStationAdapter(RadioStationList.getStations(), this::radioClicked);
		recyclerView.setAdapter(radioAdapter);

		updateRadioPowerUi(radioOn);
	}
	private void updateSelectedStation(RadioStation selectedStation) {
		if (radioAdapter != null) {
			radioAdapter.setSelectedStation(selectedStation);
		}
	}
	private void stopNowPlayingPolling() {
		if (nowPlayingRunnable != null) {
			nowPlayingHandler.removeCallbacks(nowPlayingRunnable);
			nowPlayingRunnable = null;
		}
	}




//	private void showRadio() {
//
//	}



	private void resetScreen() {
		ViewGroup midPanel = findViewById(R.id.mid_panel);
		LinearLayout topBar = findViewById(R.id.top_bar);
		LinearLayout bottomBar = findViewById(R.id.bottom_bar);

		if (midPanel != null) midPanel.removeAllViews();
		if (topBar != null) topBar.removeAllViews();
		if (bottomBar != null) bottomBar.removeAllViews();

		// HARD stop DATA-only views
		hideDataViews();
	}

	private void hideDataViews() {
		ViewGroup root = findViewById(R.id.mid_panel);
		if (root == null) return;

		View map = root.findViewWithTag("MAP");
		View radio = root.findViewWithTag("RADIO");

		if (map != null) map.setVisibility(View.GONE);
		if (radio != null) radio.setVisibility(View.GONE);
	}


	// -----------------------------
	// Dynamic stats updater
	// -----------------------------
	private void updateStatsValues() {
		if (VarVault.hp != null) VarVault.hp.setText("HP: " + VarVault.hp.getText());
		if (VarVault.ap != null) VarVault.ap.setText("AP: " + VarVault.ap.getText());
		if (VarVault.bat != null) VarVault.bat.setText("BAT: " + VarVault.bat.getText());
	}




	// TextView assignment
//	private void assignVarVaultTextReferences(TextView tv) {
//		int id = tv.getId();
//		if (id == R.id.left_button_stats) { /* optional if needed */ }
//		else if (id == R.id.left_button_items) { }
//		else if (id == R.id.left_button_data) { }
//	}
//
//	// ImageView assignment
//	private void assignVarVaultImageReferences(ImageView iv) {
//		int id = iv.getId();
//		if (id == R.id.left_stats) VarVault.stats = iv;
//		else if (id == R.id.left_items) VarVault.items = iv;
//		else if (id == R.id.left_data) VarVault.data = iv;
//		else if (id == R.id.top_left) VarVault.topLeft = iv;
//		else if (id == R.id.top_right) VarVault.topRight = iv;
//		else if (id == R.id.bottom_left) VarVault.bottomLeft = iv;
//		else if (id == R.id.bottom_right) VarVault.bottomRight = iv;
//	}

	// LinearLayout assignment
//	private void assignVarVaultLayoutReferences(LinearLayout ll) {
//		int id = ll.getId();
//		if (id == R.id.left_panel) VarVault.leftPanel = ll;
//		else if (id == R.id.statsLL) VarVault.statsLL = ll;  // your stats container
//		else if (id == R.id.statusLL) VarVault.statusLL = ll;
//		else if (id == R.id.skillsLL) VarVault.skillsLL = ll;
//		else if (id == R.id.specialLL) VarVault.specialLL = ll;
//		// Add other LinearLayouts as needed
//	}

	/**
	 * Optional helper to automatically assign VarVault references if needed.
	 * Example: map R.id.left_stats -> VarVault.stats
	 */
	// For TextView labels
	// Assigns button ImageViews to VarVault
	private void assignVarVaultImageView(ImageView iv) {
		int id = iv.getId();
		if (id == R.id.left_stats) VarVault.stats = iv;
		else if (id == R.id.left_items) VarVault.items = iv;
		else if (id == R.id.left_data) VarVault.data = iv;
		// Add other ImageView buttons as needed
	}

	// Assigns TextViews to VarVault (optional)
	private void assignVarVaultTextView(TextView tv) {
		String name = tv.getResources().getResourceEntryName(tv.getId());
		VarVault.labels.put(name, tv);
	}

	// Assigns LinearLayout containers from top/bottom bars
	private void assignVarVaultLinearLayout(LinearLayout ll) {
		String name = getResources().getResourceEntryName(ll.getId());
		VarVault.linearLayouts.put(name, ll);
	}

	// Recursively walk a view hierarchy and apply fonts, click listeners, and assignments
	private void applyFontAndClickListener(View view, Typeface font, int color) {
		if (view instanceof TextView) {
			TextView tv = (TextView) view;
			tv.setTypeface(font);
			tv.setTextColor(color);
			tv.setOnClickListener(this);
			assignVarVaultTextView(tv);

		} else if (view instanceof ImageView) {
			ImageView iv = (ImageView) view;
			iv.setOnClickListener(this);
			assignVarVaultImageView(iv);

		} else if (view instanceof LinearLayout) {
			LinearLayout ll = (LinearLayout) view;
			assignVarVaultLinearLayout(ll);
			for (int i = 0; i < ll.getChildCount(); i++) {
				applyFontAndClickListener(ll.getChildAt(i), font, color);
			}
		} else if (view instanceof ViewGroup) {
			ViewGroup vg = (ViewGroup) view;
			for (int i = 0; i < vg.getChildCount(); i++) {
				applyFontAndClickListener(vg.getChildAt(i), font, color);
			}
		}
	}

	private void assignViewsAndFonts(View view, Typeface font, int color) {
		if (view instanceof TextView) {
			TextView tv = (TextView) view;
			tv.setTypeface(font);
			tv.setTextColor(color);
			tv.setOnClickListener(this);  // All TextViews clickable
			assignVarVaultReferences(tv); // Assign VarVault reference if needed
		} else if (view instanceof ImageView) {
			ImageView iv = (ImageView) view;
			iv.setOnClickListener(this);
			assignVarVaultReferences(iv); // Assign VarVault reference if needed
		} else if (view instanceof LinearLayout) {
			LinearLayout ll = (LinearLayout) view;
			assignVarVaultReferences(ll); // Assign VarVault reference if needed
		}

		if (view instanceof ViewGroup) {
			ViewGroup vg = (ViewGroup) view;
			for (int i = 0; i < vg.getChildCount(); i++) {
				assignViewsAndFonts(vg.getChildAt(i), font, color);
			}
		}
	}
	private void assignVarVaultReferences(View v) {
		if (v instanceof TextView) assignVarVaultTextView((TextView)v);
		else if (v instanceof ImageView) assignVarVaultImageView((ImageView)v);
		else if (v instanceof LinearLayout) assignVarVaultLinearLayout((LinearLayout)v);
	}

	// Example helper for ImageView
	private void assignVarVaultReferences(ImageView iv) {
		int id = iv.getId();
		if (id == R.id.left_stats) VarVault.stats = iv;
		else if (id == R.id.left_items) VarVault.items = iv;
		else if (id == R.id.left_data) VarVault.data = iv;
		// Add other ImageView references here
	}

	// Example helper for LinearLayout
	private void assignVarVaultReferences(LinearLayout ll) {
		int id = ll.getId();
//		if (id == R.id.statsLL) VarVault.statsLL = ll;
//		else if (id == R.id.statusLL) VarVault.statusLL = ll;
//		else if (id == R.id.specialLL) VarVault.specialLL = ll;
//		else if (id == R.id.skillsLL) VarVault.skillsLL = ll;
//		else if (id == R.id.localMapLL) VarVault.localMapLL = ll;
//		else if (id == R.id.worldMapLL) VarVault.worldMapLL = ll;
//		else if (id == R.id.questsLL) VarVault.questsLL = ll;
//		else if (id == R.id.notesLL) VarVault.notesLL = ll;
//		else if (id == R.id.radioLL) VarVault.radioLL = ll;
	}
	private void fetchViews(View root) {
		int id = root.getId();

		if (id != View.NO_ID) {  // Only process views with a valid ID
			String name = getResources().getResourceEntryName(id);

			if (root instanceof LinearLayout) {
				VarVault.linearLayouts.put(name, (LinearLayout) root);
			} else if (root instanceof ImageView) {
				VarVault.imageViews.put(name, (ImageView) root);
			} else if (root instanceof TextView) {
				VarVault.textViews.put(name, (TextView) root);
			}
		}

		if (root instanceof ViewGroup) {
			ViewGroup vg = (ViewGroup) root;
			for (int i = 0; i < vg.getChildCount(); i++) {
				fetchViews(vg.getChildAt(i));
			}
		}
	}

	private void playClickSound(View source) {
		// Check if the source is one of the main buttons
		if (VarVault.MAIN_BUTTONS.contains(source)) {
			HandleSound.playSound(HandleSound.aud_newTab); // Main tab sound
		} else if (source == VarVault.stimpak) {
			HandleSound.playSound(HandleSound.aud_stimpak); // Stimpak sound
		} else {
			HandleSound.playSound(HandleSound.aud_selection); // Default selection sound
		}
	}

	//TO-DO refactor

	// Helper method to bind a button, set its click listener, and apply the font
	private void bindButton(LinearLayout parent, int buttonId, int buttonBoxId, Runnable onClickAction) {
		TextView button = parent.findViewById(buttonId);
		LinearLayout buttonBox = parent.findViewById(buttonBoxId);

		if (buttonBox != null) {
			buttonBox.setOnClickListener(v -> {
				onClickAction.run(); // Execute the provided action
				playClickSound(v); // Optionally play a sound on click
			});
		}

		if (button != null) {
			button.setTypeface(VarVault.font); // Apply the font
		}
	}


	private void initGameData() {
		initSkills();
		initSpecial();
		InitializeArrays.all();
		initCaps();
	}
//	private void initStats() {
//		VarVault.curWG = new Stat();
//		VarVault.maxWG = new Stat();
//		VarVault.curCaps = new Stat();
//
//		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
//		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//	}
	private void initAudio() {
		HandleSound.initSound(getApplicationContext());
	}
	private void initMediaPlayer() {
		exoPlayer = new ExoPlayer.Builder(this).build();
		currentStation = RadioStationList.getStations().get(0);
		exoPlayer.setMediaItem(MediaItem.fromUri(currentStation.getStreamUrl()));
		exoPlayer.prepare();
		// exoPlayer.play(); // optional
	}
	private void setupWindow() {
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

		WindowInsetsControllerCompat controller =
				new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

		controller.hide(WindowInsetsCompat.Type.statusBars());
		controller.setSystemBarsBehavior(
				WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		);

		// Battery receiver
		VarVault.mBatInfoReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (VarVault.bat != null) {
					int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
					VarVault.bat.setText("BAT: " + level + "%");
				}
			}
		};
	}

	//TO-DO: refactor
//	private void initViews() {
//		// Panels
//		leftPanel = findViewById(R.id.left_panel);  // container for buttons
//		midPanel = findViewById(R.id.mid_panel);
//		leftPanel = findViewById(R.id.left_panel);  // container for buttons
//		// Buttons
//		VarVault.stats = findViewById(R.id.left_stats);
//		VarVault.items = findViewById(R.id.left_items);
//		VarVault.data = findViewById(R.id.left_data);
//
//		// Other UI components (add here if needed)
//		initButtonActions();
//	}
//	private void initButtonsAndFonts() {
//		// Load your custom font and color
//		Typeface font = Typeface.createFromAsset(getAssets(), "Monofonto.ttf");
//		int color = Color.argb(100, 255, 225, 0);
//
//		// Start from root view
//		View root = findViewById(android.R.id.content);
//
//		// Recursively apply fonts/colors and assign VarVault references
//		assignViewsAndFonts(root, font, color);
//
//		// Initialize the button actions map
//		initButtonActions();
//	}
//	private void initButtonActions() {
//		// Clear previous mappings just in case
//		buttonActions.clear();
//
//		// ===== Main buttons =====
//		assignActionIfNotNull(VarVault.stats, this::statsClicked);
//		assignActionIfNotNull(VarVault.statusLL, this::statusClicked);
//		assignActionIfNotNull(VarVault.specialLL, this::specialClicked);
//		assignActionIfNotNull(VarVault.special, this::specialClicked);
//		assignActionIfNotNull(VarVault.skillsLL, this::skillsClicked);
//		assignActionIfNotNull(VarVault.skills, this::skillsClicked);
//
//		// ===== Submenu items =====
//		for (View v : VarVault.SUBMENU_SPECIAL) {
//			if (v != null) buttonActions.put(v, () -> specialStatClicked(v));
//		}
//		for (View v : VarVault.SUBMENU_SKILLS) {
//			if (v != null) buttonActions.put(v, () -> skillStatClicked(v));
//		}
//
//		// ===== Data buttons =====
//		assignActionIfNotNull(VarVault.data, this::dataClicked);
//		assignActionIfNotNull(VarVault.localMapLL, () -> { Log.d("Menu", "Local Map clicked");
//			if (mapFragment != null) showLocalMap(); });
//		assignActionIfNotNull(VarVault.worldMapLL, () -> { Log.d("Menu", "World Map clicked");
//			if (mapFragment != null) worldMapClicked(); });
//		assignActionIfNotNull(VarVault.questsLL, () -> Log.d("Menu", "Quests clicked"));
//		assignActionIfNotNull(VarVault.notesLL, () -> Log.d("Menu", "Notes clicked"));
//		assignActionIfNotNull(VarVault.radioLL, () -> Log.d("Menu", "Radio clicked"));
//	}
	public void onReceive(Context context, Intent intent) {
		if (VarVault.bat != null) {
			int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
			int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
			int batteryPct = (int) ((level / (float) scale) * 100);
			VarVault.bat.setText(batteryPct + "%");
		}
	}

	private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (VarVault.bat != null) {
				int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
				int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
				int batteryPct = (int) ((level / (float) scale) * 100);
				VarVault.bat.setText(batteryPct + "%");
			}
		}
	};

	//Radio helpers

	private void initFonts() {
		if (VarVault.font == null) {
			VarVault.font = Typeface.createFromAsset(getAssets(), "Monofonto.ttf");
		}

		int textColor = Color.argb(100, 255, 225, 0);
		View rootView = findViewById(android.R.id.content);
		applyFontAndClickListener(rootView, VarVault.font, textColor);
	}

	/**
	 * Recursively traverses a ViewGroup and applies font and color to all TextViews.
	 */
//	private void applyFontToTextViews(View view, Typeface font, int color) {
//		if (view instanceof TextView) {
//			((TextView) view).setTypeface(font);
//			((TextView) view).setTextColor(color);
//		} else if (view instanceof ViewGroup) {
//			ViewGroup vg = (ViewGroup) view;
//			for (int i = 0; i < vg.getChildCount(); i++) {
//				applyFontToTextViews(vg.getChildAt(i), font, color);
//			}
//		}
//	}

	// Helper to avoid repeated null checks

//	public void initAnimations() {
//		Animation turnOnAnimation = new Animation() {
//			@Override
//			public void applyTransformation(float interpolatedTime, Transformation t) {
//				// Calculate the scaling and rotation values based on the interpolation time
//				float scaleX = 1 + (0.5f - interpolatedTime);
//				float scaleY = scaleX;
//				float rotation = 360 * interpolatedTime;
//
//				// Apply the transformations to the CRT screen
//				crtImage.setScaleX(scaleX);
//				crtImage.setScaleY(scaleY);
//				crtImage.setRotation(rotation);
//			}
//		};
//
//
//		Animation turnOffAnimation = new Animation() {
//			@Override
//			public void applyTransformation(float interpolatedTime, Transformation t) {
//				// Calculate the scaling and rotation values based on the interpolation time
//				float scaleX = 1 + (0.5f - interpolatedTime);
//				float scaleY = scaleX;
//				float rotation = 360 * interpolatedTime;
//
//				// Apply the transformations to the CRT screen
//				crtImage.setScaleX(scaleX);
//				crtImage.setScaleY(scaleY);
//				crtImage.setRotation(rotation);
//			}
//		};
//
//	}
	 public void initMainMenu() {
		 VarVault.stats = (ImageView) findViewById(R.id.left_stats);
		 TextView x = (TextView) findViewById(R.id.left_button_stats);
		 if (x != null) {
			 x.setTypeface(VarVault.font);
			 x.setTextColor(Color.argb(100, 255, 225, 0));
		 }
		 VarVault.stats.setOnClickListener(this);

		 VarVault.items = (ImageView) findViewById(R.id.left_items);
		 TextView y = (TextView) findViewById(R.id.left_button_items);
		 if (y != null) {
			 y.setTypeface(VarVault.font);
			 y.setTextColor(Color.argb(100, 255, 225, 0));
		 }
		 VarVault.items.setOnClickListener(this);

		 VarVault.data = (ImageView) findViewById(R.id.left_data);
		 TextView z = (TextView) findViewById(R.id.left_button_data);
		 if (z != null) {
			 z.setTypeface(VarVault.font);
			 z.setTextColor(Color.argb(100, 255, 225, 0));
		 }
	 }
	 public void initSensors() {
		 sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

		 accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		 VarVault.playerMovement = accelerometer;
		 magnetometer   = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		 VarVault.playerOrientation = magnetometer;
		 Sensor rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		 sensorManager.registerListener(sensorEventListener, rotationVector, SensorManager.SENSOR_DELAY_UI);
	 }
}
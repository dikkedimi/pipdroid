package com.skettidev.pipdroid;

import android.os.Handler;
import android.os.Looper;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.*;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.*;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.LayoutInflater;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.*;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;


public class MainMenu extends AppCompatActivity implements OnMapReadyCallback, View.OnClickListener, View.OnLongClickListener {


	//public vars
	public static GoogleMap mMap;

	//private vars
	private boolean isCompassModeEnabled = false;
	private MapViewType currentMapView;

	private SupportMapFragment mapFragment;
	private SensorManager sensorManager;
	private Sensor rotationSensor;

	private enum MapViewType { WORLD, LOCAL }

	private float[] rotationMatrix = new float[9];
	private float[] orientation = new float[3];

	private float zoomLevel = VarVault.WORLD_MAP_ZOOM; // default to WORLD MAP

	private final ActivityResultLauncher<String> requestPermissionLauncher =
			registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
				if (isGranted) {
					enableLocation(zoomLevel);  // Enable location if permission is granted
				} else {
					Toast.makeText(MainMenu.this, "Permission denied", Toast.LENGTH_SHORT).show();
				}
			});

	// ########################
	// ## On app start ########
	// ########################

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

		if (rotationSensor != null) {
			sensorManager.registerListener(
					sensorEventListener,
					rotationSensor,
					SensorManager.SENSOR_DELAY_UI
			);
		} else {
			Log.e("Sensors", "Rotation vector sensor not available on this device");
		}
		setContentView(R.layout.main);

		// Initialize all buttons once in onCreate()
		initializeMenuButtons();

		// ===== Initialize sound =====
		HandleSound.initSound(this.getApplicationContext());

		// ===== Initialize stats / fonts =====
		VarVault.font = Typeface.createFromAsset(getAssets(), "Monofonto.ttf");
		VarVault.curWG = new Stat();
		VarVault.maxWG = new Stat();
		VarVault.curCaps = new Stat();

		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// ===== Initialize buttons =====
		VarVault.stats = findViewById(R.id.left_stats);
		TextView x = findViewById(R.id.left_button_stats);
		x.setTypeface(VarVault.font);
		x.setTextColor(Color.argb(100, 255, 225, 0));
		VarVault.stats.setOnClickListener(this);

		VarVault.items = findViewById(R.id.left_items);
		TextView y = findViewById(R.id.left_button_items);
		y.setTypeface(VarVault.font);
		y.setTextColor(Color.argb(100, 255, 225, 0));
		VarVault.items.setOnClickListener(this);

		VarVault.data = findViewById(R.id.left_data);
		TextView z = findViewById(R.id.left_button_data);
		z.setTypeface(VarVault.font);
		z.setTextColor(Color.argb(100, 255, 225, 0));
		VarVault.data.setOnClickListener(this);

		// ===== Initialize main menu functions =====
		initSkills();
		initSpecial();
		InitializeArrays.all();
		onClick(VarVault.stats);
		initCaps();

		// ===== Optional: initialize map immediately =====
		// If you want the map ready at startup, you can call:
//		 dataClicked();
	}

	@Override
	public void onClick(View source) {
		Log.e("onClick()", "start");
		VarVault.curCaps.setValue(VarVault.curCaps.getValue() + 5);

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
			dataClicked(source);
			Log.e("onClick()", "dataClicked(source)");
		}

		Log.e("onClick()", "sort source DONE!");

	}


	private void flashlightClicked() {
		Log.e("flashlightClicked()", "flashlightClicked START!");
		if (VarVault.mCamera == null) {
			Log.e("flashlightClicked()", "flashlightClicked VarVault.mCamerais NULL!");
			VarVault.preview = (SurfaceView) findViewById(R.id.PREVIEW);
			VarVault.mHolder = VarVault.preview.getHolder();
			VarVault.mCamera = Camera.open();
			try {
				VarVault.mCamera.setPreviewDisplay(VarVault.mHolder);
				Log.e("flashlightClicked()", "Try set preview display!");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
		}

		// If it's off, turn it on
		if (VarVault.isCamOn == false) {
			Log.e("flashlightClicked()", "If it's off, turn it on");
			Parameters params = VarVault.mCamera.getParameters();
			params.setFlashMode(Parameters.FLASH_MODE_TORCH);
			VarVault.mCamera.setParameters(params);
			VarVault.mCamera.startPreview();
			VarVault.isCamOn = true;
		}

		// If it's on, turn it off
		else {
			Log.e("flashlightClicked()", "If it's on, turn it off");
			Parameters params = VarVault.mCamera.getParameters();
			params.setFlashMode(Parameters.FLASH_MODE_OFF);
			VarVault.mCamera.setParameters(params);
			VarVault.mCamera.stopPreview();
			VarVault.mCamera.release();
			VarVault.mCamera = null;
			VarVault.isCamOn = false;
		}
	}

	private void statsClicked() {
		Log.e("statsClicked()", "statsClicked START");
		// Clear crap

		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		ViewGroup topBar = (ViewGroup) findViewById(R.id.top_bar);
		ViewGroup bottomBar = (ViewGroup) findViewById(R.id.bottom_bar);

		// Check if midPanel is null
		if (midPanel != null) {
			Log.e("StatsClicked", "midPanel is null! Cannot remove views.");
		} else {
			midPanel.removeAllViews();  // Proceed with removing all views safely
		}

		// Check if topBar is null
		if (topBar != null) {
			topBar.removeAllViews();  // Proceed with removing all views safely
		} else {
			Log.e("StatsClicked", "top_bar is null! Cannot remove views.");
		}

		// Check if bottomBar is null
		if (bottomBar != null) {
			bottomBar.removeAllViews();  // Proceed with removing all views safely
		} else {
			Log.e("StatsClicked", "bottom_bar is null! Cannot remove views.");
		}

		LayoutInflater inf = this.getLayoutInflater();

		// Inflate new layouts
		if (midPanel != null) {
			inf.inflate(R.layout.status_screen, midPanel);
		} else {
			Log.e("StatsClicked", "midPanel is still null after check! Cannot inflate status_screen.");
		}

		if (topBar != null) {
			inf.inflate(R.layout.stats_bar_top, topBar);
		} else {
			Log.e("StatsClicked", "topBar is still null after check! Cannot inflate stats_bar_top.");
		}

		if (bottomBar != null) {
			inf.inflate(R.layout.stats_bar_bottom, bottomBar);
		} else {
			Log.e("StatsClicked", "bottomBar is still null after check! Cannot inflate stats_bar_bottom.");
		}

		// Format top bar text
		VarVault.title = (TextView) findViewById(R.id.title_stats);
		if (VarVault.title != null) {
			VarVault.title.setText("STATUS");
			VarVault.title.setTypeface(VarVault.font);
		} else {
			Log.e("StatsClicked", "title_stats is null! Cannot set title text.");
		}

		VarVault.hp = (TextView) findViewById(R.id.hp_stats);
		if (VarVault.hp != null) {
			VarVault.hp.setTypeface(VarVault.font);
		}

		VarVault.ap = (TextView) findViewById(R.id.ap_stats);
		if (VarVault.ap != null) {
			VarVault.ap.setTypeface(VarVault.font);
		}

		VarVault.bat = (TextView) findViewById(R.id.bat_stats);
		if (VarVault.bat != null) {
			VarVault.bat.setTypeface(VarVault.font);
			this.registerReceiver(VarVault.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		}

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

	private void itemsClicked() {
		// check to see if views are NULL, if not, clear them.

		ViewGroup topBar = (ViewGroup) findViewById(R.id.top_bar);
		if (topBar != null) {
			topBar.removeAllViews();  // Proceed with removing all views safely
		} else {
			Log.e("itemsClicked", "top_bar is null! Cannot remove views.");
		}

		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		if (midPanel != null) {
			midPanel.removeAllViews();  // Proceed with removing all views safely
		} else {
			Log.e("itemsClicked", "mid_panel is null! Cannot remove views.");
		}

		ViewGroup bottomBar = (ViewGroup) findViewById(R.id.bottom_bar);
		if (bottomBar != null) {
			bottomBar.removeAllViews();  // Proceed with removing all views safely
		} else {
			Log.e("itemsClicked", "bottom_bar is null! Cannot remove views.");
		}

		// inflate layout
		LayoutInflater inf = this.getLayoutInflater();


		// Main screen turn on
		if (midPanel != null) {
			inf.inflate(R.layout.weapons_screen, midPanel);
		}

		if (topBar != null) {
			inf.inflate(R.layout.items_bar_top, topBar);
		}

		if (bottomBar != null) {
			inf.inflate(R.layout.items_bar_bottom, bottomBar);
		}

		VarVault.title = (TextView) findViewById(R.id.title_items);
		if (VarVault.title != null) {
			VarVault.title.setTypeface(VarVault.font);
		}
		VarVault.title.setTypeface(VarVault.font);

		VarVault.wg = (TextView) findViewById(R.id.wg_items);
		if (VarVault.wg != null) {
			VarVault.maxWG.setValue(150 + (10 * VarVault.strength.getValue()));
			updateWG();
			VarVault.wg.setTypeface(VarVault.font);
		}

		VarVault.caps = (TextView) findViewById(R.id.caps_items);
		if (VarVault.caps != null) {
			updateCAPS();
			VarVault.caps.setTypeface(VarVault.font);
		}

		VarVault.bat = (TextView) findViewById(R.id.bat_items);
		if (VarVault.bat != null) {
			VarVault.bat.setTypeface(VarVault.font);
			this.registerReceiver(VarVault.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		}

		// Button-ize the buttons
		VarVault.weapons = (TextView) findViewById(R.id.btn_weapons);
		if (VarVault.weapons != null) {
			VarVault.weapons.setTypeface(VarVault.font);
			VarVault.weaponsLL = (LinearLayout) findViewById(R.id.btn_weapons_box);
			VarVault.weaponsLL.setOnClickListener(this);
		}

		VarVault.apparel = (TextView) findViewById(R.id.btn_apparel);
		if (VarVault.apparel != null) {
			VarVault.apparel.setTypeface(VarVault.font);
			VarVault.apparelLL = (LinearLayout) findViewById(R.id.btn_apparel_box);
			VarVault.apparelLL.setOnClickListener(this);
		}

		VarVault.aid = (TextView) findViewById(R.id.btn_aid);
		if (VarVault.aid != null) {
			VarVault.aid.setTypeface(VarVault.font);
			VarVault.aidLL = (LinearLayout) findViewById(R.id.btn_aid_box);
			VarVault.aidLL.setOnClickListener(this);
		}

		VarVault.misc = (TextView) findViewById(R.id.btn_misc);
		if (VarVault.misc != null) {
			VarVault.misc.setTypeface(VarVault.font);
			VarVault.miscLL = (LinearLayout) findViewById(R.id.btn_misc_box);
			VarVault.miscLL.setOnClickListener(this);
		}

		VarVault.ammo = (TextView) findViewById(R.id.btn_ammo);
		if (VarVault.ammo != null) {
			VarVault.ammo.setTypeface(VarVault.font);
			VarVault.ammoLL = (LinearLayout) findViewById(R.id.btn_ammo_box);
			VarVault.ammoLL.setOnClickListener(this);
		}

		populateOwnedWeapons();

	}

	private void weaponsClicked() {

		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		LayoutInflater inf = this.getLayoutInflater();

		midPanel.removeAllViews();
		inf.inflate(R.layout.weapons_screen, midPanel);

		populateOwnedWeapons();

		updateCAPS();
	}

	private void apparelClicked() {

		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		LayoutInflater inf = this.getLayoutInflater();

		midPanel.removeAllViews();
		inf.inflate(R.layout.apparel_screen, midPanel);

		populateOwnedApparel();

		updateCAPS();
	}

	private void dataClicked(View source) {

		View mapScreen = null;

		// check topBar
		ViewGroup topBar = findViewById(R.id.top_bar);
		if (topBar != null) {
			topBar.removeAllViews();  // Proceed with removing all views safely
		} else {
			Log.e("dataClicked", "top_bar is null! Cannot remove views.");
		}
		//check mid_panel
		ViewGroup midPanel = findViewById(R.id.mid_panel);
		if (midPanel != null) {
			midPanel.removeAllViews();  // Clear the midPanel to remove any current views
			Log.e("dataClicked", "Cleared midPanel.");



		}
		//viewGroup midpanel additional setup?
		if (midPanel != null) {
			ViewGroup.LayoutParams params = midPanel.getLayoutParams();
			params.width = ViewGroup.LayoutParams.MATCH_PARENT;
			midPanel.setLayoutParams(params);
		}

		//check bottom bar
		ViewGroup bottomBar = findViewById(R.id.bottom_panel);
		// Dynamically inflate the bottom bar based on some condition
		if (bottomBar != null) {
			bottomBar.removeAllViews();

			View bottomBarView = LayoutInflater.from(this)
					.inflate(R.layout.data_bar_bottom, bottomBar, true);
					initializeMenuButtons();
//			View bottom_bar = bottomBarView.findViewById(R.id.bottom_bar);
		} else {
			Log.e("dataClicked", "bottom_panel is null! Cannot remove views.");
		}

		LayoutInflater inflater = getLayoutInflater();
		mapScreen = inflater.inflate(R.layout.map_screen, midPanel, false); // Add map screen to midPanel

		if (mapScreen != null) {
			midPanel.addView(mapScreen);
			Log.e("dataClicked", "Inflated map screen to the midPanel.");
			// 🔑 INITIALIZE COMPASS HERE
			initCompassToggle(mapScreen);
		} else {
			Log.e("dataClicked()", "mapScreen inflation FAILED.");
			return;
		}

		View mapFragmentContainer = findViewById(R.id.map_fragment_container);
		if (mapFragmentContainer != null && mapFragmentContainer.getVisibility() == View.VISIBLE) {
			Log.e("dataClicked", "mapContainer left=" + mapFragmentContainer.getLeft() + " right=" + mapFragmentContainer.getRight());
			// Try to find an existing fragment by its tag
			SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentByTag("MAP_FRAGMENT_TAG");
			if (mapFragment == null) {
				mapFragment = SupportMapFragment.newInstance();
				FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
				Log.e("dataClicked", "Map fragment is not found, create and add it.");
				transaction.replace(R.id.map_fragment_container, mapFragment, "MAP_FRAGMENT_TAG");
				transaction.commit();
			} else {
				Log.e("dataClicked", "Map fragment already exists.");
			}
			mapFragment.getMapAsync(this);
		}
		//now for the logic stuff
//		inflate mapScreen
		// data button menu handling


	}
	// Sets the active map view and moves the camera
//	private void setActiveMapView(MapViewType currentMapView) {
////		currentMapView = mapViewType;
//
//		if (mMap != null && VarVault.playerLocation != null) {
//			switch (currentMapView) {
//				case LOCAL:
//					setInitialCameraPosition(VarVault.LOCAL_MAP_ZOOM);
//					break;
//				case WORLD:
//					setInitialCameraPosition(VarVault.WORLD_MAP_ZOOM);
//					break;
//			}
//		}
		// Update button UI (alpha) to reflect active map
//		updateMapButtonsUI();
//	}
	// Updates the alpha of map buttons only
	private void updateMapButtonsUI() {
		VarVault.localMapLL.setAlpha(currentMapView == MapViewType.LOCAL ? 1f : 0.4f);
		VarVault.worldMapLL.setAlpha(currentMapView == MapViewType.WORLD ? 1f : 0.4f);
		// Other buttons
//		VarVault.questsLL.oncli
//			Log.d("Menu", "Quests clicked");
//		} else if (source == VarVault.notesLL) {
//			Log.d("Menu", "Notes clicked");
//		} else if (source == VarVault.radioLL) {
//			Log.d("Menu", "Radio clicked");
//		}
	}

	private void statusClicked() {
//		setContentView(R.layout.main);
		ViewGroup midPanel = findViewById(R.id.mid_panel);
		ViewGroup bottomBar = findViewById(R.id.bottom_bar);


		if (midPanel == null || bottomBar == null) {
			Log.e("MainMenu", "One or more views are null!");
			return; // Prevent further actions on null views
		}

		LayoutInflater inf = this.getLayoutInflater();

		midPanel.removeAllViews();

		inf.inflate(R.layout.status_screen, midPanel);

		VarVault.title.setText("STATUS");

		VarVault.cnd = (TextView) findViewById(R.id.btn_cnd);
		VarVault.cnd.setTypeface(VarVault.font);
		VarVault.cnd.setOnClickListener(this);

		VarVault.rad = (TextView) findViewById(R.id.btn_rad);
		VarVault.rad.setTypeface(VarVault.font);
		VarVault.rad.setOnClickListener(this);

		VarVault.stimpak = (TextView) findViewById(R.id.btn_stimpak);
		VarVault.stimpak.setTypeface(VarVault.font);
		VarVault.stimpak.setOnClickListener(this);

		VarVault.flashlight = (TextView) findViewById(R.id.btn_flashlight);
		VarVault.flashlight.setTypeface(VarVault.font);
		VarVault.flashlight.setOnClickListener(this);
	}

	private void specialClicked() {

		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		LayoutInflater inf = this.getLayoutInflater();

		midPanel.removeAllViews();
		inf.inflate(R.layout.special_screen, midPanel);

		VarVault.title.setText("SPECIAL");

		VarVault.specialImage = (ImageView) findViewById(R.id.special_image);

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

		InitializeArrays.submenu_special();
	}

	private void skillsClicked() {
		int allocatedpoints = (VarVault.barter.getValue() + VarVault.big_guns.getValue()
				+ VarVault.energy.getValue() + VarVault.explosives.getValue()
				+ VarVault.lockpick.getValue() + VarVault.medicine.getValue() + VarVault.melee.getValue()
				+ VarVault.repair.getValue() + VarVault.science.getValue()
				+ VarVault.small_guns.getValue() + VarVault.sneak.getValue() + VarVault.speech.getValue() + VarVault.unarmed
				.getValue());
		if ((VarVault.numContacts + 130) > allocatedpoints) {
			// Allocate unused points
			Intent i = new Intent(MainMenu.this, SetSkills.class);
			MainMenu.this.startActivityForResult(i, 1);
		} else {
			// Show skills screen
			ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
			LayoutInflater inf = this.getLayoutInflater();

			midPanel.removeAllViews();
			inf.inflate(R.layout.skills_screen, midPanel);

			VarVault.skillImage = (ImageView) findViewById(R.id.skills_image);

			VarVault.title.setText("SKILLS");

			VarVault.bart = (TextView) findViewById(R.id.text_barter);
			VarVault.barterSTAT = (TextView) findViewById(R.id.barter_stat);
			VarVault.bart.setTypeface(VarVault.font);
			VarVault.barterSTAT.setTypeface(VarVault.font);
			VarVault.barterSTAT.setText(String.valueOf(VarVault.barter.getValue()));
			VarVault.bart.setOnClickListener(this);

			VarVault.bgns = (TextView) findViewById(R.id.text_big_guns);
			VarVault.big_gunsSTAT = (TextView) findViewById(R.id.big_guns_stat);
			VarVault.bgns.setTypeface(VarVault.font);
			VarVault.big_gunsSTAT.setTypeface(VarVault.font);
			VarVault.big_gunsSTAT.setText(String.valueOf(VarVault.big_guns.getValue()));
			VarVault.bgns.setOnClickListener(this);

			VarVault.nrg = (TextView) findViewById(R.id.text_energy);
			VarVault.energySTAT = (TextView) findViewById(R.id.energy_stat);
			VarVault.nrg.setTypeface(VarVault.font);
			VarVault.energySTAT.setTypeface(VarVault.font);
			VarVault.energySTAT.setText(String.valueOf(VarVault.energy.getValue()));
			VarVault.nrg.setOnClickListener(this);

			VarVault.expl = (TextView) findViewById(R.id.text_explosives);
			VarVault.explosivesSTAT = (TextView) findViewById(R.id.explosives_stat);
			VarVault.expl.setTypeface(VarVault.font);
			VarVault.explosivesSTAT.setTypeface(VarVault.font);
			VarVault.explosivesSTAT.setText(String.valueOf(VarVault.explosives.getValue()));
			VarVault.expl.setOnClickListener(this);

			VarVault.lock = (TextView) findViewById(R.id.text_lockpick);
			VarVault.lockpickSTAT = (TextView) findViewById(R.id.lockpick_stat);
			VarVault.lock.setTypeface(VarVault.font);
			VarVault.lockpickSTAT.setTypeface(VarVault.font);
			VarVault.lockpickSTAT.setText(String.valueOf(VarVault.lockpick.getValue()));
			VarVault.lock.setOnClickListener(this);

			VarVault.medi = (TextView) findViewById(R.id.text_medicine);
			VarVault.medicineSTAT = (TextView) findViewById(R.id.medicine_stat);
			VarVault.medi.setTypeface(VarVault.font);
			VarVault.medicineSTAT.setTypeface(VarVault.font);
			VarVault.medicineSTAT.setText(String.valueOf(VarVault.medicine.getValue()));
			VarVault.medi.setOnClickListener(this);

			VarVault.mlee = (TextView) findViewById(R.id.text_melee);
			VarVault.meleeSTAT = (TextView) findViewById(R.id.melee_stat);
			VarVault.mlee.setTypeface(VarVault.font);
			VarVault.meleeSTAT.setTypeface(VarVault.font);
			VarVault.meleeSTAT.setText(String.valueOf(VarVault.melee.getValue()));
			VarVault.mlee.setOnClickListener(this);

			VarVault.rpar = (TextView) findViewById(R.id.text_repair);
			VarVault.repairSTAT = (TextView) findViewById(R.id.repair_stat);
			VarVault.rpar.setTypeface(VarVault.font);
			VarVault.repairSTAT.setTypeface(VarVault.font);
			VarVault.repairSTAT.setText(String.valueOf(VarVault.repair.getValue()));
			VarVault.rpar.setOnClickListener(this);

			VarVault.sci = (TextView) findViewById(R.id.text_science);
			VarVault.scienceSTAT = (TextView) findViewById(R.id.science_stat);
			VarVault.sci.setTypeface(VarVault.font);
			VarVault.scienceSTAT.setTypeface(VarVault.font);
			VarVault.scienceSTAT.setText(String.valueOf(VarVault.science.getValue()));
			VarVault.sci.setOnClickListener(this);

			VarVault.sgns = (TextView) findViewById(R.id.text_small_guns);
			VarVault.small_gunsSTAT = (TextView) findViewById(R.id.small_guns_stat);
			VarVault.sgns.setTypeface(VarVault.font);
			VarVault.small_gunsSTAT.setTypeface(VarVault.font);
			VarVault.small_gunsSTAT.setText(String.valueOf(VarVault.small_guns.getValue()));
			VarVault.sgns.setOnClickListener(this);

			VarVault.snek = (TextView) findViewById(R.id.text_sneak);
			VarVault.sneakSTAT = (TextView) findViewById(R.id.sneak_stat);
			VarVault.snek.setTypeface(VarVault.font);
			VarVault.sneakSTAT.setTypeface(VarVault.font);
			VarVault.sneakSTAT.setText(String.valueOf(VarVault.sneak.getValue()));
			VarVault.snek.setOnClickListener(this);

			VarVault.spch = (TextView) findViewById(R.id.text_speech);
			VarVault.speechSTAT = (TextView) findViewById(R.id.speech_stat);
			VarVault.spch.setTypeface(VarVault.font);
			VarVault.speechSTAT.setTypeface(VarVault.font);
			VarVault.speechSTAT.setText(String.valueOf(VarVault.speech.getValue()));
			VarVault.spch.setOnClickListener(this);

			VarVault.uarm = (TextView) findViewById(R.id.text_unarmed);
			VarVault.unarmedSTAT = (TextView) findViewById(R.id.unarmed_stat);
			VarVault.uarm.setTypeface(VarVault.font);
			VarVault.unarmedSTAT.setTypeface(VarVault.font);
			VarVault.unarmedSTAT.setText(String.valueOf(VarVault.unarmed.getValue()));
			VarVault.uarm.setOnClickListener(this);

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
			VarVault.perception.setValue(prefs.getInt("PERCEPTION", -2));
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

	protected void onPause() {
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		if (VarVault.isCamOn == true) {
			Parameters params = VarVault.mCamera.getParameters();
			params.setFlashMode(Parameters.FLASH_MODE_OFF);
			VarVault.mCamera.setParameters(params);
			VarVault.mCamera.stopPreview();
			VarVault.mCamera.release();
			VarVault.mCamera = null;
			VarVault.isCamOn = false;
		}
		super.onPause();
		sensorManager.unregisterListener(sensorEventListener);
	}

	// onResume()
	protected void onResume() {
		super.onResume();
		sensorManager.registerListener(sensorEventListener, rotationSensor, SensorManager.SENSOR_DELAY_UI);
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		// Check if the map fragment is already added


		// Clear crap

		ViewGroup topBar = (ViewGroup) findViewById(R.id.top_bar);
		// Check if topBar is null
		if (topBar != null) {
			topBar.removeAllViews();  // Proceed with removing all views safely
		} else {
			Log.e("onResume", "top_bar is null! Cannot remove views.");
		}

		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		// Check if midPanel is null
		if (midPanel != null) {
			midPanel.removeAllViews();  // Proceed with removing all views safely
		} else {
			Log.e("onResume", "midPanel is null! Cannot remove views.");
		}

		ViewGroup bottomBar = (ViewGroup) findViewById(R.id.bottom_bar);
		// Check if bottomBar is null
		if (bottomBar != null) {
			bottomBar.removeAllViews();  // Proceed with removing all views safely
		} else {
			Log.e("onResume", "bottom_bar is null! Cannot remove views.");
		}

		LayoutInflater inf = this.getLayoutInflater();

		// Main screen turn on

		if (topBar != null) {
			inf.inflate(R.layout.stats_bar_top, topBar, true);
		}
		if (midPanel != null) {
			inf.inflate(R.layout.status_screen, midPanel, true);
		}
		if (bottomBar != null) {
			inf.inflate(R.layout.stats_bar_bottom, bottomBar, true);
		}
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

		if (VarVault.status != null && VarVault.statusLL != null) {
			VarVault.status.setTypeface(VarVault.font);
			VarVault.status.setOnClickListener(this);
			VarVault.statusLL.setOnClickListener(this);
			VarVault.status.setTypeface(VarVault.font);
		} else {
			Log.e("onResume", "status or statusLL is null! Cannot set OnClickListener.");
		}

		VarVault.special = (TextView) findViewById(R.id.btn_special);
		VarVault.specialLL = (LinearLayout) findViewById(R.id.btn_special_box);
		if (VarVault.special != null && VarVault.specialLL != null) {
			VarVault.special.setOnClickListener(this);
			VarVault.specialLL.setOnClickListener(this);
			VarVault.special.setTypeface(VarVault.font);
		} else {
			Log.e("onResume()", "special or specialLL is null! Cannot set OnClickListener.");
		}


		VarVault.skills = (TextView) findViewById(R.id.btn_skills);
		VarVault.skillsLL = (LinearLayout) findViewById(R.id.btn_skills_box);
		if (VarVault.skills != null && VarVault.skillsLL != null) {
			VarVault.skills.setOnClickListener(this);
			VarVault.skillsLL.setOnClickListener(this);
			VarVault.skills.setTypeface(VarVault.font);
		} else {
			Log.e("onResume()", "skills or skillsLL is null! Cannot set OnClickListener.");
		}

		VarVault.perks = (TextView) findViewById(R.id.btn_perks);
		VarVault.perksLL = (LinearLayout) findViewById(R.id.btn_perks_box);

		if (VarVault.perks != null && VarVault.perksLL != null) {
			VarVault.perks.setOnClickListener(this);
			VarVault.perksLL.setOnClickListener(this);
			VarVault.perks.setTypeface(VarVault.font);
		} else {
			Log.e("onResume()", "perks or perksLL is null! Cannot set OnClickListener.");
		}


		VarVault.general = (TextView) findViewById(R.id.btn_general);
		VarVault.generalLL = (LinearLayout) findViewById(R.id.btn_general_box);
		if (VarVault.general != null && VarVault.generalLL != null) {
			VarVault.general.setOnClickListener(this);
			VarVault.generalLL.setOnClickListener(this);
			VarVault.general.setTypeface(VarVault.font);
		} else {
			Log.e("onResume()", "general or generalLL is null! Cannot set OnClickListener.");
		}

		if (VarVault.cnd != null) {
			VarVault.cnd.setOnClickListener(this);
			VarVault.cnd = (TextView) findViewById(R.id.btn_cnd);
			VarVault.cnd.setTypeface(VarVault.font);
		} else {
			Log.e("onResume()", "cnd or cndLL is null! Cannot set OnClickListener.");
		}

		if (VarVault.rad != null) {
			VarVault.rad = (TextView) findViewById(R.id.btn_rad);
			VarVault.rad.setTypeface(VarVault.font);
			VarVault.rad.setOnClickListener(this);
		} else {
			Log.e("onResume()", "rad or radLL is null! Cannot set OnClickListener.");
		}


		VarVault.stimpak = (TextView) findViewById(R.id.btn_stimpak);
		if (VarVault.stimpak != null) {
			VarVault.stimpak.setOnClickListener(this);
			VarVault.stimpak.setTypeface(VarVault.font);
		} else {
			Log.e("onResume()", "stimpak or stimpakLL is null! Cannot set OnClickListener.");
		}

		VarVault.flashlight = (TextView) findViewById(R.id.btn_flashlight);
		if (VarVault.flashlight != null) {
			VarVault.flashlight.setOnClickListener(this);
			VarVault.flashlight.setTypeface(VarVault.font);
		} else {
			Log.e("onResume()", "flashlight or flashlightLL is null! Cannot set OnClickListener.");
		}
	}


	protected void onDestroy() {
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		if (VarVault.isCamOn == true) {
			Parameters params = VarVault.mCamera.getParameters();
			params.setFlashMode(Parameters.FLASH_MODE_OFF);
			VarVault.mCamera.setParameters(params);
			VarVault.mCamera.stopPreview();
			VarVault.mCamera.release();
			VarVault.mCamera = null;
			VarVault.isCamOn = false;} else {
			Log.e("onDestroy()", "don't know what happens here");
		}

		this.unregisterReceiver(VarVault.mBatInfoReceiver);
		super.onDestroy();
	}


	private void populateOwnedWeapons() {
		Log.e("populateOwnedWeapons()", "don't know what happens here");
		// Open Database
		dbHelper database = new dbHelper(MainMenu.this);
		Log.d("PopulateOwnedWeapons", "new database");
		Log.d("DB", "Getting a writable database...");
		SQLiteDatabase db = database.getWritableDatabase();
		Log.d("DB", "...writable database gotten!");

		// Get EVERYTHING from OwnedWeapons
		Cursor allWeapons = db.query("OwnedWeapons", new String[]{dbHelper.colName, dbHelper.colIsWearing}, null, null, null, null, "_id");
		allWeapons.moveToFirst();
		LinearLayout weaponsList = (LinearLayout) findViewById(R.id.weaponsList);

		for (int i = 0; i < 20 && !allWeapons.isAfterLast(); i++) {

			String tempName = "";
			int isWearing = 0;
			Log.d("PopulateOwnedWeapons", "...looping through all weaponsssss!");
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
			lp.setMargins(15, 0, 0, 15);
			tempName = allWeapons.getString(0);
			isWearing = allWeapons.getInt(1);

			VarVault.Weapons.add(i, new TextView(this));
			Log.d("DB", "...writable database gotten!");
			VarVault.Weapons.get(i).setLayoutParams(lp);
			VarVault.Weapons.get(i).setTypeface(VarVault.font);
			VarVault.Weapons.get(i).setTextSize((float) 22.0);
			VarVault.Weapons.get(i).setTextColor(Color.parseColor("#AAFFAA"));

			// Are they wearing it?
			if (isWearing == 0)
				VarVault.Weapons.get(i).setText("  " + tempName);
			else
				VarVault.Weapons.get(i).setText("\u25a0 " + tempName);

			VarVault.Weapons.get(i).setOnLongClickListener(this);
			weaponsList.addView(VarVault.Weapons.get(i));

			allWeapons.moveToNext();
		}
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

	protected boolean isRouteDisplayed() {
		// TODO Auto-generated method stub
		return false;
	}

	@SuppressLint("MissingPermission")
	@Override
	public void onMapReady(GoogleMap googleMap) {
		mMap = googleMap;
		Log.e("onMapReady", "just initialized mMap");
		// Configure map visuals
		Log.e("Map Styling", "configureMapSettings()");
		configureMapSettings();

		Log.e("Map Styling", "triggering applyMapStyle()");
		applyMapStyle();

		// Set initial camera position to current location if available
		Log.e("location", "setInitialCameraPosition()");

		// Store map for later use
		VarVault.mMap = mMap;

		// Enable location if permission granted
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			mMap.setMyLocationEnabled(true);
			Log.e("location", "setMyLocationEnabled(true)");

		} else {
			requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
			Log.e("location", "requestpermissionlauncher)");
		}
	}

	private void applyMapStyle() {
		Log.e("Map Styling", "starting now.");
		try {
			boolean success = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));
			if (!success) {
				Log.e("Map Styling", "Style parsing failed.");
			} else {
				Log.d("MapStyling", "Map style applied successfully.");
			}
		} catch (Resources.NotFoundException e) {
			Log.e("Map Styling", "Can't find style. Error: ", e);
		}
	}

	private void configureMapSettings() {
		// Set map type and UI settings
		mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
		Log.e("Configure Map Settings", "setMapType MAP_TYPE_NORMAL");
		mMap.getUiSettings().setMapToolbarEnabled(false);  // Disable map toolbar
		Log.e("Configure Map Settings", "sidable toolbar");
		mMap.setBuildingsEnabled(false);  // Disable 3D buildings
		Log.e("Configure Map Settings", "disable 3D buildings");
		mMap.setIndoorEnabled(true);
		mMap.setTrafficEnabled(false);
		// Enable gestures
//		mMap.getUiSettings().setRotateGesturesEnabled(false);  // Disable rotation
//		Log.e("Configure Map Settings", "disable rotation");
		mMap.getUiSettings().setTiltGesturesEnabled(false);    // Disable tilt gestures
		Log.e("Configure Map Settings", "disable tilt");

		// Enable compass and disable zoom controls
//		mMap.getUiSettings().setCompassEnabled(true);
//		Log.e("Configure Map Settings", "set compass enabled");
		mMap.getUiSettings().setZoomControlsEnabled(false);
		Log.e("Configure Map Settings", "set zoom controls disabled");
		mMap.getUiSettings().setMyLocationButtonEnabled(false);
//		mMap.getUiSettings().setAllGesturesEnabled(false);
	}

	// This method ensures the map is centered around the player's location
	private void setInitialCameraPosition(float zoomLevel) {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

			FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
			fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
				if (location != null && mMap != null) {
					LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
					VarVault.playerLocation = currentLocation;

					CameraPosition position = new CameraPosition.Builder()
							.target(currentLocation)
							.zoom(zoomLevel)// Decide zoom level based on parameter
							.bearing(0)
							.tilt(0)
							.build();

					mMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 600, null);

					Log.d("Map", "Camera centered at " + currentLocation + " with zoom " + zoomLevel);
				} else if (location == null) {
					Log.e("Map", "Location is null");
				} else if (mMap == null) {
					Log.e("Map", "mMap is null");
				}
			});
		} else {
			Log.e("Map", "Location permission not granted");
		}
	}



	private void enableLocation(float zoomLevel) {
		FusedLocationProviderClient fusedLocationClient =
				LocationServices.getFusedLocationProviderClient(this);

		// Permission check
		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED) {

			requestPermissionLauncher.launch(
					Manifest.permission.ACCESS_FINE_LOCATION);
			return;
		}

		// Map must be ready
		if (mMap == null) {
			return;
		}
		try {
			mMap.setMyLocationEnabled(true);
			mMap.getUiSettings().setMyLocationButtonEnabled(true);
		} catch (SecurityException e) {
			// Get last known location
			fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
				if (location == null) {
					Toast.makeText(
							MainMenu.this,
							"Unable to get current location",
							Toast.LENGTH_SHORT
					).show();
					return;
				}
				VarVault.playerLocation = new LatLng(location.getLatitude(), location.getLongitude());

				// Move the camera
				if (mMap != null) {
					mMap.moveCamera(
							CameraUpdateFactory.newLatLngZoom(
									VarVault.playerLocation,
									zoomLevel
							)
					);
				}
			});

		}
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
		if (requestCode == 1) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				// Permission granted, proceed with accessing contacts
//				accessContacts();
			} else {
				// Permission denied, show a message to the user
				Toast.makeText(this, "Permission denied to read contacts", Toast.LENGTH_SHORT).show();
			}
		}
	}

	private void moveCamera(LatLng target, float zoom) {
		if (VarVault.mMap == null || target == null) return;

		CameraUpdate update = CameraUpdateFactory.newLatLngZoom(target, zoom);
		VarVault.mMap.animateCamera(update, 600, null);
	}

	private void showWorldMap() {
		if (VarVault.mMap == null || VarVault.playerLocation == null) {
			Log.w("Map", "Player location or map is not ready.");
			return;
		}
		// Check if the location permission is granted
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
				== PackageManager.PERMISSION_GRANTED) {
			// Permission granted, enable location
			enableLocation(VarVault.WORLD_MAP_ZOOM);
		} else {
			// Permission not granted, request it
			requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
		}
		// Ensure my location button is enabled
		VarVault.mMap.setMyLocationEnabled(true);

		// Set the camera update with zoom and animate the movement
		VarVault.mMap.animateCamera(
				CameraUpdateFactory.newLatLngZoom(VarVault.playerLocation, zoomLevel),
				600,
				null
		);

		// Optional: Log position when the camera has stopped moving
		VarVault.mMap.setOnCameraIdleListener(() -> {
			LatLng cameraPosition = VarVault.mMap.getCameraPosition().target;
			Log.d("Map", "Camera position is: " + cameraPosition);
		});

	}

	private void showLocalMap() {
		if (VarVault.mMap == null || VarVault.playerLocation == null) {
			Log.w("Map", "Player location or map is not ready.");
			return;
		}
		// Check if the location permission is granted
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
				== PackageManager.PERMISSION_GRANTED) {
			// Permission granted, enable location
			enableLocation(VarVault.LOCAL_MAP_ZOOM);
		} else {
			// Permission not granted, request it
			requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
		}
		// Ensure my location button is enabled
		VarVault.mMap.setMyLocationEnabled(true);

		// Set the camera update with zoom and animate the movement
		VarVault.mMap.animateCamera(
				CameraUpdateFactory.newLatLngZoom(VarVault.playerLocation, zoomLevel),
				600,
				null
		);

		// Optional: Log position when the camera has stopped moving
		VarVault.mMap.setOnCameraIdleListener(() -> {
			LatLng cameraPosition = VarVault.mMap.getCameraPosition().target;
			Log.d("Map", "Camera position is: " + cameraPosition);
		});
	}

	private final SensorEventListener sensorEventListener = new SensorEventListener() {
		@Override
		public void onSensorChanged(SensorEvent event) {
			if (mMap == null) return;

			SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
			SensorManager.getOrientation(rotationMatrix, orientation);
			float azimuthInRadians = orientation[0];
			float azimuthInDegrees = (float) Math.toDegrees(azimuthInRadians);
			azimuthInDegrees = (azimuthInDegrees + 360) % 360;

			// Update camera bearing to match compass
			CameraPosition currentPos = mMap.getCameraPosition();
			CameraPosition camPos = new CameraPosition.Builder(currentPos)
					.bearing(azimuthInDegrees)
					.tilt(0)  // keep top-down
					.build();
//			mMap.moveCamera(CameraUpdateFactory.newCameraPosition(camPos));
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}
	};

	public void onSensorChanged(SensorEvent event) {
		if (!isCompassModeEnabled) return;
		if (mMap == null || VarVault.playerLocation == null) return;

		SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
		SensorManager.getOrientation(rotationMatrix, orientation);

		float azimuth = (float) Math.toDegrees(orientation[0]);
		azimuth = (azimuth + 360) % 360;

		CameraPosition camPos = new CameraPosition.Builder()
				.target(VarVault.playerLocation)
				.zoom(mMap.getCameraPosition().zoom)
				.bearing(azimuth)
				.tilt(0)
				.build();

		mMap.moveCamera(CameraUpdateFactory.newCameraPosition(camPos));
	}

	private void updateCompassIcon() {
		compassToggle.setAlpha(isCompassModeEnabled ? 1.0f : 0.4f);
	}

	//unused but appaerently useful?
	private void loadMapFragmentOnMapReady() {

		SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
				.findFragmentByTag("MAP_FRAGMENT_TAG");

		if (mapFragment == null) {
			mapFragment = SupportMapFragment.newInstance();
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.map_fragment_container, mapFragment, "MAP_FRAGMENT_TAG")
					.commit();
			Log.e("MapFragment", "Map fragment created and added.");
		} else {
			Log.e("MapFragment", "Map fragment already exists, replacing.");
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.map_fragment_container, mapFragment, "MAP_FRAGMENT_TAG")
					.commit();
		}


		mapFragment.getMapAsync(this);

		ViewGroup mapFragmentContainer = findViewById(R.id.map_fragment_container);
		if (mapFragmentContainer == null) {
			Log.e("MapFragment", "mapFragmentContainer is not found.");
			return;
		}
		// Ensure bottom bar is on top and clickable
		ViewGroup bottomBar = findViewById(R.id.bottom_panel);
		if (bottomBar != null) bottomBar.bringToFront();
	}


	private void initializeMenuButtons() {
		// Initialize the "Stats" button
		VarVault.stats = findViewById(R.id.left_stats);
		TextView statsText = findViewById(R.id.left_button_stats);
		statsText.setTypeface(VarVault.font);
		statsText.setTextColor(Color.argb(100, 255, 225, 0));
		VarVault.stats.setOnClickListener(this);

		// Initialize the "Items" button
		VarVault.items = findViewById(R.id.left_items);
		TextView itemsText = findViewById(R.id.left_button_items);
		itemsText.setTypeface(VarVault.font);
		itemsText.setTextColor(Color.argb(100, 255, 225, 0));
		VarVault.items.setOnClickListener(this);

		// Initialize the "Data" button
		VarVault.data = findViewById(R.id.left_data);
		TextView dataText = findViewById(R.id.left_button_data);
		dataText.setTypeface(VarVault.font);
		dataText.setTextColor(Color.argb(100, 255, 225, 0));
		VarVault.data.setOnClickListener(this);
		// For STATS button
		TextView statsButton = findViewById(R.id.left_button_stats);
		if (statsButton != null) {
			statsButton.setTypeface(VarVault.font);  // Reapply the font
			statsButton.setTextColor(Color.argb(100, 255, 225, 0));  // Ensure the text color is correct
		}

		// For ITEMS button
		TextView itemsButton = findViewById(R.id.left_button_items);
		if (itemsButton != null) {
			itemsButton.setTypeface(VarVault.font);  // Reapply the font
			itemsButton.setTextColor(Color.argb(100, 255, 225, 0));  // Ensure the text color is correct
		}

		// For DATA button
		TextView dataButton = findViewById(R.id.left_button_data);
		if (dataButton != null) {
			dataButton.setTypeface(VarVault.font);  // Reapply the font
			dataButton.setTextColor(Color.argb(100, 255, 225, 0));  // Ensure the text color is correct
		}

		// Step 4: Check permissions and enable location if necessary
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			enableLocation(zoomLevel);  // Enable location if permission is granted
		}
	}

	private void recenterMap(float zoom) {
		if (mMap == null || VarVault.playerLocation == null) return;

		CameraPosition camPos = new CameraPosition.Builder()
				.target(VarVault.playerLocation)
				.zoom(zoom)
				.bearing(mMap.getCameraPosition().bearing)
				.tilt(0)
				.build();

		mMap.animateCamera(
				CameraUpdateFactory.newCameraPosition(camPos),
				600,
				null
		);
	}

	private void lockMapNorth() {
		if (mMap == null || VarVault.playerLocation == null) return;

		CameraPosition camPos = new CameraPosition.Builder()
				.target(VarVault.playerLocation)
				.zoom(mMap.getCameraPosition().zoom)
				.bearing(0f)
				.tilt(0)
				.build();

		mMap.animateCamera(
				CameraUpdateFactory.newCameraPosition(camPos),
				300,
				null
		);
	}

	ImageButton compassToggle;

	public void initCompassToggle(View view) {

		compassToggle = findViewById(R.id.btnCompassToggle);
		Log.e("Compass", "btn parent=" + compassToggle.getParent());
		Log.e("Compass", "Compass attached = " + compassToggle.isAttachedToWindow());
		// Safety checks
		compassToggle.setClickable(true);
		compassToggle.setFocusable(true);
		compassToggle.bringToFront();

//		compassToggle.invalidate();

		// TAP
		compassToggle.setOnClickListener(v -> {
			Log.d("Compass", "CLICK");
			isCompassModeEnabled = !isCompassModeEnabled;

			if (!isCompassModeEnabled) {
				lockMapNorth();
			}

			updateCompassIcon();
		});

		// LONG PRESS
		compassToggle.setOnLongClickListener(v -> {
			if (mMap != null) {
				mMap.getUiSettings().setRotateGesturesEnabled(true);
				if (VarVault.playerLocation != null) {
					mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
							VarVault.playerLocation, zoomLevel
					));
					Log.d("Compass", "Rotation ENABLED and map centered (long press)");
				} else {
					Log.d("Compass", "Player location not ready yet");
				}
			}
			v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
			return true;
		});

		// TOUCH (release only)
		compassToggle.setOnTouchListener((v, event) -> {
			switch (event.getAction()) {
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					Log.d("Compass", "RELEASE");
					if (mMap != null) {
						mMap.getUiSettings().setRotateGesturesEnabled(false);
					}
					if (!isCompassModeEnabled) {
						lockMapNorth();
					}
					break;
			}
			return false; // VERY IMPORTANT
		});
	}

}
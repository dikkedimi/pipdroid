package com.skettidev.pipdroid;

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
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
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


import java.io.IOException;


public class MainMenu extends AppCompatActivity implements OnMapReadyCallback, SurfaceHolder.Callback, View.OnClickListener, View.OnLongClickListener {


	//private vars
	public static GoogleMap mMap;
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
	private boolean openWorldMapByDefault = false;
	// default to WORLD MAP


	private enum MapViewType {WORLD, LOCAL}


	//???
	private float[] rotationMatrix = new float[9];
	private float[] orientation = new float[3];
	private float azimuthDeg;

	private final ActivityResultLauncher<String> requestPermissionLauncher =
			registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
				if (isGranted) {
					enableLocation();  // Enable location if permission is granted
				} else {
					Toast.makeText(MainMenu.this, "Permission denied", Toast.LENGTH_SHORT).show();
				}
			});
//	FusedLocationProviderClient fusedLocationClient= LocationServices.getFusedLocationProviderClient(this);

	// ########################
	// ## On app start ########
	// ########################
//	@Override
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
//		// Allow content behind system bars
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
		setContentView(R.layout.main);


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

		// ===== Initialize stats and arrays =====
		initSkills();
		initSpecial();
		InitializeArrays.all();
		onClick(VarVault.stats);
		initCaps();

		// ===== Optional: initialize map immediately =====
		// If you want the map ready at startup, you can call:
		// dataClicked();
	}

	@Override
	public void onClick(View source) {

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
		ViewGroup topBar = findViewById(R.id.top_bar);
		ViewGroup bottomBar = findViewById(R.id.bottom_bar);

		// Sort the source
		if (source == VarVault.stats)
			statsClicked();
		else if (source == VarVault.statusLL)
			statusClicked();
		else if (source == VarVault.specialLL || source == VarVault.special)
			specialClicked();
		else if (source == VarVault.skillsLL || source == VarVault.skills)
			skillsClicked();
		else if (source == VarVault.perksLL) {
		} else if (source == VarVault.generalLL) {
		} else if (source == VarVault.cnd || source == VarVault.rad || source == VarVault.stimpak) {
		} else if (VarVault.SUBMENU_SPECIAL.contains(source))
			specialStatClicked(source);
		else if (VarVault.SUBMENU_SKILLS.contains(source))
			skillStatClicked(source);
		else if (source == VarVault.flashlight)
			flashlightClicked();
		else if (source == VarVault.items)
			itemsClicked();
		else if (source == VarVault.weaponsLL)
			weaponsClicked();
		else if (VarVault.Weapons.contains(source)) {
		} else if (source == VarVault.apparelLL)
			apparelClicked();
		else if (VarVault.Apparel.contains(source)) {
		} else if (source == VarVault.aidLL) {
			updateCAPS();
		} else if (source == VarVault.miscLL) {
			updateCAPS();
		} else if (source == VarVault.ammoLL) {
			updateCAPS();
		} else if (source == VarVault.data)
			dataClicked();

			// ===== DATA BUTTONS HANDLING =====
		else if (source == VarVault.localMapLL)
			if (mapFragment != null) {
				showLocalMap();
				Log.d("Menu", "source == VarVault.localMapLL");
			} else if (source == VarVault.worldMapLL)
				if (mapFragment != null) {
					showWorldMap();
					Log.d("Menu", "source == VarVault.worldMapLL");
				} else if (source == VarVault.questsLL)
					Log.d("Menu", "Quests clicked");
				else if (source == VarVault.notesLL)
					Log.d("Menu", "Notes clicked");
				else if (source == VarVault.radioLL)
					Log.d("Menu", "Radio clicked");

				else {
					topBar.removeAllViews();
					midPanel.removeAllViews();
					showWorldMap();
					bottomBar.removeAllViews();
					Log.d("Menu", "Removed all views");
				}
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

	private void statsClicked() {
		// Clear crap
		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		ViewGroup topBar = (ViewGroup) findViewById(R.id.top_bar);
		ViewGroup bottomBar = (ViewGroup) findViewById(R.id.bottom_bar);
		LayoutInflater inf = this.getLayoutInflater();

		midPanel.removeAllViews();
		topBar.removeAllViews();
		bottomBar.removeAllViews();

		// Main screen turn on
		inf.inflate(R.layout.status_screen, midPanel);
		inf.inflate(R.layout.stats_bar_top, topBar);
		inf.inflate(R.layout.stats_bar_bottom, bottomBar);

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

	}

	private void itemsClicked() {
		// Clear crap
		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		ViewGroup topBar = (ViewGroup) findViewById(R.id.top_bar);
		ViewGroup bottomBar = (ViewGroup) findViewById(R.id.bottom_bar);
		LayoutInflater inf = this.getLayoutInflater();

		midPanel.removeAllViews();
		topBar.removeAllViews();
		bottomBar.removeAllViews();

		// Main screen turn on
		inf.inflate(R.layout.weapons_screen, midPanel);
		inf.inflate(R.layout.items_bar_top, topBar);
		inf.inflate(R.layout.items_bar_bottom, bottomBar);

		VarVault.title = (TextView) findViewById(R.id.title_items);
		VarVault.title.setTypeface(VarVault.font);

		VarVault.wg = (TextView) findViewById(R.id.wg_items);
		VarVault.maxWG.setValue(150 + (10 * VarVault.strength.getValue()));
		updateWG();
		VarVault.wg.setTypeface(VarVault.font);

		VarVault.caps = (TextView) findViewById(R.id.caps_items);
		updateCAPS();
		VarVault.caps.setTypeface(VarVault.font);
		//VarVault.caps.setText(VarVault.curCaps.getValue());

		VarVault.bat = (TextView) findViewById(R.id.bat_items);
		VarVault.bat.setTypeface(VarVault.font);
		this.registerReceiver(VarVault.mBatInfoReceiver, new IntentFilter(
				Intent.ACTION_BATTERY_CHANGED));

		// Button-ize the buttons
		VarVault.weapons = (TextView) findViewById(R.id.btn_weapons);
		VarVault.weaponsLL = (LinearLayout) findViewById(R.id.btn_weapons_box);
		VarVault.weapons.setTypeface(VarVault.font);
		VarVault.weaponsLL.setOnClickListener(this);

		VarVault.apparel = (TextView) findViewById(R.id.btn_apparel);
		VarVault.apparelLL = (LinearLayout) findViewById(R.id.btn_apparel_box);
		VarVault.apparel.setTypeface(VarVault.font);
		VarVault.apparelLL.setOnClickListener(this);

		VarVault.aid = (TextView) findViewById(R.id.btn_aid);
		VarVault.aidLL = (LinearLayout) findViewById(R.id.btn_aid_box);
		VarVault.aid.setTypeface(VarVault.font);
		VarVault.aidLL.setOnClickListener(this);

		VarVault.misc = (TextView) findViewById(R.id.btn_misc);
		VarVault.miscLL = (LinearLayout) findViewById(R.id.btn_misc_box);
		VarVault.misc.setTypeface(VarVault.font);
		VarVault.miscLL.setOnClickListener(this);

		VarVault.ammo = (TextView) findViewById(R.id.btn_ammo);
		VarVault.ammoLL = (LinearLayout) findViewById(R.id.btn_ammo_box);
		VarVault.ammo.setTypeface(VarVault.font);
		VarVault.ammoLL.setOnClickListener(this);

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

	private void dataClicked() {
		//view settings
		openWorldMapByDefault = true; //default to WORLD_MAP_ZOOM, otherwise to LOCAL_MAP_ZOOM

		//clear crap
		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		ViewGroup topBar = (ViewGroup) findViewById(R.id.top_bar);
		ViewGroup bottomBar = (ViewGroup) findViewById(R.id.bottom_bar);
		LayoutInflater inf = this.getLayoutInflater();

		midPanel.removeAllViews();
		topBar.removeAllViews();
		bottomBar.removeAllViews();

		//main screen on
		// Inflate top and bottom bars
		inf.inflate(R.layout.data_bar_top, topBar, true);
		inf.inflate(R.layout.data_bar_bottom, bottomBar, true);
		// Inflate map_screen once
		View mapScreenView = inf.inflate(R.layout.map_screen, midPanel, true);
		FrameLayout mapContainer = mapScreenView.findViewById(R.id.map_container);

//		ViewGroup midPanel = findViewById(R.id.mid_panel);
//		ViewGroup topBar = findViewById(R.id.top_bar);
//		ViewGroup bottomBar = findViewById(R.id.bottom_bar);

		// Create the map fragment programmatically
		SupportMapFragment mapFragment = SupportMapFragment.newInstance();
		getSupportFragmentManager()
				.beginTransaction()
				.replace(mapContainer.getId(), mapFragment)
				.commit();

		// Ensure fragment is attached before requesting the map
		getSupportFragmentManager().executePendingTransactions();
		mapFragment.getMapAsync(this);
//		initCompassToggle();

		// Button-ize the bottom bar buttons
		VarVault.localMap = bottomBar.findViewById(R.id.btn_localmap);
		VarVault.localMapLL = bottomBar.findViewById(R.id.btn_localmap_box);
		VarVault.localMap.setTypeface(VarVault.font);
		VarVault.localMapLL.setOnClickListener(v -> showLocalMap());


		VarVault.worldMap = bottomBar.findViewById(R.id.btn_worldmap);
		VarVault.worldMapLL = bottomBar.findViewById(R.id.btn_worldmap_box);
		VarVault.worldMap.setTypeface(VarVault.font);
		VarVault.worldMapLL.setOnClickListener(v -> showWorldMap());

		VarVault.quests = bottomBar.findViewById(R.id.btn_quests);
		VarVault.questsLL = bottomBar.findViewById(R.id.btn_quests_box);
		VarVault.quests.setTypeface(VarVault.font);
		VarVault.questsLL.setOnClickListener(v -> this);

		VarVault.notes = bottomBar.findViewById(R.id.btn_notes);
		VarVault.notesLL = bottomBar.findViewById(R.id.btn_notes_box);
		VarVault.notes.setTypeface(VarVault.font);
		VarVault.notesLL.setOnClickListener(v -> this);

		VarVault.radio = bottomBar.findViewById(R.id.btn_radio);
		VarVault.radioLL = bottomBar.findViewById(R.id.btn_radio_box);
		VarVault.radio.setTypeface(VarVault.font);
		VarVault.radioLL.setOnClickListener(v -> this);

	}


	private void statusClicked() {

		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		midPanel.removeAllViews();

		LayoutInflater inf = this.getLayoutInflater();
		inf.inflate(R.layout.status_screen, midPanel, true);

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
		// Clear crap
		ViewGroup midPanel = (ViewGroup) findViewById(R.id.mid_panel);
		ViewGroup topBar = (ViewGroup) findViewById(R.id.top_bar);
		ViewGroup bottomBar = (ViewGroup) findViewById(R.id.bottom_bar);
		LayoutInflater inf = this.getLayoutInflater();

		midPanel.removeAllViews();
		topBar.removeAllViews();
		bottomBar.removeAllViews();

		// Main screen turn on
		inf.inflate(R.layout.status_screen, midPanel);
		inf.inflate(R.layout.stats_bar_top, topBar);
		inf.inflate(R.layout.stats_bar_bottom, bottomBar);

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
		VarVault.status.setOnClickListener(this);
		VarVault.statusLL.setOnClickListener(this);

		VarVault.special = (TextView) findViewById(R.id.btn_special);
		VarVault.specialLL = (LinearLayout) findViewById(R.id.btn_special_box);
		VarVault.special.setTypeface(VarVault.font);
		VarVault.special.setOnClickListener(this);
		VarVault.specialLL.setOnClickListener(this);

		VarVault.skills = (TextView) findViewById(R.id.btn_skills);
		VarVault.skillsLL = (LinearLayout) findViewById(R.id.btn_skills_box);
		VarVault.skills.setTypeface(VarVault.font);
		VarVault.skills.setOnClickListener(this);
		VarVault.skillsLL.setOnClickListener(this);

		VarVault.perks = (TextView) findViewById(R.id.btn_perks);
		VarVault.perksLL = (LinearLayout) findViewById(R.id.btn_perks_box);
		VarVault.perks.setTypeface(VarVault.font);
		VarVault.perks.setOnClickListener(this);
		VarVault.perksLL.setOnClickListener(this);

		VarVault.general = (TextView) findViewById(R.id.btn_general);
		VarVault.generalLL = (LinearLayout) findViewById(R.id.btn_general_box);
		VarVault.general.setTypeface(VarVault.font);
		VarVault.general.setOnClickListener(this);
		VarVault.generalLL.setOnClickListener(this);

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

	protected void onDestroy() {
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
		this.unregisterReceiver(VarVault.mBatInfoReceiver);
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

	private void populateOwnedWeapons() {

		// Open Database
		dbHelper database = new dbHelper(MainMenu.this);

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

			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
			lp.setMargins(15, 0, 0, 15);
			tempName = allWeapons.getString(0);
			isWearing = allWeapons.getInt(1);

			VarVault.Weapons.add(i, new TextView(this));

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

	@SuppressLint("MissingPermission")
	@Override
	public void onMapReady(GoogleMap googleMap) {
		initCompassToggle();
		mMap = googleMap;           // ← FIX
		VarVault.mMap = googleMap;           // ← FIX
		// ===== Map visuals =====
		mMap.setMapType(googleMap.MAP_TYPE_NORMAL);
//		mMap.getUiSettings().setMapToolbarEnabled(false);
		mMap.setBuildingsEnabled(false);
		VarVault.mMap.getUiSettings().setCompassEnabled(false);
		mMap.getUiSettings().setMapToolbarEnabled(false);

		mMap.getUiSettings().setIndoorLevelPickerEnabled(false);

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
		FusedLocationProviderClient fusedLocationClient =
				LocationServices.getFusedLocationProviderClient(this);
		// Permission check
		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED) {

			requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
			return true;
		}

		// Map must be ready
		if (VarVault.mMap == null) {
			return false;
		}

		// Enable location layer
		try {
			mMap.setMyLocationEnabled(true);
			mMap.getUiSettings().setMyLocationButtonEnabled(true);

			// Get last known location
			fusedLocationClient.getLastLocation()
				.addOnSuccessListener(this, location -> {
					if (location == null) {
						Toast.makeText(MainMenu.this,
								"Unable to get current location",
								Toast.LENGTH_SHORT).show();
						return;
					}

					LatLng currentLocation =
							new LatLng(location.getLatitude(),
									location.getLongitude());
					mMap.clear();
					// either
//					Marker playerStart = mMap.addMarker(new MarkerOptions()
//							.position(currentLocation)
//							.icon(createCustomMarker(Color.parseColor("#00FF6E"), Color.parseColor("#007803"), 24))
//							.anchor(0.5f, 0.5f)); // center the marker
//							.title("This is where you started!"));
					// or
//					mMap.addMarker(new MarkerOptions()
//							.position(currentLocation)
//							.icon(createCustomMarkerWithLabel(
//									Color.parseColor("#007803"),
//									Color.parseColor("#00FF6E"),
//									10,             // radius in dp
//									"Where you started"             // your label
//							))
//							.anchor(0.5f, 0.5f)); // center marker

					VarVault.playerLocation = currentLocation;

					if (openWorldMapByDefault) {
						showWorldMap();
						openWorldMapByDefault = false;
					} else {

						mMap.moveCamera(
								CameraUpdateFactory.newLatLngZoom(
										VarVault.playerLocation, LOCAL_MAP_ZOOM));
					}

				});
			return true;

		} catch (SecurityException e) {
			// Safety net
			return false;
		}

	}

	private void disableLocation() {
		if (VarVault.mMap == null) return;

		try {
			VarVault.mMap.setMyLocationEnabled(false);
			Log.d("disableLocation", "set location enabled FALSE");
		} catch (SecurityException ignored) {}
		Log.d("disableLocation", "security exception ignored?");
		isLocationEnabled = false;

		// Optional cleanup
		// mMap.clear();   // only if you want markers gone
	}
	private void toggleLocation() {
		if (isLocationEnabled) {
			Log.d("Compass", "Tap detected: dosable location");
			disableLocation();
		} else {
			enableLocation();
		}
	}



	private static final float WORLD_MAP_ZOOM = 13.5f; // city-wide
	private static final float LOCAL_MAP_ZOOM = 16.5f; // nearby streets

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


	private void showWorldMap() {
		GoogleMap map = VarVault.mMap;
		LatLng player = VarVault.playerLocation;

		if (map == null) return;
		if (player == null) {
			Log.w("Map", "Player location not ready yet");
			return;
		}

		CameraUpdate update = CameraUpdateFactory.newLatLngZoom(player, WORLD_MAP_ZOOM);

		// Animate safely with duration + callback
		map.animateCamera(update, 600, new GoogleMap.CancelableCallback() {
			@Override
			public void onFinish() {
			}

			@Override
			public void onCancel() {
			}
		});
	}

	private void showLocalMap() {
		Log.d("showLocalMap", "source == VarVault.localMapLL");
		if (VarVault.mMap == null || VarVault.playerLocation == null) return;
		Log.d("showLocalMap", "VarVault.mMap != null, VarVault.playerLocation != null, continuing");
		Log.d("showLocalMap", "animate camera");
		VarVault.mMap.animateCamera(
				CameraUpdateFactory.newLatLngZoom(
						VarVault.playerLocation,
						LOCAL_MAP_ZOOM
				),
				600,
				null
		);
		Log.d("showLocalMap", "ending");
	}

	private void recenterMap(float zoom) {
		Log.d("recenterMap", "starting");
		if (mMap == null || VarVault.playerLocation == null) return;
		Log.d("recenterMap", "VarVault.mMap != null, VarVault.playerLocation != null");
		CameraPosition camPos = new CameraPosition.Builder()
				.target(VarVault.playerLocation)
				.zoom(zoom)
				.bearing(mMap.getCameraPosition().bearing)
				.tilt(0)
				.build();
		Log.d("recenterMap", "animate camera");
		mMap.animateCamera(
				CameraUpdateFactory.newCameraPosition(camPos),
				600,
				null
		);
	}


	private void lockMapNorth() {
		Log.d("lockMapNorth", "starting");
		if (VarVault.mMap == null || VarVault.playerLocation == null) {
			Toast.makeText(this, "Map not ready", Toast.LENGTH_SHORT).show();
		}

		Log.d("lockMapNorth", "VarVault.mMap != null, VarVault.playerLocation != null");
		CameraPosition camPos = new CameraPosition.Builder()
				.target(VarVault.playerLocation)
				.zoom(mMap.getCameraPosition().zoom)
				.bearing(0f)
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
		Log.d("initCompassToggle", "animate camera");
		compassToggle = findViewById(R.id.btnCompassToggle);

		// Safety
		if (compassToggle != null) {
			compassToggle.setClickable(true);
			Log.d("initCompassToggle", "animate camera");
			compassToggle.setFocusable(true);
			Log.d("initCompassToggle", "animate camera");
			compassToggle.setElevation(100f);
			Log.d("initCompassToggle", "animate camera");


			//click listeners (short, long, release(optional))
			// TAP → center map on player (works! Don't touch :p)
			compassToggle.setOnClickListener(v -> toggleLocation());

			}


			// LONG PRESS → toggle map rotation mode
			//old code
//			compassToggle.setOnLongClickListener(v -> {
//				Log.d("Compass", "Long press detected");
//				if (VarVault.mMap == null) {
//					Log.d("Compass", "VarVault.mMap == null, can't continue");
//					return true;
//				} else {
//					Log.d("Compass", "VarVault.mMap != null");
//					UiSettings ui = VarVault.mMap.getUiSettings();
//					ui.setCompassEnabled(!ui.isCompassEnabled());
//
//
//					// Toggle rotation mode
//					isMapRotationEnabled = !isMapRotationEnabled;
//					ui.setRotateGesturesEnabled(isMapRotationEnabled);
//					ui.setCompassEnabled(isMapRotationEnabled);
//
//					if (!isMapRotationEnabled) {
//						Log.d("Compass", "Map rotation disabled → locking north");
//						lockMapNorth(); // Map bearing reset to north
//					} else {
//						Log.d("Compass", "Map rotation enabled → map follows device heading");
//					}
//
//					v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
//					return true;
//				}
			// new code
			compassToggle.setOnLongClickListener(v -> {
				if (VarVault.mMap == null) return true;

				isMapRotationEnabled = !isMapRotationEnabled;

				UiSettings ui = VarVault.mMap.getUiSettings();
				ui.setRotateGesturesEnabled(isMapRotationEnabled);

				if (!isMapRotationEnabled) {
					lockMapNorth(); // reset bearing to north

				}

				v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
				return true;
			});
		}


	private void updateCompassIcon(float azimuthDeg) {
		if (compassToggle == null) return;
		Log.d("updateCompassIcon", "compassToggle == null, can't continue");
		// Only rotate the icon if map rotation is enabled
		if (isMapRotationEnabled) {
			// Animate the rotation
			RotateAnimation rotateAnim = new RotateAnimation(
					lastCompassRotation,
					-azimuthDeg, // negative to rotate opposite to device heading
					Animation.RELATIVE_TO_SELF, 0.5f,
					Animation.RELATIVE_TO_SELF, 0.5f
			);
			rotateAnim.setDuration(250);
			rotateAnim.setFillAfter(true);
			rotateAnim.setInterpolator(new LinearInterpolator());

			compassToggle.startAnimation(rotateAnim);

			lastCompassRotation = -azimuthDeg;
		}
	}


	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR && mMap != null) {
			float[] rotationMatrix = new float[9];
			SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

			float[] orientation = new float[3];
			SensorManager.getOrientation(rotationMatrix, orientation);

			float azimuthRad = orientation[0];
			float azimuthDeg = (float) Math.toDegrees(azimuthRad);

			if (isMapRotationEnabled) {
				// MAP ROTATION MODE → rotate map bearing
				CameraPosition current = mMap.getCameraPosition();
				CameraPosition newCam = new CameraPosition.Builder(current)
						.bearing(azimuthDeg)
						.build();
				mMap.moveCamera(CameraUpdateFactory.newCameraPosition(newCam));
			} else {
				// COMPASS MODE → rotate the icon only
				updateCompassIcon(azimuthDeg);
			}
		}
	}


	private final SensorEventListener sensorEventListener = new SensorEventListener() {
		@Override
		public void onSensorChanged(SensorEvent event) {
			if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD || event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				// Handle sensor changes (e.g., for compass or orientation)
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

}
package com.skettidev.pipdroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class VarVault {
// views
// ImageViews
	public static ImageView topLeft;
	public static ImageView topRight;
	public static ImageView bottomLeft;
	public static ImageView bottomRight;
	public static TextView left_button_items;
	public static void clearViews() {
		title = null;
		hp = null;
		ap = null;
		bat = null;
		// any other View references
	}

//	public static LinearLayout statsLL;
//	public static LinearLayout statusLL;
//	public static LinearLayout specialLL;
//	public static LinearLayout skillsLL;
	public static Map<String, LinearLayout> linearLayouts = new HashMap<>();
	public static Map<String, ImageView> imageViews = new HashMap<>();
	public static Map<String, TextView> labels = new HashMap<>();
	public static Map<String, TextView> textViews = new HashMap<>();

	// Layouts
	public static LinearLayout leftPanel;
	// init maps
	public static GoogleMap mMap;
	public static LatLng playerLocation;

	public static Sensor playerOrientation;
	public static Sensor playerMovement;

	// set local & world map zoom values
	public static final float WORLD_MAP_ZOOM = 13.5f; // city-wide
	public static final float LOCAL_MAP_ZOOM = 16.5f; // nearby streets

	public static final boolean setMyLocationButtonEnabledBool = false;
	public static final boolean setMyLocationEnabledBool = true;

	public static final int textColor = Color.argb(100, 0, 225, 0);;

	// SPECIAL stats

	protected static Stat strength = new Stat(), perception = new Stat(), endurance = new Stat(), charisma = new Stat(), intelligence = new Stat(), agility = new Stat(), luck = new Stat();
	protected static ArrayList<Stat> SPECIAL_STAT_VALUES = new ArrayList<Stat>();
	
	// Skill stats
	protected static Stat barter = new Stat(), big_guns = new Stat(), energy = new Stat(), explosives = new Stat(), lockpick = new Stat(), medicine = new Stat(), melee = new Stat(), repair = new Stat(), science = new Stat(), small_guns = new Stat(), sneak = new Stat(), speech = new Stat(), unarmed = new Stat();
	protected static ArrayList<Stat> SKILL_STAT_VALUES = new ArrayList<Stat>();
	
	// Three main buttons
	protected static ImageView stats, items, data;
	protected static ArrayList<ImageView> MAIN_BUTTONS = new ArrayList<ImageView>();

	// Top bar content
	protected static TextView title;
	protected static TextView bat;
	protected static TextView hp, ap;
	protected static TextView wg, caps;
	protected static Stat curWG, maxWG;
	protected static Stat curCaps;

	// Bottom bar content
	protected static TextView status, special, skills, perks, general;
	protected static LinearLayout statusLL, specialLL, skillsLL, perksLL, generalLL;
	protected static ArrayList<View> BOTTOM_BAR_STATS = new ArrayList<View>();

	// items view bottom bar
	protected static TextView weapons, apparel, aid, misc, ammo;
	protected static LinearLayout weaponsLL, apparelLL, aidLL, miscLL, ammoLL;
	protected static ArrayList<View> BOTTOM_BAR_ITEMS = new ArrayList<View>();

	// data view bottom bar
	protected static TextView localMap, worldMap, quests, notes, radio;
	protected static LinearLayout localMapLL, worldMapLL, questsLL, notesLL, radioLL;

	// Sub-menus
	protected static TextView stimpak, cnd, rad, flashlight;

	protected static TextView str, strSTAT, per, perSTAT, end, endSTAT, chr, chrSTAT, intel, intelSTAT, agi, agiSTAT, luk, lukSTAT;
	protected static ArrayList<TextView> SUBMENU_SPECIAL = new ArrayList<TextView>();

	protected static TextView bart, barterSTAT, bgns, big_gunsSTAT, nrg, energySTAT, expl, explosivesSTAT, lock, lockpickSTAT, medi, medicineSTAT, mlee, meleeSTAT, rpar, repairSTAT, sci, scienceSTAT, sgns, small_gunsSTAT, snek, sneakSTAT, spch, speechSTAT, uarm, unarmedSTAT;
	protected static ArrayList<TextView> SUBMENU_SKILLS = new ArrayList<TextView>();

	// Items
	final static ArrayList<TextView> Weapons = new ArrayList<TextView>();
	final static ArrayList<TextView> Apparel = new ArrayList<TextView>();

	// Images
	protected static ImageView specialImage, skillImage;

//	public static Fragment mMap; // type must be GoogleMap

	protected static Typeface font;


	// Camera stuff
	protected static Camera mCamera;
	protected static SurfaceView preview;
	protected static SurfaceHolder mHolder;
	public static boolean isCamOn = false;

	// Battery info receiver
	protected static BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context arg0, Intent intent) {
			int level = intent.getIntExtra("level", 0);
			bat.setText("Battery: " + String.valueOf(level) + " % ");
		}
	};

	// Misc stuff
	protected static int numContacts = 0;
}

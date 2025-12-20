package com.skettidev.pipdroid;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class Splash extends AppCompatActivity implements View.OnClickListener  {

	private boolean doubleBackToExitPressedOnce = false;
//	private LinearLayout frame = null;
	private TextView txt = null;
	private Typeface font;
	private MediaPlayer mp;

	private void loadContactsAndCount() {
		// Query the contacts
		Cursor cursor = getContentResolver().query(
				ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
				null, null, null,
				ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
		);

		if (cursor != null) {
			VarVault.numContacts = cursor.getCount();
			cursor.close(); // Always close the cursor
		} else {
			VarVault.numContacts = 0;
		}

		Toast.makeText(this, "Found " + VarVault.numContacts + " contacts", Toast.LENGTH_SHORT).show();
	}
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		// Request READ_CONTACTS at runtime if not yet granted
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
				!= PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, 1);
			// check if camera permission is granted
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
					!= PackageManager.PERMISSION_GRANTED) {
				// If permission is not granted, request it
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
				// Check if location permission is granted (doesn't work well here)
				if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
						!= PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
						!= PackageManager.PERMISSION_GRANTED) {
					// Request permissions if not granted
					ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
					return;
				}

			}
		}

        setContentView(R.layout.splash);

        mp = MediaPlayer.create(getApplicationContext(), R.raw.boot);
        mp.start();

        txt = (TextView) findViewById(R.id.splash_info);
        LinearLayout frame = (LinearLayout) findViewById(R.id.splash_frame);

        //Set custom font
        font = Typeface.createFromAsset(getAssets(), "Monofonto.ttf");
        txt.setTypeface(font);

        frame.setOnClickListener(this);
    }

	public void onClick(View v) {
		
		if (v.getId() == R.id.splash_frame)
		{
			txt.setTextAppearance(getApplicationContext(), R.style.text_clicked);
			txt.setTypeface(font);
			
			mp.stop();
			
			Intent i = new Intent(Splash.this, MainMenu.class);
			Splash.this.startActivity(i);
		}
	}
	
	@Override
	public void onDestroy(){
		mp.stop();
		super.onDestroy();
	}
}
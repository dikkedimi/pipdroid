package com.skettidev.pipdroid;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;


public class Splash extends AppCompatActivity implements View.OnClickListener {

	private boolean doubleBackToExitPressedOnce = false;
	private LinearLayout frame = null;
	private TextView txt = null;
	private Typeface font;
	private MediaPlayer mp;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
//
        mp = MediaPlayer.create(getApplicationContext(), R.raw.boot);
        mp.start();

        txt = (TextView) findViewById(R.id.splash_info);
        Framelayout frame = (LinearLayout) findViewById(R.id.frame);

        //Set custom font
        font = Typeface.createFromAsset(getAssets(), "Monofonto.ttf");
        txt.setTypeface(font);

        frame.setOnClickListener(this);
    }

	public void onClick(View v) {
		
		if (v.getId() == R.id.frame)
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
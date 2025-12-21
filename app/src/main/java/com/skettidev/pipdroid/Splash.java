package com.skettidev.pipdroid;
import com.skettidev.pipdroid.utils.PermissionManager;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.List;
import java.util.ArrayList;

public class Splash extends AppCompatActivity implements View.OnClickListener {

	private TextView txt;
	private Typeface font;
	private MediaPlayer mp;
	private ImageView splashImage;
	private LinearLayout frame;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.splash);

		// Initialize media player
		mp = MediaPlayer.create(getApplicationContext(), R.raw.boot);
		mp.start();

		// Get views
		txt = findViewById(R.id.splash_info);
		frame = findViewById(R.id.splash_frame);
		splashImage = findViewById(R.id.splash_image);

		// Set custom font
		font = Typeface.createFromAsset(getAssets(), "Monofonto.ttf");
		txt.setTypeface(font);

		frame.setOnClickListener(this);

		// Start CRT animation after a short delay
		new Handler().postDelayed(this::startCrtAnimation, 300);
	}

	private void startCrtAnimation() {
		splashImage.setVisibility(View.VISIBLE);
		splashImage.setScaleX(1f);
		splashImage.setScaleY(1f);
		splashImage.setAlpha(0f); // start invisible

		// Step 1: Flicker in parallel
		splashImage.animate()
				.alpha(1f)
				.setDuration(400)
				.withEndAction(() -> {
					// Step 2: Optional small vertical scale for scanline effect
					splashImage.animate()
							.scaleY(1.05f)
							.setDuration(150)
							.withEndAction(() -> splashImage.animate()
									.scaleY(1f)
									.setDuration(150)
									.withEndAction(this::requestNeededPermissions)
									.start())
							.start();
				})
				.start();

		// Optional: small jitter effect (rotation)
		ObjectAnimator swing = ObjectAnimator.ofFloat(splashImage, "rotation", 0f, 1.5f, -1.5f, 0f);
		swing.setDuration(400);
		swing.start();
	}

	private void onPermissionsGranted() {
		// Start MainMenu or other setup
		Intent i = new Intent(Splash.this, MainMenu.class);
		startActivity(i);
		finish();
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.splash_frame) {
			txt.setTextAppearance(getApplicationContext(), R.style.text_clicked);
			txt.setTypeface(font);

			if (mp != null && mp.isPlaying()) {
				mp.stop();
			}

			Intent i = new Intent(Splash.this, MainMenu.class);
			startActivity(i);
			finish();
		}
	}

	@Override
	public void onDestroy() {
		if (mp != null && mp.isPlaying()) {
			mp.stop();
		}
		super.onDestroy();
	}

	private void requestNeededPermissions() {
		String[] permissions = {
				Manifest.permission.READ_CONTACTS,
				Manifest.permission.CAMERA,
				Manifest.permission.ACCESS_FINE_LOCATION
		};

		// Request permissions that are not granted
		List<String> permissionsToRequest = new ArrayList<>();
		for (String perm : permissions) {
			if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
				permissionsToRequest.add(perm);
			}
		}

		// If there's any permission left to request, request them
		if (!permissionsToRequest.isEmpty()) {
			ActivityCompat.requestPermissions(this,
					permissionsToRequest.toArray(new String[0]), 1);
		} else {
			// If all permissions are already granted
			onPermissionsGranted();
			PermissionManager.getInstance().setPermissionsGranted(true);
		}
	}
}
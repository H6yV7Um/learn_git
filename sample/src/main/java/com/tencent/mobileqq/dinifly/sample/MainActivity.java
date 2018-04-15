package com.tencent.mobileqq.dinifly.sample;


import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.Toast;

import com.tencent.mobileqq.dinifly.DiniFlyAnimationView;
import com.tencent.mobileqq.dinifly.LottieComposition;
import com.tencent.mobileqq.dinifly.OnCompositionLoadedListener;
import com.tencent.mobileqq.dinifly.ViewAnimation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity implements OnClickListener{
	DiniFlyAnimationView animationView;
	LogWriter mLogWriter;
	Button bt;
	Button anim0;
	Button anim1;
	Button anim2;
	Button anim3;
	Button anim4;
	 private static final int RC_ASSET = 1337;
	  private static final int RC_FILE = 1338;
	  private static final int RC_URL = 1339;
	  static final String EXTRA_ANIMATION_NAME = "animation_name";
	  private final Map<String, String> assetFolders = new HashMap<String, String>() {{
		    put("WeAccept.json", "Images/WeAccept");
		    put("bubble.json", "Images/bubble");
		    put("aioview.json", "view_aio/images");
		  }};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		 File logf = new File(Environment.getExternalStorageDirectory()  
		            + File.separator + "Lottie_FPS.txt");  
		       try {
				   if(!logf.exists()){
					   logf.createNewFile();
				   }
		        mLogWriter = LogWriter.open(logf.getAbsolutePath());  
		    } catch (IOException e) {  
		    }
		setContentView(R.layout.activity_main);
		animationView = (DiniFlyAnimationView)findViewById(R.id.animaview);
		animationView.setImageAssetsFolder(assetFolders.get("aioview.json"));
		animationView.playAnimation();
		animationView.loop(true);
//		animationView.useExperimentalHardwareAcceleration(true);
		bt = (Button)findViewById(R.id.bt);
		bt.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				onLoadFileClicked();
			}
		});
		anim0= (Button)findViewById(R.id.anim0);
		anim1 = (Button)findViewById(R.id.anim1);
		anim2 = (Button)findViewById(R.id.anim2);
//		anim3 = (Button)findViewById(R.id.anim3);
		anim4 = (Button)findViewById(R.id.anim4);
		anim0.setOnClickListener(this);
		anim1.setOnClickListener(this);
		anim2.setOnClickListener(this);
//		anim3.setOnClickListener(this);
		anim4.setOnClickListener(this);
		
		ViewAnimation  animtor= new ViewAnimation(animationView);
    	animtor.setDuration(2000);
    	animtor.setRepeatCount(Animation.INFINITE);
    	bt.startAnimation(animtor);
	  }

	@Override
	protected void onStop() {
		try {
			mLogWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		super.onStop();
	}

	void onLoadFileClicked() {
	    animationView.cancelAnimation();
	    animationView.clearAnimation();
	    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
	    intent.setType("*/*");
	    intent.addCategory(Intent.CATEGORY_OPENABLE);

	    try {
	      startActivityForResult(Intent.createChooser(intent, "Select a JSON file"), RC_FILE);
	    } catch (android.content.ActivityNotFoundException ex) {
	      // Potentially direct the user to the Market with a Dialog
	      Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
	    }
	  }
	
	@Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
	    if (resultCode != Activity.RESULT_OK) {
	      return;
	    }
	    switch (requestCode) {
	    case RC_ASSET:
	    final String assetName = data.getStringExtra(EXTRA_ANIMATION_NAME);
        animationView.setImageAssetsFolder(assetFolders.get(assetName));
        LottieComposition.Factory.fromAssetFileName(this, assetName,
            new OnCompositionLoadedListener() {
              @Override
              public void onCompositionLoaded(LottieComposition composition) {
                setComposition(composition, assetName);
              }
            });
        break;
	      case RC_FILE:
	        onFileLoaded(data.getData());
	        break;
	    }
	  }
	
	private void onFileLoaded(final Uri uri) {
	    InputStream fis = null;
	    try {
	    	if(uri.getScheme().equals("file")){
	    		 fis = new FileInputStream(uri.getPath());
	    	}else if(uri.getScheme().equals("content")){
	    		fis = this.getContentResolver().openInputStream(uri);
	    	}
	    } catch (FileNotFoundException e) {
	      return;
	    }
	    LottieComposition.Factory.fromInputStream(this, fis, new OnCompositionLoadedListener() {
          @Override
          public void onCompositionLoaded(LottieComposition composition) {
            setComposition(composition, uri.getPath());
          }
        });
	}
	
//	private void onAssetLoaded(final Uri uri) {
//		  animationView.cancelAnimation();
//		  android.support.v4.app.DialogFragment assetFragment = ChooseAssetDialogFragment.newInstance();
//		  assetFragment.setTargetFragment(this, RC_ASSET);
//		  assetFragment.show(getFragmentManager(), "assets");
//	}
	
	private void setComposition(LottieComposition composition, String name) {
	    animationView.setComposition(composition);
	    animationView.loop(true);
	    if (animationView.getProgress() == 1f) {
            animationView.setProgress(0f);
          }
          animationView.resumeAnimation();
	  }

	@Override
	public void onClick(View v) {
		String filename = "";
		switch (v.getId()) {
		case R.id.anim0:
			filename = "bubble.json";
			animationView.setImageAssetsFolder(assetFolders
					.get("bubble.json"));
			break;
		case R.id.anim1:
			filename = "WeAccept.json";
			animationView.setImageAssetsFolder(assetFolders
					.get("WeAccept.json"));
			break;
		case R.id.anim2:
			filename = "lottiefiles.com - VR.json";
			break;
		case R.id.anim4:
			filename = "lottiefiles.com - Notifications.json";
			break;
		}
		final String assetName = filename;
		LottieComposition.Factory.fromAssetFileName(this, assetName,
				new OnCompositionLoadedListener() {
					@Override
					public void onCompositionLoaded(
							LottieComposition composition) {
						if (composition == null) {
							return;
						}
						setComposition(composition, assetName);
					}
				});
	}
}

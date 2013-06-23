package com.sensorcon.reducinggasmonitor;



import java.util.EventObject;

import com.sensorcon.sensordrone.Drone;
import com.sensorcon.sensordrone.Drone.DroneEventListener;
import com.sensorcon.sensordrone.Drone.DroneStatusListener;
import com.sensorcon.sdhelper.ConnectionBlinker;
import com.sensorcon.sdhelper.OnOffRunnable;
import com.sensorcon.sdhelper.SDBatteryStreamer;
import com.sensorcon.sdhelper.SDHelper;
import com.sensorcon.sdhelper.SDStreamer;

import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
// Don't let eclipse import android.widget.TableLayout.LayoutParams for your TableRows!
import android.widget.TableRow.LayoutParams;

/**
 * Reducing Gas Monitor for the Sensordrone
 * 
 * Built against Android-10
 * 
 * Build using SDAndroidLib 1.1.1
 * 
 * @author Sensorcon, Inc
 *
 */
@SuppressLint("NewApi")
public class MainActivity extends Activity {

	static String LAST_MAC = "LAST_MAC";
	static String DISABLE_INTRO = "DISABLE_INTRO";
	private SharedPreferences preferences;
	private int screenSize;
	private final int SMALL_SCREEN = 0;
	private final int NORMAL_SCREEN = 1;
	private final int LARGE_SCREEN = 2;
	private final int XLARGE_SCREEN = 3;
	private int api;
	private final int NEW_API = 0;
	private final int OLD_API = 1;
	/*
	 * Constants
	 */
	private final int WARMUP_COUNT = 6;
	private final int BASELINE_COUNT = 3;
	private final int TICK_FREQ_BASE = 1000;
	/*
	 * We put our Drone object in a class that extends Application so it
	 * can be accessed in multiple activities.
	 */
	protected DroneApplication droneApp;
	/*
	 * A class which store useful Alert Dialogs
	 */
	public AlertInfo myInfo;
	/*
	 * Variables for popup windows
	 */
	private PopupWindow popup;
	private TextView tv_popupTitle;
	private TextView tv_popupInfo;
	/*
	 * GUI variables
	 */
	private TextView tvConnectionStatus;
	private TextView tvConnectInfo;
	private TextView tvSensorValue;
	private ImageButton xButton;
	private ImageButton leftButton;
	private ImageButton rightButton;
	private ImageButton redButton;
	private ImageButton genButton;
	private ImageButton warningButton;
	private ImageView ledOn1;
	private ImageView ledOn2;
	private ImageView ledOn3;
	private ImageView ledOn4;
	private ImageView ledOn5;
	private ImageView ledOn6;
	private ImageView ledOn7;
	private ImageView ledOn8;
	private ImageView ledOn9;
	private LinearLayout ledOnLayout;
	private PreferencesStream pStream;
	private LayoutInflater inflater2;
	private LayoutInflater inflater3;
    private View layout2;
    private View layout3;
    private Button buttonBaseline;
    private RelativeLayout tickerLayout;
    private ImageView monitor;
    private LinearLayout infoRow;
    private ImageButton buttonLowOff;
    private ImageButton buttonMedOff;
    private ImageButton buttonHighOff;
    private ImageButton buttonLowOn;
    private ImageButton buttonMedOn;
    private ImageButton buttonHighOn;
	/*
	 * Program control variables
	 */
    private int mode;
	private boolean on;
	private boolean firstTime;
	private boolean suspendCount;
	private boolean modeChange;
	private boolean warmedUp;
	private float val;
	private float ratio;
	private int tickFrequency;
	private float ledStep = 20000;
	/*
	 * Tick sound variables
	 */
	private SoundPool tickSound;
	private int soundId;
	private boolean loaded;
	private AudioManager am;
	private boolean mute;
	/*
	 * Modes
	 */
	private final int TICKER_MODE = 0;
	private final int INFO_MODE = 1;
	/*
	 * Baseline calculation variables
	 */
	private float baseline;
	private float[] blValues = new float[WARMUP_COUNT];
	private float[] blResetValues = new float[BASELINE_COUNT];
	/*
	 * Countdown variables
	 */
	public boolean inCountdown1Mode = false;
	private boolean inBaselineMode = false;
	public PopupWindow warmUpWindow1;
	private PopupWindow baselineWindow;
	private int countdown1 = WARMUP_COUNT;
	private int countdown2 = BASELINE_COUNT;
	private TextView tv_countdown1;
	private TextView tv_countdown2;
	/*
	 * Handlers
	 */
	private Handler countdown1Handler = new Handler();
	private Handler countdown2Handler = new Handler();
	private Handler modeHandler = new Handler();
	private Handler setLEDsHandler = new Handler();
	private Handler tickHandler = new Handler();
	
	/*
	 * This is a lookup table to decide how many leds to light up in the ticker
	 */
	private float[] LUT = {1, (float)0.7, (float)0.6, (float)0.5, (float)0.4, (float)0.3, (float)0.2, (float)0.1, (float)0.09};
	
	/*
	 * Because Android will destroy and re-create things on events like orientation changes,
	 * we will need a way to store our objects and return them in such a case. 
	 * 
	 * A simple and straightforward way to do this is to create a class which has all of the objects
	 * and values we want don't want to get lost. When our orientation changes, it will reload our
	 * class, and everything will behave as normal! See onRetainNonConfigurationInstance in the code
	 * below for more information.
	 * 
	 * A lot of the GUI set up will be here, and initialized via the Constructor
	 */
	public final class Storage {
		
		// A ConnectionBLinker from the SDHelper Library
		public ConnectionBlinker myBlinker;

		// Our Listeners
		public DroneEventListener deListener;
		public DroneStatusListener dsListener;
		public String MAC = "";

		// An int[] that will hold the QS_TYPEs for our sensors of interest
		public int qsSensor;

		// Text to display
		public String[] sensorNames= {
				"Reducing Gas"
		};
		
		// Another object from the SDHelper library. It helps us set up our pseudo streaming
		public SDStreamer streamer;

		// We only want to notify of a low battery once,
		// but the event might be triggered multiple times.
		// We use this to try and show it only once
		public boolean lowbatNotify;

		// Our constructor to set up the GUI
		public Storage(Context context) {

			qsSensor = droneApp.myDrone.QS_TYPE_REDUCING_GAS;

			// This will Blink our Drone, once a second, Blue
			myBlinker = new ConnectionBlinker(droneApp.myDrone, 1000, 0, 0, 255);

			streamer = new SDStreamer(droneApp.myDrone, qsSensor);

			/*
			 * Let's set up our Drone Event Listener.
			 * 
			 * See adcMeasured for the general flow for when a sensor is measured.
			 * 
			 */
			deListener = new DroneEventListener() {

				@Override
				public void adcMeasured(EventObject arg0) {
					
				}

				@Override
				public void altitudeMeasured(EventObject arg0) {

				}

				@Override
				public void capacitanceMeasured(EventObject arg0) {

				}

				@Override
				public void connectEvent(EventObject arg0) {
					
					Editor prefEditor = preferences.edit();
					prefEditor.putString(LAST_MAC, droneApp.myDrone.lastMAC);
					prefEditor.commit();
					
					// Things to do when we connect to a Sensordrone
					quickMessage("Connected!");
					tvUpdate(tvConnectionStatus, "Connected to: " + droneApp.myDrone.lastMAC);
					// Flash teh LEDs green
					myHelper.flashLEDs(droneApp.myDrone, 3, 100, 0, 255, 0);
					// Turn on our blinker
					myBlinker.enable();
					myBlinker.run();
					// People don't need to know how to connect if they are already connected
					tvConnectInfo.setVisibility(TextView.INVISIBLE);
					// Notify if there is a low battery
					lowbatNotify = true;
					
                    suspendCount = false;
                    // Check to see if reconnect
                    if(on) {
                        // Enable our steamer
                        box.streamer.enable();
                        // Enable the sensor
                        droneApp.myDrone.quickEnable(box.qsSensor);
                    }
				}

				@Override
				public void connectionLostEvent(EventObject arg0) {

					/*
					 * Things to do if we think the connection has been lost.
					 */
					
					// If in the middle of a countdown, stop it
					suspendCount = true;
					
					// Turn off the blinker
					myBlinker.disable();

					// notify the user
					tvUpdate(tvConnectionStatus, "Connection Lost!"); 
					quickMessage("Connection lost! Trying to re-connect!");

					// Try to reconnect once, automatically
					if (droneApp.myDrone.btConnect(droneApp.myDrone.lastMAC)) {
						// A brief pause
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					} else {
						quickMessage("Re-connect failed");
						suspendCount = false;
						doOnDisconnect();
					}
				}

				@Override
				public void customEvent(EventObject arg0) {

				}

				@Override
				public void disconnectEvent(EventObject arg0) {
					// notify the user
					quickMessage("Disconnected!");	
					tvConnectionStatus.setText("Disconnected");
				}

				@Override
				public void oxidizingGasMeasured(EventObject arg0) {
				}

				@Override
				public void reducingGasMeasured(EventObject arg0) {
					val = droneApp.myDrone.reducingGas_Ohm ;
					
					tvUpdate(tvSensorValue, String.format("%.0f", val/1000) + " KOhms");
					streamer.streamHandler.postDelayed(streamer, droneApp.streamingRate);
				}

				@Override
				public void humidityMeasured(EventObject arg0) {
				
				}

				@Override
				public void i2cRead(EventObject arg0) {

				}

				@Override
				public void irTemperatureMeasured(EventObject arg0) {
					
				}

				@Override
				public void precisionGasMeasured(EventObject arg0) {
					
				}

				@Override
				public void pressureMeasured(EventObject arg0) {
					
				}

				@Override
				public void rgbcMeasured(EventObject arg0) {

				}

				@Override
				public void temperatureMeasured(EventObject arg0) {
				}

				@Override
				public void uartRead(EventObject arg0) {

				}

				@Override
				public void unknown(EventObject arg0) {

				}

				@Override
				public void usbUartRead(EventObject arg0) {

				}
			};


			/*
			 * Set up our status listener
			 * 
			 * see adcStatus for the general flow for sensors.
			 */
			dsListener = new DroneStatusListener() {


				@Override
				public void adcStatus(EventObject arg0) {
					
				}

				@Override
				public void altitudeStatus(EventObject arg0) {
					
				}

				@Override
				public void batteryVoltageStatus(EventObject arg0) {
//					// This is triggered when the battery voltage has been measured.
//					String bVoltage = String.format("%.2f", droneApp.myDrone.batteryVoltage_Volts) + " V";
//					tvUpdate(bvValue, bVoltage);
//					// Set up the next measurement
//					bvStreamer.streamHandler.postDelayed(bvStreamer, droneApp.streamingRate);
				}

				@Override
				public void capacitanceStatus(EventObject arg0) {
					
				}

				@Override
				public void chargingStatus(EventObject arg0) {


				}

				@Override
				public void customStatus(EventObject arg0) {


				}

				@Override
				public void humidityStatus(EventObject arg0) {
				
				}

				@Override
				public void irStatus(EventObject arg0) {
					
				}

				@Override
				public void lowBatteryStatus(EventObject arg0) {
					// If we get a low battery, notify the user
					// and disconnect
					
					// This might trigger a lot (making a call the the LEDS will trigger it,
					// so the myBlinker will trigger this once a second.
					// calling myBlinker.disable() even sets LEDS off, which will trigger it...
					if (lowbatNotify) {
						lowbatNotify = false; // Set true again in connectEvent
						myBlinker.disable();
						doOnDisconnect(); // run our disconnect routine
						// Notify the user
						tvUpdate(tvConnectionStatus, "Low Battery: Disconnected!");
						myInfo.lowBattery();
					}

				}

				@Override
				public void oxidizingGasStatus(EventObject arg0) {
				}

				@Override
				public void precisionGasStatus(EventObject arg0) {
				}

				@Override
				public void pressureStatus(EventObject arg0) {
				
				}

				@Override
				public void reducingGasStatus(EventObject arg0) {
					if (droneApp.myDrone.reducingGasStatus) {
						streamer.run();
					}
				}

				@Override
				public void rgbcStatus(EventObject arg0) {
					
				}

				@Override
				public void temperatureStatus(EventObject arg0) {				
				}

				@Override
				public void unknownStatus(EventObject arg0) {
				}
			};
			

			/*
			 * Register the listeners
			 * 
			 * This is done once on create. Disable them in onDestroy
			 */
			droneApp.myDrone.registerDroneEventListener(deListener);
			droneApp.myDrone.registerDroneStatusListener(dsListener);
			

		} // Constructor

	}

	/*
	 * Our program will need one of these classes
	 */
	public Storage box;

	/*
	 * We use this so we can restore our data. Note that this has been deprecated as of 
	 * Android API 13. The official Android Developer's recommendation is 
	 * if you are targeting HONEYCOMB or later, consider instead using a 
	 * Fragment with Fragment.setRetainInstance(boolean)
	 * (Also available via the android-support libraries for older versions)
	 */
	@Override
	public Storage onRetainNonConfigurationInstance() {
		
		// Make a new Storage object from our old data
		Storage bin = box;
		// Return our old data
		return bin;
	}

	/*
	 * We will use some stuff from our Sensordrone Helper library
	 */
	public SDHelper myHelper = new SDHelper();
	
	@Override
	public void onDestroy() {
		super.onDestroy();

		if (isFinishing()) {
			// Try and nicely shut down
			doOnDisconnect();
			// A brief delay
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			// Unregister the listener
			droneApp.myDrone.unregisterDroneEventListener(box.deListener);
			droneApp.myDrone.unregisterDroneStatusListener(box.dsListener);

		} else { 
			//It's an orientation change.
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		setLEDsHandler.removeCallbacksAndMessages(null);
		tickHandler.removeCallbacks(null);
		
		mute = true;
	}

	@Override
	public void onResume() {
		super.onResume();
		
		mute = false;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Check to see if API supports swipe views and fragments
		if (android.os.Build.VERSION.SDK_INT < 13) {
		    api = OLD_API;
		} else {
			api = NEW_API;
		}
				
		if(api == NEW_API) {
			getWindow().requestFeature(Window.FEATURE_ACTION_BAR); // Add this line
		}
		setContentView(R.layout.main);
		if(api == NEW_API) {
		    ActionBar actionBar = getActionBar();
		    actionBar.show();
		}

		// Get out Application so we have access to our Drone
		droneApp = (DroneApplication)getApplication();

		// Set up out AlertInfo
		myInfo = new AlertInfo(this);
		
		/*
		 * If we have destroyed and recreated our activity, due to something like
		 * and orientation change, this will restore it.
		 * 
		 * We want to restore it, because our Drone object remembers important things...
		 * like if it was connected or not.
		 */
		box = new Storage(this);
		
		/*
		 * Set up all of the gui stuff
		 */
		tvConnectionStatus = (TextView)findViewById(R.id.connectionStatus);
		tvConnectInfo = (TextView)findViewById(R.id.connectInfo);
		tvSensorValue = (TextView)findViewById(R.id.tvSensorValue);
		popup = new PopupWindow(this);
		leftButton = (ImageButton)findViewById(R.id.left_button);
		rightButton = (ImageButton)findViewById(R.id.right_button);
		redButton = (ImageButton)findViewById(R.id.red_button);
		genButton = (ImageButton)findViewById(R.id.gen_button);
		warningButton = (ImageButton)findViewById(R.id.warning_button);
		ledOn1 = (ImageView)findViewById(R.id.led_on1);
		ledOn2 = (ImageView)findViewById(R.id.led_on2);
		ledOn3 = (ImageView)findViewById(R.id.led_on3);
		ledOn4 = (ImageView)findViewById(R.id.led_on4);
		ledOn5 = (ImageView)findViewById(R.id.led_on5);
		ledOn6 = (ImageView)findViewById(R.id.led_on6);
		ledOn7 = (ImageView)findViewById(R.id.led_on7);
		ledOn8 = (ImageView)findViewById(R.id.led_on8);
		ledOn9 = (ImageView)findViewById(R.id.led_on9);
		buttonBaseline = (Button)findViewById(R.id.zero_button);
		tickerLayout = (RelativeLayout)findViewById(R.id.ticker_layout);
		ledOnLayout = (LinearLayout)findViewById(R.id.led_group2);
		infoRow = (LinearLayout)findViewById(R.id.info_row);
		buttonLowOff = (ImageButton)findViewById(R.id.rb_low);
		buttonMedOff = (ImageButton)findViewById(R.id.rb_med);
		buttonHighOff = (ImageButton)findViewById(R.id.rb_high);
		buttonLowOn = (ImageButton)findViewById(R.id.rb_low_on);
		buttonMedOn = (ImageButton)findViewById(R.id.rb_med_on);
		buttonHighOn = (ImageButton)findViewById(R.id.rb_high_on);
		monitor = (ImageView)findViewById(R.id.monitor_bg);
		
		/*
		 * Info mode stuff is invisible
		 */
		ledOn1.setVisibility(View.INVISIBLE);
		ledOn2.setVisibility(View.INVISIBLE);
		ledOn3.setVisibility(View.INVISIBLE);
		ledOn4.setVisibility(View.INVISIBLE);
		ledOn5.setVisibility(View.INVISIBLE);
		ledOn6.setVisibility(View.INVISIBLE);
		ledOn7.setVisibility(View.INVISIBLE);
		ledOn8.setVisibility(View.INVISIBLE);
		ledOn9.setVisibility(View.INVISIBLE);
//		ledOnLayout.setVisibility(View.INVISIBLE);
		infoRow.setVisibility(View.INVISIBLE);
		buttonMedOn.setVisibility(View.INVISIBLE);
		buttonHighOn.setVisibility(View.INVISIBLE);
		
		// Initialize SharedPreferences
		preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		
		// Get screen size
		if((getResources().getConfiguration().screenLayout & 
			    Configuration.SCREENLAYOUT_SIZE_MASK) == 
			        Configuration.SCREENLAYOUT_SIZE_SMALL) {
			    screenSize = SMALL_SCREEN;
		}
		else if((getResources().getConfiguration().screenLayout & 
			    Configuration.SCREENLAYOUT_SIZE_MASK) == 
			        Configuration.SCREENLAYOUT_SIZE_NORMAL) {
			    screenSize = NORMAL_SCREEN;
		}
		else if((getResources().getConfiguration().screenLayout & 
			    Configuration.SCREENLAYOUT_SIZE_MASK) == 
			        Configuration.SCREENLAYOUT_SIZE_LARGE) {
			    screenSize = LARGE_SCREEN;
		}
		else if((getResources().getConfiguration().screenLayout & 
			    Configuration.SCREENLAYOUT_SIZE_MASK) == 
			        Configuration.SCREENLAYOUT_SIZE_XLARGE) {
			    screenSize = XLARGE_SCREEN;
		}
		else {
			screenSize = NORMAL_SCREEN;
		}
		
		on = false;
		firstTime = true;
		suspendCount = false;
		modeChange = false;
		warmedUp = false;
		mode = TICKER_MODE;
		val = 0;
		tickFrequency = 1000;
		mute = false;
		
		leftButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                leftButtonPressed();
            }   
		});
		
		rightButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                rightButtonPressed();
            }   
		});
		
		LayoutInflater inflater1 = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		View layout1 = inflater1.inflate(R.layout.popup,
				(ViewGroup) findViewById(R.id.popup_element));

//		if(screenSize == SMALL_SCREEN) {
//			popup = new PopupWindow(
//					layout1, 
//					250, 
//					LayoutParams.WRAP_CONTENT, 
//					true);
//		}
//		else if(screenSize == NORMAL_SCREEN) {
//			popup = new PopupWindow(
//					layout1, 
//					375, 
//					LayoutParams.WRAP_CONTENT, 
//					true);
//		}
//		else if(screenSize == LARGE_SCREEN) {
//			popup = new PopupWindow(
//					layout1, 
//					550, 
//					LayoutParams.WRAP_CONTENT, 
//					true);
//		}
//		else if(screenSize == XLARGE_SCREEN) {
//			popup = new PopupWindow(
//					layout1, 
//					650, 
//					LayoutParams.WRAP_CONTENT, 
//					true);
//		}
		
		int w;
		
		if(api == OLD_API) {
			Display display = getWindowManager().getDefaultDisplay();
			w = display.getWidth();
		}
		else {
			Display display = getWindowManager().getDefaultDisplay();
			Point size = new Point();
			display.getSize(size);
			w = size.x;
		}
		
		popup = new PopupWindow(
				layout1, 
				w - 50, 
				LayoutParams.WRAP_CONTENT, 
				true);
				
		popup.setBackgroundDrawable(new BitmapDrawable());
		
		tv_popupTitle = (TextView)layout1.findViewById(R.id.popupTitle);
		tv_popupInfo = (TextView)layout1.findViewById(R.id.popupInfo);
		
		buttonLowOff.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setLowSensitivity();
			}	
		});
		
		buttonMedOff.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setMedSensitivity();
			}	
		});
		
		buttonHighOff.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setHighSensitivity();
			}	
		});
		
		buttonBaseline.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if(warmedUp) {
					resetBaseline();
				}
			}	
		});
		
		xButton = (ImageButton)layout1.findViewById(R.id.xButton);
		xButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				popup.dismiss();
			}	
		});
		
		redButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showRedPopup();
			}		
		});	
		
		genButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showGenPopup();
			}		
		});
		
		warningButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showWarningPopup();
			}		
		});	
		
		/*
		 * Set up warmup window
		 */
		inflater2 = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		layout2 = inflater2.inflate(R.layout.warmup,
				(ViewGroup) findViewById(R.id.warmup_element));
		
		tv_countdown1 = (TextView)layout2.findViewById(R.id.countdown);
		
		/*
		 * Set up baseline window
		 */
		inflater3 = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		layout3 = inflater3.inflate(R.layout.baseline,
				(ViewGroup) findViewById(R.id.baseline_element));
		
		tv_countdown2 = (TextView)layout3.findViewById(R.id.bl_countdown);
		
		tickSound = new SoundPool(10, AudioManager.STREAM_ALARM, 0);
		tickSound.setOnLoadCompleteListener(new OnLoadCompleteListener() {
			@Override
			public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
				loaded = true;
			}
		});
		soundId = tickSound.load(this, R.raw.tick, 1);
		am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		// Check to see if user still wants intro screen to show
		pStream = new PreferencesStream();
		pStream.initFile(this);
		String[] preferences = new String[1];
		preferences = pStream.readPreferences();
		
		if(!preferences[0].equals("DISABLE INTRO")){
			showIntroDialog();
		}
	
		warmUpWindow1 = new PopupWindow(this);
	}

	/**
	 * Starts the baseline count
	 */
	public void resetBaseline() {
		int w = monitor.getWidth();
		int h = monitor.getHeight();
	
		baselineWindow = new PopupWindow(layout3, w, h, true);
		baselineWindow.setOutsideTouchable(true);
		baselineWindow.setFocusable(false);
		
		showBaselineWindow(h);
		countdown2Handler.post(countdown2Runnable);
	}
	
	/**
	 * What to do when left button is pressed
	 */
	public void leftButtonPressed() {
		if(droneApp.myDrone.isConnected) {
			if(!on) {
				// Enable our steamer
				box.streamer.enable();
				// Enable the sensor
				droneApp.myDrone.quickEnable(box.qsSensor);
				
				on = true;
				
				int w = tvSensorValue.getWidth();
				int h = tvSensorValue.getHeight();
				
				if(firstTime) {		
					firstTime = false;
					warmUpWindow1 = new PopupWindow(layout2, w, h, true);
					warmUpWindow1.setOutsideTouchable(true);
					warmUpWindow1.setFocusable(false);
				}
				
				if(warmedUp) {
					warmedUp = false;
				}
				
				showWarmupWindow(h);
				countdown1Handler.post(countdown1Runnable);
			} else {
				resetAllOperations();
			}
		}
	}
	
	/**
	 * What to do when right button is pressed
	 */
	public void rightButtonPressed() {
		
		// Only register a click if the sensor is enabled
		if(droneApp.myDrone.isConnected) {
			if (on) {
				if(!inCountdown1Mode) {
					Intent myIntent = new Intent(getApplicationContext(), GraphActivity.class);
					myIntent.putExtra("SensorName", "Reducing Gas");
					myIntent.putExtra("quickInt", box.qsSensor);
					startActivity(myIntent);
				}
			} else {
				//
			}
		}
	}
	
	/**
	 * Loads the dialog shown at startup
	 */
	public void showIntroDialog() {

		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setCancelable(false);
		alert.setTitle("Introduction").setMessage(R.string.intro);
		alert.setNegativeButton("Don't Show Again", new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int which) { 
		            pStream.disableIntroDialog();
		        }
		     })
		    .setPositiveButton("Okay", new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int which) { 
		            // do nothing
		        }
		     }).show();
	}
	
	/**
	 * Performs a single tick
	 * 
	 * @return	True if successful
	 */
	public boolean tick() {
		float volume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
		float max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		volume = volume/max;
		
		if(loaded) {
			tickSound.play(soundId, volume, volume, 1, 0, 1f);
			return true;
		}
		else {
			return false;
		}
	}
	
	public void showWarmupWindow(int h) {
		inCountdown1Mode = true;
		warmUpWindow1.showAsDropDown(tvSensorValue, 0, -h);
	}
	
	public void showBaselineWindow(int h) {
		inBaselineMode = true;
		baselineWindow.showAsDropDown(monitor, 0, -h);
	}
	
	/**
	 * Makes informative popup appear
	 */
	public void showRedPopup() {
		tv_popupTitle.setText(R.string.title1);
		tv_popupInfo.setText(R.string.description1);
		
		// The code below assumes that the root container has an id called 'main'
		 popup.showAtLocation(findViewById(R.id.anchor), Gravity.CENTER, 0, 0);
	}
	
	/**
	 * Makes informative popup appear
	 */
	public void showGenPopup() {
		tv_popupTitle.setText(R.string.title3);
		tv_popupInfo.setText(R.string.description3);
		
		// The code below assumes that the root container has an id called 'main'
		 popup.showAtLocation(findViewById(R.id.anchor), Gravity.CENTER, 0, 0);
	}
	
	/**
	 * Makes informative popup appear
	 */
	public void showWarningPopup() {
		tv_popupTitle.setText("Power Warning");
		tv_popupInfo.setText(R.string.warning);
		
		// The code below assumes that the root container has an id called 'main'
		 popup.showAtLocation(findViewById(R.id.anchor), Gravity.CENTER, 0, 0);
	}
	
	/**
	 * This function resets any current operations on a disconnect or turning off the sensor
	 */
	public void resetAllOperations() {
		/*
		 * Check to see if we are in any countdown modes
		 */
		if(inCountdown1Mode) {
			warmUpWindow1.dismiss();
		}		
		if(inBaselineMode) {
			baselineWindow.dismiss();
		}
		
		/**
		 * Turn off any red leds
		 */
		ledOn1.setVisibility(View.INVISIBLE);
		ledOn2.setVisibility(View.INVISIBLE);
		ledOn3.setVisibility(View.INVISIBLE);
		ledOn4.setVisibility(View.INVISIBLE);
		ledOn5.setVisibility(View.INVISIBLE);
		ledOn6.setVisibility(View.INVISIBLE);
		ledOn7.setVisibility(View.INVISIBLE);
		ledOn8.setVisibility(View.INVISIBLE);
		ledOn9.setVisibility(View.INVISIBLE);
		
		/*
		 * Reset any program flow variables
		 */
		inCountdown1Mode = false;
		inBaselineMode = false;
		on = false;
		modeChange = false;
		warmedUp = false;
		
		/*
		 * Reset counts
		 */
		countdown1 = WARMUP_COUNT;
		countdown2 = BASELINE_COUNT;
		tvUpdate(tvSensorValue, "--");
		
		countdown1Handler.removeCallbacksAndMessages(null);
		countdown2Handler.removeCallbacksAndMessages(null);
		modeHandler.removeCallbacksAndMessages(null);
		tickHandler.removeCallbacksAndMessages(null);
		setLEDsHandler.removeCallbacksAndMessages(null);
		
		// Stop taking measurements
		box.streamer.disable();
		// Disable the sensor
		droneApp.myDrone.quickDisable(box.qsSensor);
	}
	
	/*
	 * Controls timing thread for countdown
	 */
	public Runnable countdown1Runnable = new Runnable() {

		@Override
		public void run() {
			
			if(inCountdown1Mode) {
				if(!suspendCount) {
					countdown1--;
				}
				
				// Average for the last 15 seconds of count down
				if(countdown1 == 0) {
					inCountdown1Mode = false;
					countdown1 = WARMUP_COUNT;
					warmUpWindow1.dismiss();
					warmedUp = true;
					
					if(mode == TICKER_MODE) {
						tickHandler.post(tickRunnable);
						setLEDsHandler.post(setLEDsRunnable);
					}
					
					blValues[0] = val;
					Log.d("chris", "blValues[0]: " + Float.toString(blValues[0]));
					
					float sum = 0;
					for(int i = 0; i < blValues.length; i++) {
						sum += blValues[i];
					}
					
					Log.d("chris", "Sum: " + Float.toString(sum));
					
					float ave = sum/(float)blValues.length;
					
					Log.d("chris", "Average: " + Float.toString(ave));
					
					baseline = ave;
					
					countdown1Handler.removeCallbacksAndMessages(null);
				}
				else {
					blValues[countdown1] = val;
					tv_countdown1.setText(Integer.toString(countdown1));
					Log.d("chris", "blValues[" + Integer.toString(countdown1) + "]: " + Float.toString(blValues[countdown1]));
					countdown1Handler.postDelayed(this, 1000);
				}
				
			}
		}
	};
	
	/*
	 * Controls timing thread for countdown
	 */
	public Runnable countdown2Runnable = new Runnable() {

		@Override
		public void run() {
			
			if(inBaselineMode) {
				if(!suspendCount) {
					countdown2--;
				}
				
				// Average for the last 15 seconds of count down
				if(countdown2 == 0) {
					inBaselineMode = false;
					countdown2 = BASELINE_COUNT;
					baselineWindow.dismiss();
					
					blResetValues[0] = val;
					Log.d("chris", "blResetValues[0]: " + Float.toString(blResetValues[0]));
					
					float sum = 0;
					for(int i = 0; i < blResetValues.length; i++) {
						sum += blResetValues[i];
					}
					
					Log.d("chris", "Sum: " + Float.toString(sum));
					
					float ave = sum/(float)blResetValues.length;
					
					Log.d("chris", "Average: " + Float.toString(ave));
					
					baseline = ave;
					
					countdown2Handler.removeCallbacksAndMessages(null);
				}
				else {
					blResetValues[countdown2] = val;
					Log.d("chris", "blResetValues[" + Integer.toString(countdown2) + "]: " + Float.toString(blResetValues[countdown2]));
					tv_countdown2.setText(Integer.toString(countdown2));
					countdown2Handler.postDelayed(this, 1000);
				}
				
			}
		}
	};
	
	public Runnable changeModeRunnable = new Runnable() {

		@Override
		public void run() {
			
			if(modeChange == true) {
				if(mode == INFO_MODE) {
					setLEDsHandler.removeCallbacksAndMessages(null);
					tickHandler.removeCallbacksAndMessages(null);
					
					infoRow.setVisibility(View.VISIBLE);
					
					if(inBaselineMode) {
						baselineWindow.dismiss();
						countdown2 = BASELINE_COUNT;
						setLEDsHandler.post(setLEDsRunnable);
					}
					
					tickerLayout.setVisibility(View.INVISIBLE);
					ledOnLayout.setVisibility(View.INVISIBLE);
				}
				else {
					if(warmedUp) {
						setLEDsHandler.post(setLEDsRunnable);
						tickHandler.post(tickRunnable);
					}
					
					ledOnLayout.setVisibility(View.VISIBLE);
					
					infoRow.setVisibility(View.INVISIBLE);
					
					tickerLayout.setVisibility(View.VISIBLE);
				}
				modeChange = false;
			}
		}
	};
	
	public Runnable setLEDsRunnable = new Runnable() {

		@Override
		public void run() {
			
			ratio = val/baseline;
			Log.d("chris","Val: " + Float.toString(val));
			Log.d("chris","Baseline: " + Float.toString(baseline));
			Log.d("chris","Ratio: " + Float.toString(ratio));
			
			if(mode == TICKER_MODE) {
				if(warmedUp) {
					if(ratio > LUT[1]) {
						ledOn1.setVisibility(View.VISIBLE);
						ledOn2.setVisibility(View.INVISIBLE);
						ledOn3.setVisibility(View.INVISIBLE);
						ledOn4.setVisibility(View.INVISIBLE);
						ledOn5.setVisibility(View.INVISIBLE);
						ledOn6.setVisibility(View.INVISIBLE);
						ledOn7.setVisibility(View.INVISIBLE);
						ledOn8.setVisibility(View.INVISIBLE);
						ledOn9.setVisibility(View.INVISIBLE);
						
						tickFrequency = TICK_FREQ_BASE;
					}
					else if((ratio < LUT[1]) && (ratio > LUT[2])) {
						ledOn1.setVisibility(View.VISIBLE);
						ledOn2.setVisibility(View.VISIBLE);
						ledOn3.setVisibility(View.INVISIBLE);
						ledOn4.setVisibility(View.INVISIBLE);
						ledOn5.setVisibility(View.INVISIBLE);
						ledOn6.setVisibility(View.INVISIBLE);
						ledOn7.setVisibility(View.INVISIBLE);
						ledOn8.setVisibility(View.INVISIBLE);
						ledOn9.setVisibility(View.INVISIBLE);
						
						tickFrequency = 800;
					}
					else if((ratio < LUT[2]) && (ratio > LUT[3])) {
						ledOn1.setVisibility(View.VISIBLE);
						ledOn2.setVisibility(View.VISIBLE);
						ledOn3.setVisibility(View.VISIBLE);
						ledOn4.setVisibility(View.INVISIBLE);
						ledOn5.setVisibility(View.INVISIBLE);
						ledOn6.setVisibility(View.INVISIBLE);
						ledOn7.setVisibility(View.INVISIBLE);
						ledOn8.setVisibility(View.INVISIBLE);
						ledOn9.setVisibility(View.INVISIBLE);
						
						tickFrequency = 600;
					}
					else if((ratio < LUT[3]) && (ratio > LUT[4])) {
						ledOn1.setVisibility(View.VISIBLE);
						ledOn2.setVisibility(View.VISIBLE);
						ledOn3.setVisibility(View.VISIBLE);
						ledOn4.setVisibility(View.VISIBLE);
						ledOn5.setVisibility(View.INVISIBLE);
						ledOn6.setVisibility(View.INVISIBLE);
						ledOn7.setVisibility(View.INVISIBLE);
						ledOn8.setVisibility(View.INVISIBLE);
						ledOn9.setVisibility(View.INVISIBLE);
						
						tickFrequency = 400;
					}
					else if((ratio < LUT[4]) && (ratio > LUT[5])) {
						ledOn1.setVisibility(View.VISIBLE);
						ledOn2.setVisibility(View.VISIBLE);
						ledOn3.setVisibility(View.VISIBLE);
						ledOn4.setVisibility(View.VISIBLE);
						ledOn5.setVisibility(View.VISIBLE);
						ledOn6.setVisibility(View.INVISIBLE);
						ledOn7.setVisibility(View.INVISIBLE);
						ledOn8.setVisibility(View.INVISIBLE);
						ledOn9.setVisibility(View.INVISIBLE);
						
						tickFrequency = 200;
					}
					else if((ratio < LUT[5]) && (ratio > LUT[6])) {
						ledOn1.setVisibility(View.VISIBLE);
						ledOn2.setVisibility(View.VISIBLE);
						ledOn3.setVisibility(View.VISIBLE);
						ledOn4.setVisibility(View.VISIBLE);
						ledOn5.setVisibility(View.VISIBLE);
						ledOn6.setVisibility(View.VISIBLE);
						ledOn7.setVisibility(View.INVISIBLE);
						ledOn8.setVisibility(View.INVISIBLE);
						ledOn9.setVisibility(View.INVISIBLE);
						
						tickFrequency = 100;
					}
					else if((ratio < LUT[6]) && (ratio > LUT[7])) {
						ledOn1.setVisibility(View.VISIBLE);
						ledOn2.setVisibility(View.VISIBLE);
						ledOn3.setVisibility(View.VISIBLE);
						ledOn4.setVisibility(View.VISIBLE);
						ledOn5.setVisibility(View.VISIBLE);
						ledOn6.setVisibility(View.VISIBLE);
						ledOn7.setVisibility(View.VISIBLE);
						ledOn8.setVisibility(View.INVISIBLE);
						ledOn9.setVisibility(View.INVISIBLE);
						
						tickFrequency = 75;
					}
					else if((ratio < LUT[7]) && (ratio > LUT[8])) {
						ledOn1.setVisibility(View.VISIBLE);
						ledOn2.setVisibility(View.VISIBLE);
						ledOn3.setVisibility(View.VISIBLE);
						ledOn4.setVisibility(View.VISIBLE);
						ledOn5.setVisibility(View.VISIBLE);
						ledOn6.setVisibility(View.VISIBLE);
						ledOn7.setVisibility(View.VISIBLE);
						ledOn8.setVisibility(View.VISIBLE);
						ledOn9.setVisibility(View.INVISIBLE);
						
						tickFrequency = 60;
					}
					else if(ratio < LUT[8]) {
						ledOn1.setVisibility(View.VISIBLE);
						ledOn2.setVisibility(View.VISIBLE);
						ledOn3.setVisibility(View.VISIBLE);
						ledOn4.setVisibility(View.VISIBLE);
						ledOn5.setVisibility(View.VISIBLE);
						ledOn6.setVisibility(View.VISIBLE);
						ledOn7.setVisibility(View.VISIBLE);
						ledOn8.setVisibility(View.VISIBLE);
						ledOn9.setVisibility(View.VISIBLE);
						
						tickFrequency = 50;
					}
					else {
						ledOn1.setVisibility(View.VISIBLE);
						ledOn2.setVisibility(View.INVISIBLE);
						ledOn3.setVisibility(View.INVISIBLE);
						ledOn4.setVisibility(View.INVISIBLE);
						ledOn5.setVisibility(View.INVISIBLE);
						ledOn6.setVisibility(View.INVISIBLE);
						ledOn7.setVisibility(View.INVISIBLE);
						ledOn8.setVisibility(View.INVISIBLE);
						ledOn9.setVisibility(View.INVISIBLE);
						
						tickFrequency = 1000;
					}
				}
				else {
					ledOn1.setVisibility(View.INVISIBLE);
					ledOn2.setVisibility(View.INVISIBLE);
					ledOn3.setVisibility(View.INVISIBLE);
					ledOn4.setVisibility(View.INVISIBLE);
					ledOn5.setVisibility(View.INVISIBLE);
					ledOn6.setVisibility(View.INVISIBLE);
					ledOn7.setVisibility(View.INVISIBLE);
					ledOn8.setVisibility(View.INVISIBLE);
					ledOn9.setVisibility(View.INVISIBLE);
				}
				
				setLEDsHandler.postDelayed(this, 1000);
			}
			else {
				ledOn1.setVisibility(View.INVISIBLE);
				ledOn2.setVisibility(View.INVISIBLE);
				ledOn3.setVisibility(View.INVISIBLE);
				ledOn4.setVisibility(View.INVISIBLE);
				ledOn5.setVisibility(View.INVISIBLE);
				ledOn6.setVisibility(View.INVISIBLE);
				ledOn7.setVisibility(View.INVISIBLE);
				ledOn8.setVisibility(View.INVISIBLE);
				ledOn9.setVisibility(View.INVISIBLE);
				setLEDsHandler.removeCallbacksAndMessages(null);
			}
		}
	};
	
	public Runnable tickRunnable = new Runnable() {

		@Override
		public void run() {
			if(mode == TICKER_MODE) {
				if(warmedUp) {
					if(!mute) {
						tick();
					}
				}
				tickHandler.postDelayed(this, tickFrequency);
			}
			else {
				tickHandler.removeCallbacksAndMessages(null);
			}
		}
	};
	
	public int pxToDp(int px) {
		
		float d = this.getResources().getDisplayMetrics().density;
		int ret = (int)(px * d); // margin in pixels
		return ret;
	}
	
	public void onRadioButtonClicked(View view) {
		
		boolean checked = ((RadioButton)view).isChecked();
		
		switch(view.getId()) {
		case R.id.rb_info_mode:
			if(checked) {
				mode = TICKER_MODE;
				modeChange = true;
				modeHandler.post(changeModeRunnable);
			}
			break;
		case R.id.rb_ticker_mode:
			if(checked) {
				mode = INFO_MODE;
				modeChange = true;
				modeHandler.post(changeModeRunnable);
			}
			break;
		}
	}

	/**
	 * CUSTOM RADIO BUTTON CONTROL
	 */
	private void setLowSensitivity() {
		buttonLowOn.setVisibility(View.VISIBLE);
		buttonMedOn.setVisibility(View.INVISIBLE);
		buttonHighOn.setVisibility(View.INVISIBLE);
		
		LUT[0] = 1;
		LUT[1] = (float)0.7;
		LUT[2] = (float)0.6;
		LUT[3] = (float)0.5;
		LUT[4] = (float)0.4;
		LUT[5] = (float)0.3;
		LUT[6] = (float)0.2;
		LUT[7] = (float)0.1;
		LUT[8] = (float)0.09;
	}
	
	private void setMedSensitivity() {
		buttonLowOn.setVisibility(View.INVISIBLE);
		buttonMedOn.setVisibility(View.VISIBLE);
		buttonHighOn.setVisibility(View.INVISIBLE);
		
		LUT[0] = 1;
		LUT[1] = (float)0.85;
		LUT[2] = (float)0.8;
		LUT[3] = (float)0.75;
		LUT[4] = (float)0.7;
		LUT[5] = (float)0.65;
		LUT[6] = (float)0.6;
		LUT[7] = (float)0.55;
		LUT[8] = (float)0.545;
	}
	
	private void setHighSensitivity() {
		buttonLowOn.setVisibility(View.INVISIBLE);
		buttonMedOn.setVisibility(View.INVISIBLE);
		buttonHighOn.setVisibility(View.VISIBLE);
		
		LUT[0] = 1;
		LUT[1] = (float)0.925;
		LUT[2] = (float)0.9;
		LUT[3] = (float)0.875;
		LUT[4] = (float)0.85;
		LUT[5] = (float)0.825;
		LUT[6] = (float)0.8;
		LUT[7] = (float)0.775;
		LUT[8] = (float)0.7725;
	}
	
	/*
	 * A function to display Toast Messages.
	 * 
	 * By having it run on the UI thread, we will be sure that the message
	 * is displays no matter what thread tries to use it.
	 */
	public void quickMessage(final String msg) {
		this.runOnUiThread(new Runnable() {

			@Override
			public void run() {
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
			}
		});

	}

	/*
	 * A function to update a TextView
	 * 
	 * We have it run on the UI thread to make sure it safely updates.
	 */
	public void tvUpdate(final TextView tv, final String msg) {
		this.runOnUiThread(new Runnable() {

			@Override
			public void run() {
				tv.setText(msg);
			}
		});
	}


	/**
	 * Things to do when we disconnect
	 */
	public void doOnDisconnect() {
		// Shut off any sensors that are on
		MainActivity.this.runOnUiThread(new Runnable() {

			@Override
			public void run() {

				resetAllOperations();
				
				// Turn off myBlinker
				box.myBlinker.disable();
				
				// Make sure the LEDs go off
				if (droneApp.myDrone.isConnected) {
					droneApp.myDrone.setLEDs(0, 0, 0);
				}
				
//				// Don't forget the battery voltage button
//				if (box.bvToggle.isChecked()){
//					box.bvToggle.performClick();
//				}

				// Only try and disconnect if already connected
				if (droneApp.myDrone.isConnected) {
					droneApp.myDrone.disconnect();
				}

				// Remind people how to connect
				tvConnectInfo.setVisibility(TextView.VISIBLE);
			}
		});
	}

	/*
	 * We will handle connect and disconnect in the menu.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		switch(item.getItemId()){
		case R.id.menuConnect:
			if (!droneApp.myDrone.isConnected) {
				String prefLastMAC = preferences.getString(LAST_MAC, "");
				// This option is used to re-connect to the last connected MAC
				if (!prefLastMAC.equals("")) {
					if (!droneApp.myDrone.btConnect(prefLastMAC)) {
						myInfo.connectFail();
					}
				} else {
					// Notify the user if no previous MAC was found.
					quickMessage("Last MAC not found... Please scan");
				} 
			} else {
				quickMessage("Already connected...");
			}
			break;
		case R.id.menuDisconnect:
			// Only disconnect if it's connected
			if (droneApp.myDrone.isConnected) {
				// Run our routine of things to do on disconnect
				doOnDisconnect();
			} else {
				quickMessage("Not currently connected..");
			}
			break;
		case R.id.menuScan:
			if (!droneApp.myDrone.isConnected) {
				myHelper.scanToConnect(droneApp.myDrone, MainActivity.this , this, false);
			} else {
				quickMessage("Please disconnect first");
			}
			break;
			
			//Help Menu items
		case R.id.instructions:
			break;
		}
		return true;
	}
}

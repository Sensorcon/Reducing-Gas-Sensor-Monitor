package com.sensorcon.reducinggasmonitor;



import java.util.EventObject;

import com.sensorcon.sensordrone.Drone.DroneEventListener;
import com.sensorcon.sensordrone.Drone.DroneStatusListener;
import com.sensorcon.sdhelper.ConnectionBlinker;
import com.sensorcon.sdhelper.SDBatteryStreamer;
import com.sensorcon.sdhelper.SDHelper;
import com.sensorcon.sdhelper.SDStreamer;

import android.os.Bundle;
import android.os.Handler;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.view.MotionEvent;
import android.view.View.OnTouchListener;
// Don't let eclipse import android.widget.TableLayout.LayoutParams for your TableRows!
import android.widget.TableRow.LayoutParams;

/**
 * Sensordrone Control for the Sensordrone
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
	 * Variables for popup info
	 */
	private PopupWindow popup;
	private TextView tv_popupTitle;
	private TextView tv_popupInfo;
	private ImageButton xButton;
	private ImageButton leftButton;
    private ImageButton rightButton;
    private ImageButton leftButtonPressed;
    private ImageButton rightButtonPressed;
	private ImageButton redButton;
	private ImageButton genButton;
	private ImageButton warningButton;
	private PreferencesStream pStream;
	private LayoutInflater inflater2;
	private View layout2;
	
	private boolean on;
	private boolean firstTime;
	private int redValue;
    
    public boolean inCountdown1Mode = false;
    public PopupWindow warmUpWindow1;
    private int countdown1 = 61;
    private TextView tv_countdown1;
    private Handler countdown1Handler = new Handler();
    private Handler displayValHandler = new Handler();
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

		// GUI Stuff
		public TableLayout onOffLayout;
		public TableLayout infoLayout;
		public TextView tvConnectionStatus;
		public TextView tvConnectInfo;
		public TextView tvSensorValue;
		public LinearLayout logoLayout;
		public TextView logoText;
		public ImageView logoImage;
		public TableRow sensorRow;
		
		
//		// This is added for the battery voltage
//		public TableRow bvRow;
//		public ToggleButton bvToggle;
//		public TextView bvLabel;
//		public TextView bvValue;
		
		// Another object from the SDHelper library. It helps us set up our pseudo streaming
		public SDStreamer streamer;

		// We only want to notify of a low battery once,
		// but the event might be triggered multiple times.
		// We use this to try and show it only once
		public boolean lowbatNotify;


		/*
		 * Our TableRow layout
		 */
		public LayoutParams trLayout = new LayoutParams(
				LayoutParams.MATCH_PARENT, 
				LayoutParams.MATCH_PARENT
				);
		/*
		 * Our TextView label layout
		 */
		public LayoutParams tvLayout = new LayoutParams(
				LayoutParams.MATCH_PARENT, 
				LayoutParams.MATCH_PARENT, 
				0.45f
				);

		/*
		 * Our ToggleButton layout
		 */
		public LayoutParams tbLayout = new LayoutParams(
				LayoutParams.MATCH_PARENT, 
				LayoutParams.MATCH_PARENT, 
				0.1f
				);

		// Our constructor to set up the GUI
		public Storage(Context context, TableLayout mainLayout) {

			onOffLayout = mainLayout;

			onOffLayout.setBackground(context.getResources().getDrawable(R.drawable.bg));
			
			qsSensor = droneApp.myDrone.QS_TYPE_REDUCING_GAS;

			// This will Blink our Drone, once a second, Blue
			myBlinker = new ConnectionBlinker(droneApp.myDrone, 1000, 0, 0, 255);

			// Set up the TableRows
			sensorRow = new TableRow(context);
			sensorRow.setPadding(10, 10, 10, 0);

			tvSensorValue = new TextView(context);
			streamer = new SDStreamer(droneApp.myDrone, qsSensor);

			sensorRow.setLayoutParams(trLayout); // Set the layout
			
			tvSensorValue.setBackgroundResource(R.drawable.valuegradient);
			tvSensorValue.setTextColor(Color.WHITE);
			tvSensorValue.setGravity(Gravity.CENTER);
			tvSensorValue.setText("--"); // Start off with -- for the sensor value on create
			tvLayout.setMargins(30, 30, 30, 30);
			tvSensorValue.setLayoutParams(tvLayout); // Set the layout
			tvSensorValue.setTextSize(60);

			/*
			 * Add all of our UI elements to the TableRow.
			 * (Order is important!)
			 */
			sensorRow.addView(tvSensorValue);

				/*
				 * The general behavior of the program is as follows:
				 * 
				 * When a sensor is enabled:
				 * 1) When this button is toggled, it executes the Drone qsEnable method 
				 * for the sensor. This updates the sensors status, and triggers its section
				 * of the DroneStatusListener. It also sets up the myStreamer object used 
				 * to make measurements at a specified interval.
				 * 2) When the DroneStatusListener is triggered, it runs the sensor's measurement method.
				 * When the measurement comes back, it triggers the DroneEventListener section 
				 * for that sensor. There, it updates the display with it's value, and uses the myStreamer
				 * handler to ask for a measurement again automatically at the defined interval.
				 * 2-b) This repeats until the myStreamer object is disabled.
				 * 
				 * When a sensor is disabled:
				 * 1) The mySteamer object is stopped, preventing more measurements from being requested.
				 * The Drone qsDisable method is called for the appropriate sensor (This also triggers
				 * the corresponding DroneStatusEvent!).
				 */

//					/*
//					 * Turn the sensors on/off
//					 */
//					@Override
//					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//						// If the Sensordrone is not connected, don't allow toggling of the sensors
//						if (!droneApp.myDrone.isConnected) {
//							toggleButtons[counter].setChecked(false);
//						} else {
//							if (toggleButtons[counter].isChecked() ) {
//								
//								showWarmupWindow();
//								countdown1Handler.post(countdown1Runnable);
//								
//								// Enable our steamer
//								streamerArray[counter].enable();
//								// Enable the sensor
//								droneApp.myDrone.quickEnable(qsSensors[counter]);
//
//							} else {
//								// Stop taking measurements
//								streamerArray[counter].disable();
//								
//								inCountdown1Mode = false;
//								countdown1 = 61;
//								warmUpWindow1.dismiss();
//								countdown1Handler.removeCallbacksAndMessages(null);
//
//								// Disable the sensor
//								droneApp.myDrone.quickDisable(qsSensors[counter]);
//
//							}
//						}
//					}
//				});
			
			// Connection Status TextView
			tvConnectionStatus = new TextView(context);
			tvConnectionStatus.setText("Disconnected");
			tvConnectionStatus.setTextColor(Color.WHITE);
			tvConnectionStatus.setTextSize(18);
			tvConnectionStatus.setPadding(10, 10, 10, 10);
			tvConnectionStatus.setGravity(Gravity.CENTER);
			// Tell people how to connect
			tvConnectInfo = new TextView(context);
			tvConnectInfo.setText("Connect from your device's menu");
			tvConnectInfo.setTextColor(Color.WHITE);
			tvConnectInfo.setTextSize(18);
			tvConnectInfo.setPadding(10, 10, 10, 10);
			tvConnectInfo.setGravity(Gravity.CENTER);
			tvConnectInfo.setVisibility(TextView.VISIBLE);
			

			// Our top Picture Thing
			logoLayout = new LinearLayout(context);
			logoText = new TextView(context);
			logoText.setBackgroundColor(Color.WHITE);
			logoText.setTextColor(Color.BLACK);
			logoText.setBackgroundColor(Color.parseColor("#B0B0B0"));
			logoText.setText("Reducing Gas Sensor Monitor   ");
			logoText.setTextSize(22);
			logoImage = new ImageView(context);
			Drawable img = getResources().getDrawable(R.drawable.red_icon);
			logoImage.setImageDrawable(img);
			logoImage.setAdjustViewBounds(true);
			logoImage.setMaxHeight(100);
			logoLayout.addView(logoText);
			logoLayout.addView(logoImage);
			logoLayout.setGravity(Gravity.CENTER);
			logoLayout.setBackgroundResource(R.drawable.logogradient);
			logoLayout.setPadding(10,10,10,10);

			
//			// Measuring battery voltage is not part of the API's quickSyetem, so we will have
//			// to set up a table row manually here
//			bvRow = new TableRow(context);
//			bvRow.setLayoutParams(trLayout);
//			bvRow.setPadding(10, 10, 10, 0);
//			bvToggle = new ToggleButton(context);
//			bvToggle.setLayoutParams(tbLayout);
//			bvLabel = new TextView(context);
//			bvLabel.setLayoutParams(tvLayout);
//			bvLabel.setText("Battery Voltage");
//			bvValue = new TextView(context);
//			bvValue.setBackgroundResource(R.drawable.valuegradient);
//			bvValue.setTextColor(Color.WHITE);
//			bvValue.setGravity(Gravity.CENTER);
//			bvValue.setLayoutParams(tvLayout);
//			bvValue.setText("--");
			
			
//			// Use our Battery Streamer from the SDHelper library
//			final SDBatteryStreamer bvStreamer = new SDBatteryStreamer(droneApp.myDrone);
//			
//			// Set up our graphing
//			bvValue.setOnClickListener(new OnClickListener() {
//				
//				@Override
//				public void onClick(View v) {
//					// Only graph if the toggle button is checked
//					if (bvToggle.isChecked()){
//						Intent myIntent = new Intent(getApplicationContext(), GraphActivity.class);
//						myIntent.putExtra("SensorName", "Battery Voltage");
//						// We'll use a made-up number outside of the range of the quickSystem
//						// that we can parse for the battery voltage
//						myIntent.putExtra("quickInt", 42); 
//						startActivity(myIntent);
//					}
//				}
//			});
//			
//			// Set up our toggle button
//			bvToggle.setOnCheckedChangeListener(new OnCheckedChangeListener() {
//
//				@Override
//				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//					// Don't do anything if not connected
//					if (!droneApp.myDrone.isConnected) {
//						bvToggle.setChecked(false);
//					} else {
//						if (bvToggle.isChecked() ) {
//							// Enable our steamer
//							bvStreamer.enable();
//							// Measure the voltage once to trigger streaming
//							droneApp.myDrone.measureBatteryVoltage();
//
//						} else {
//							// Stop taking measurements
//							bvStreamer.disable();
//							
//						}
//					}	
//				}
//			});
			
//			// Add it all to the row. We'll add the row to the main layout in onCreate
//			bvRow.addView(bvToggle);
//			bvRow.addView(bvLabel);
//			bvRow.addView(bvValue);
			

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
					
					// Things to do when we connect to a Sensordrone
					quickMessage("Connected!");
					tvUpdate(tvConnectionStatus, "Connected to: " + droneApp.myDrone.lastMAC);
					// Flash teh LEDs green
					myHelper.flashLEDs(droneApp.myDrone, 3, 100, 0, 255, 0);
					// Turn on our blinker
					myBlinker.enable();
					myBlinker.run();
					// People don't need to know how to connect if they are already connected
					box.tvConnectInfo.setVisibility(TextView.INVISIBLE);
					// Notify if there is a low battery
					lowbatNotify = true;
				}

				@Override
				public void connectionLostEvent(EventObject arg0) {

					// Things to do if we think the connection has been lost.
					
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
					tvUpdate(tvSensorValue, String.format("%.0f", droneApp.myDrone.reducingGas_Ohm) + " Ohms");
					//tvSensorValue.setText(Integer.toString((int)droneApp.myDrone.reducingGas_Ohm) + " Ohms");
					//redValue = (int)droneApp.myDrone.reducingGas_Ohm;
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
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

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
		box = (Storage) getLastNonConfigurationInstance();
		
		/*
		 * Set up all of the popup window stuff for the info buttons
		 */
		popup = new PopupWindow(this);
		leftButton = (ImageButton)findViewById(R.id.left_button);
        leftButtonPressed = (ImageButton)findViewById(R.id.left_button_pressed);
        rightButton = (ImageButton)findViewById(R.id.right_button);
        rightButtonPressed = (ImageButton)findViewById(R.id.right_button_pressed);
		redButton = (ImageButton)findViewById(R.id.red_button);
		genButton = (ImageButton)findViewById(R.id.gen_button);
		warningButton = (ImageButton)findViewById(R.id.warning_button);
		
		leftButtonPressed.setVisibility(View.GONE);
        rightButtonPressed.setVisibility(View.GONE);
        
        on = false;
        firstTime = true;
        
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

		popup = new PopupWindow(
				layout1, 
				550, 
				550, 
				true);
		
		tv_popupTitle = (TextView)layout1.findViewById(R.id.popupTitle);
		tv_popupInfo = (TextView)layout1.findViewById(R.id.popupInfo);
		
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
		
		inflater2 = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		 
        layout2 = inflater2.inflate(R.layout.warmup,
                (ViewGroup) findViewById(R.id.warmup_element));
        
        tv_countdown1 = (TextView)layout2.findViewById(R.id.countdown);
        
		/*
		 * But what if this is the first time the app has loaded?
		 */
		if (box != null) {
			// Remove the (old) views so we can re-add them
			box.onOffLayout.removeAllViews();
			// It's very important that this get called again
			box.onOffLayout = (TableLayout)findViewById(R.id.tlOnOff);
			// Add in our top image
			box.onOffLayout.addView(box.logoLayout);
			// Re-add the TableRows
			box.onOffLayout.addView(box.sensorRow);
//			box.onOffLayout.addView(box.bvRow);
			box.onOffLayout.addView(box.tvConnectionStatus);
			box.onOffLayout.addView(box.tvConnectInfo);
		}
		if (box == null) {

			// Set up a new box, and all of it's objects
			box = new Storage(this, (TableLayout)findViewById(R.id.tlOnOff));
			// Add in our top image
			box.onOffLayout.addView(box.logoLayout);
			// Add the TableRows to the TableLayout
			box.onOffLayout.addView(box.sensorRow);
//			box.onOffLayout.addView(box.bvRow);
			box.onOffLayout.addView(box.tvConnectionStatus);
			box.onOffLayout.addView(box.tvConnectInfo);
		}
		
		// Check to see if user still wants intro screen to show
		pStream = new PreferencesStream();
		pStream.initFile(this);
		String[] preferences = new String[1];
		preferences = pStream.readPreferences();
		
		if(!preferences[0].equals("DISABLE INTRO")){
			showIntroDialog();
		}
		
		// Set up our background
		Drawable bgGradient = getResources().getDrawable(R.drawable.tablegradient);
		box.onOffLayout.setBackgroundDrawable(bgGradient);
		box.onOffLayout.setPadding(10, 10, 10, 10);
		warmUpWindow1 = new PopupWindow(this);
	}

	public void leftButtonPressed() {
        
        if(droneApp.myDrone.isConnected) {
            if(!on) {
            	// Enable our steamer
                box.streamer.enable();
                // Enable the sensor
                droneApp.myDrone.quickEnable(box.qsSensor);
                
                on = true;
                
                int w = box.tvSensorValue.getWidth();
                int h = box.tvSensorValue.getHeight();
                
                if(firstTime) {
                	firstTime = false;
	                warmUpWindow1 = new PopupWindow(layout2, w, h, true);
	                warmUpWindow1.setOutsideTouchable(true);
	                warmUpWindow1.setFocusable(false);
                }
                
                showWarmupWindow(h);
                countdown1Handler.post(countdown1Runnable);
            } else {
            	// Stop taking measurements
                box.streamer.disable();
                // Disable the sensor
                droneApp.myDrone.quickDisable(box.qsSensor);
                
            	//box.streamer.streamHandler.removeCallbacksAndMessages(null);
            	
            	countdown1Handler.removeCallbacksAndMessages(null);
            	displayValHandler.removeCallbacksAndMessages(null);
                
                inCountdown1Mode = false;
                countdown1 = 61;
                warmUpWindow1.dismiss();
//                
                on = false;
                
            }
        }
    }
 
    public void rightButtonPressed() {
        // Only register a click if the sensor is enabled
        if (droneApp.myDrone.quickStatus(box.qsSensor)) {
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
    
	/**
	 * Loads the dialog shown at startup
	 */
	public void showIntroDialog() {

		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setCancelable(false);
		alert.setTitle("Introduction").setMessage(R.string.intro);
		alert.setPositiveButton("Don't Show Again", new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int which) { 
		            pStream.disableIntroDialog();
		        }
		     })
		    .setNegativeButton("Okay", new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int which) { 
		            // do nothing
		        }
		     }).show();
	}
	
	public void showWarmupWindow(int h) {
        inCountdown1Mode = true;
        warmUpWindow1.showAsDropDown(box.tvSensorValue, 0, -h);
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
		tv_popupTitle.setText("Developer Warning");
		tv_popupInfo.setText(R.string.warning);
		
		// The code below assumes that the root container has an id called 'main'
		popup.showAtLocation(findViewById(R.id.anchor), Gravity.CENTER, 0, 0);
	}
	
	public Runnable countdown1Runnable = new Runnable() {
		 
        @Override
        public void run() {
            
            if(inCountdown1Mode) {
                countdown1--;
                
                // Average for the last 15 seconds of count down
                if(countdown1 == 0) {
                    inCountdown1Mode = false;
                    countdown1 = 61;
                    warmUpWindow1.dismiss();
                    countdown1Handler.removeCallbacksAndMessages(null);
                }
                else {
                    tv_countdown1.setText(Integer.toString(countdown1));
                    countdown1Handler.postDelayed(this, 1000);
                }
                
            }
        }
    };
	
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

				if(inCountdown1Mode) {
                    inCountdown1Mode = false;
                    countdown1 = 61;
                    warmUpWindow1.dismiss();
                    countdown1Handler.removeCallbacksAndMessages(null);
                }
				
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
				box.tvConnectInfo.setVisibility(TextView.VISIBLE);
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
				// This option is used to re-connect to the last connected MAC
				if (!droneApp.myDrone.lastMAC.equals("")) {
					if (!droneApp.myDrone.btConnect(droneApp.myDrone.lastMAC)) {
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
		case R.id.infoConnections:
			myInfo.connectionInfo();
			break;
		}
		return true;
	}

}

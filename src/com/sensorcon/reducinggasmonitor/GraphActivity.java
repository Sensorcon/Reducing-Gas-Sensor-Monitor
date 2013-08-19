package com.sensorcon.reducinggasmonitor;


import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.EditText;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.SimpleXYSeries.ArrayFormat;
import com.androidplot.xy.XYPlot;
import com.sensorcon.sensordrone.DroneEventHandler;
import com.sensorcon.sensordrone.DroneEventObject;

import java.util.Arrays;
import java.util.LinkedList;

@SuppressLint("NewApi")
public class GraphActivity extends Activity {


	protected DroneApplication droneApp;
	private float valueToWatch;
	private int sensorToWatch;
	public int upperBound;
	public int lowerBound;
	public int currentUpper;
	public int currentLower;
	public int[] savedRange;
	
	private int api;
	private final int NEW_API = 0;
	private final int OLD_API = 1;


    private DroneEventHandler droneHandler = new DroneEventHandler() {
        @Override
        public void parseEvent(DroneEventObject droneEventObject) {
             if (droneEventObject.matches(DroneEventObject.droneEventType.REDUCING_GAS_MEASURED)) {
                 if (sensorToWatch == droneApp.myDrone.QS_TYPE_REDUCING_GAS) {
                     valueToWatch = droneApp.myDrone.reducingGas_Ohm;
                     addData(valueToWatch);
                 }
             }
        }
    };




	private static final int HISTORY_SIZE = 30; // Display last 30 measurements
	private XYPlot dronePlot = null;
	SimpleXYSeries droneValues = null;
	private LinkedList<Number> droneHistory;


	@Override
	protected void onDestroy() {
		super.onDestroy();
		// The listeners are registered on every create, so remove them on every destroy
        droneApp.myDrone.unregisterDroneListener(droneHandler);

		// If we are closing the graph, then we want to restore the Streaming Rate
		if (isFinishing()) {
			droneApp.streamingRate = droneApp.defaultRate;
		}
	}
	
	@Override
	public int[] onRetainNonConfigurationInstance() {
		// We use this so if we set the range, it doesn't reset on orientation change
		int []oldRange = new int[] {currentLower, currentUpper};
		return oldRange;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
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
		setContentView(R.layout.graph);		
		if(api == NEW_API) {
		    ActionBar actionBar = getActionBar();
		    actionBar.show();
		}
		
		// Use our Drone
		droneApp = (DroneApplication)getApplication();

		// Get info from the Intent that started us
		Intent myIntent = getIntent();
		String myLabel = myIntent.getStringExtra("SensorName");
		sensorToWatch = myIntent.getIntExtra("quickInt", 0);

		// Graph data
		droneHistory = new LinkedList<Number>();
		droneValues= new SimpleXYSeries(myLabel);
		
		// Graph foramtting
		dronePlot = (XYPlot)findViewById(R.id.dynamicPlot);
		LineAndPointFormatter lF = new LineAndPointFormatter(Color.rgb(0, 0, 0), Color.rgb(0, 255, 0), null, null);
		dronePlot.addSeries(droneValues, lF);
		dronePlot.setDomainStepValue(1);
		dronePlot.setTicksPerDomainLabel(10);


		// Start with typical bounds for a particular sensor
		// These will be the default Range. Can be changed via the Menu
		if (sensorToWatch == droneApp.myDrone.QS_TYPE_TEMPERATURE) {
			upperBound = 120;
			lowerBound = 20;
		} else if (sensorToWatch == droneApp.myDrone.QS_TYPE_HUMIDITY) {
			upperBound = 90;
			lowerBound = 10;
		} else if (sensorToWatch == droneApp.myDrone.QS_TYPE_PRESSURE) {
			upperBound = 100500;
			lowerBound = 98000;
		} else if (sensorToWatch == droneApp.myDrone.QS_TYPE_IR_TEMPERATURE) {
			upperBound = 120;
			lowerBound = 20;
		} else if (sensorToWatch == droneApp.myDrone.QS_TYPE_RGBC) {
			upperBound = 1000;
			lowerBound = 0;
		} else if (sensorToWatch == droneApp.myDrone.QS_TYPE_PRECISION_GAS) {
			upperBound = 60;
			lowerBound = 0;
		} else if (sensorToWatch == droneApp.myDrone.QS_TYPE_CAPACITANCE) {
			upperBound = 3000;
			lowerBound = 2000;
		} else if (sensorToWatch == droneApp.myDrone.QS_TYPE_OXIDIZING_GAS) {
				upperBound = 10000;
				lowerBound = 50;
		} else if (sensorToWatch == droneApp.myDrone.QS_TYPE_REDUCING_GAS) {
					upperBound = 500;
					lowerBound = 1;
		} else if (sensorToWatch == droneApp.myDrone.QS_TYPE_ADC) {
			upperBound = 3;
			lowerBound = 0;
		} else if (sensorToWatch == droneApp.myDrone.QS_TYPE_ALTITUDE) {
			upperBound = 1000;
			lowerBound = 300;
		} else if (sensorToWatch == 42) { // Hard coded value for battery votlage
			upperBound = 5;
			lowerBound = 3;
		} else {
			upperBound = 100;
			lowerBound = 0;
		}

		// Get the saved ranges (if any)
		savedRange = (int[]) getLastNonConfigurationInstance();
		
		if(savedRange != null) {
			// Load the saved ranges
			currentUpper = savedRange[1];
			currentLower = savedRange[0];
		} else {
			// Use default ranges
			currentUpper = upperBound;
			currentLower = lowerBound;
		}
		
		// Quickly pre-load 30 points on the graph (due to the way it ranges).
		initializeData(currentLower);

		// Set the graph boundary modes and X,Y labels
		dronePlot.setRangeLowerBoundary(currentLower, BoundaryMode.FIXED);
		dronePlot.setRangeUpperBoundary(currentUpper, BoundaryMode.FIXED);
		dronePlot.setDomainLabel("Last 30 Measurements");
		dronePlot.setRangeLabel("Sensor Value");

		// Register the listeners
        droneApp.myDrone.registerDroneListener(droneHandler);

	}

	// This updates our data and redraws the graph
	public void addData(float data) {

		data = data/1000;
		
		Number[] numbers = {data};
		droneValues.setModel(Arrays.asList(numbers), ArrayFormat.Y_VALS_ONLY);
		// If our data size is larger than our history, remove the oldest.
		if(droneHistory.size() > HISTORY_SIZE) {
			droneHistory.removeFirst();
		}
		droneHistory.addLast(numbers[0]);
		droneValues.setModel(droneHistory, ArrayFormat.Y_VALS_ONLY);
		dronePlot.redraw();


	}

	/*
	 * Quickly load in some data. The graph grows/stretched until the max HISTRY_SIZE is reached,
	 * so we load in some points to keep it from looking strange at first.
	 */
	public void initializeData(float data) {
		data = data/1000;
		
		for (int i=0; i < HISTORY_SIZE; i++) {
			Number[] numbers = {data};
			
			droneValues.setModel(Arrays.asList(numbers), ArrayFormat.Y_VALS_ONLY);
			if(droneHistory.size() > HISTORY_SIZE) {
				droneHistory.removeFirst();
			}
			droneHistory.addLast(numbers[0]);
			droneValues.setModel(droneHistory, ArrayFormat.Y_VALS_ONLY);
		}
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.graph, menu);
		return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		switch(item.getItemId()){
		case R.id.rate100:
			droneApp.streamingRate = 100; //(milliseconds)
			break;
		case R.id.rate300:
			droneApp.streamingRate = 300; //(milliseconds)
			break;
		case R.id.rate1000:
			droneApp.streamingRate = 1000; //(milliseconds)
			break;
		case R.id.menuUpper:
			changeUpperRange();
			break;
		case R.id.menuLower:
			changeLowerRange();
			break;
		}
		return true;
	}

	/*
	 * A method to change the upper range of the graph
	 */
	public void changeUpperRange() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle("Upper Range");
		alert.setMessage("Set upper limit");

		// Set an EditText view to get user input 
		final EditText upper = new EditText(this);
		upper.setInputType(InputType.TYPE_CLASS_NUMBER);
		alert.setView(upper);

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				try {
					currentUpper = Integer.parseInt(upper.getText().toString());
				} catch(NumberFormatException nfe) {
					System.out.println("Could not parse " + nfe);
				}

				if (currentUpper > currentLower) {
					dronePlot.setRangeUpperBoundary(currentUpper, BoundaryMode.FIXED);
				}
			}
		});

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				// Do Nothing
			}
		});

		alert.show();
	}
	
	/*
	 * A method to change the lower range of the graph.
	 */
	public void changeLowerRange() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle("Lower Range");
		alert.setMessage("Set lower limit");

		// Set an EditText view to get user input 
		final EditText lower = new EditText(this);
		lower.setInputType(InputType.TYPE_CLASS_NUMBER);
		alert.setView(lower);

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				try {
					currentLower = Integer.parseInt(lower.getText().toString());
				} catch(NumberFormatException nfe) {
				}

				if (currentLower < currentUpper) {
					dronePlot.setRangeLowerBoundary(currentLower, BoundaryMode.FIXED);
				}
			}
		});

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				// Do Nothing
			}
		});

		alert.show();
	}
	
	/*
	 * Display a tip about graphing performance.
	 */
	public void infoPopUp() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle("Tip");
		alert.setMessage("For the fastest data rate, ensure only this sensor is enabled before graphing");


		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			}
		});

		alert.show();
	}
	
	
}

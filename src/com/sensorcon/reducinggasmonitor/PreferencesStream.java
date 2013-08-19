package com.sensorcon.reducinggasmonitor;

import android.content.Context;
import android.util.Log;

import java.io.*;

/**
 * Simple IO class to store user preferences
 * 
 * @author Sensorcon, Inc.
 */
public class PreferencesStream {
	
	private BufferedWriter out;
	private BufferedReader in;
	
	private final String fileName = "oxidizinggasmonitor_preferences.txt";
	
	private File file;
	
	/**
	 * Initializes file to store preferences
	 * 
	 * @param context
	 */
	public void initFile(Context context) {
		
		try {
			file = new File(context.getFilesDir(), fileName);
			 
			// if file doesnt exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}
			
		} catch (IOException e) {
			Log.d("chris", e.getMessage());
		}
		
	}
	
	/**
	 * Disables dialog
	 * 
	 */
	public void disableIntroDialog() {
		try {
			out = new BufferedWriter(new FileWriter(file.getAbsoluteFile()));
			out.write("DISABLE INTRO");
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Reads preferences
	 * 
	 * @return preferences
	 */
	public String[] readPreferences() {
		String[] data = new String[1];
		
		for(int i = 0; i < data.length; i++) {
			data[i] = "";
		}
		
		try {
			in = new BufferedReader(new FileReader(file.getAbsoluteFile()));
			String line;
			int i = 0;
			while((line = in.readLine()) != null) {
				data[i] = line;
				i++;
			}

			in.close();

		} catch (NumberFormatException e) {
			Log.d("chris", e.getMessage());
		} catch (IOException e) {
			Log.d("chris", e.getMessage());
		}
		
		return data;
	}
	
	/**
	 * Deletes the file where preferences are stored
	 */
	public void reset() {
		file.delete();
	}
}

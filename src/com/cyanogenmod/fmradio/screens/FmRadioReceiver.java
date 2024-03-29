/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.fmradio.screens;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import com.cyanogenmod.fmradio.R;
import com.cyanogenmod.fmradio.adapters.StationsAdapter;
import com.cyanogenmod.fmradio.utils.Constants;
import com.cyanogenmod.fmradio.utils.Utils;
import com.stericsson.hardware.fm.FmBand;
import com.stericsson.hardware.fm.FmReceiver;

import java.io.IOException;
import java.util.ArrayList;


public class FmRadioReceiver extends Activity implements OnClickListener, AdapterView.OnItemClickListener {


    // The base menu identifier
    private static final int BASE_OPTION_MENU = 0;

    // The band menu identifier
    private static final int BAND_SELECTION_MENU = 1;

    private Context context;

    // Handle to the Media Player that plays the audio from the selected station
    private AudioManager mMediaPlayer;

    // The scan listener that receives the return values from the scans
    private FmReceiver.OnScanListener mReceiverScanListener;

    // The listener that receives the RDS data from the current channel
    private FmReceiver.OnRDSDataFoundListener mReceiverRdsDataFoundListener;

    // The started listener is activated when the radio has started
    private FmReceiver.OnStartedListener mReceiverStartedListener;

    // Displays the currently tuned frequency
    private TextView mFrequencyTextView;

    // Displays the current station name if there is adequate RDS data
    private TextView mStationNameTextView;

    // Handle to the FM radio Band object
    private FmBand mFmBand;

    // Handle to the FM radio receiver object
    private FmReceiver mFmReceiver;

    // Indicates if we are in the initialization sequence
    private boolean mInit = true;

    // Indicates that we are restarting the app
    private boolean mRestart = false;

    // Protects the MediaPlayer and FmReceiver against rapid muting causing
    // errors
    private boolean mPauseMutex = false;

    // The name of the storage string
    public static final String PREFS_NAME = "FMRadioPrefsFile";

    // The menu items
    public static final int FM_BAND = Menu.FIRST;

    public static final int BAND_US = Menu.FIRST + 1;

    public static final int BAND_EU = Menu.FIRST + 2;

    public static final int BAND_JAPAN = Menu.FIRST + 3;

    public static final int BAND_CHINA = Menu.FIRST + 4;


    // The currently selected FM Radio band
    private int mSelectedBand;

    //visual components - things we are going to use often
    private ImageButton mBtnSeekUp, mBtnSeekDown, mBtnFullScan, mBtnMute, mBtnLoudSpeaker;
    private boolean isSpeakerOn;
    private ProgressBar mProgressScan;
    //TODO : replace gallery with custom implemented widget?
    private Gallery mGalStationsList;

    private ArrayList<String> mStationList;

    /**
     * Required method from parent class
     *
     * @param icicle - The previous instance of this app
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        context = getApplicationContext();
        setContentView(R.layout.main);
        mFmReceiver = (FmReceiver) getSystemService("fm_receiver"); //MOCK: new FakeFmReceiver();
        // USE Mock class if you don't have access to device with an FM Chip
        // (get mock framework from: https://github.com/pedronveloso/fm_mock_framework
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        mSelectedBand = settings.getInt("selectedBand", 1);
        mFmBand = new FmBand(mSelectedBand);
        mMediaPlayer = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setupButtons();
    }

    /**
     * Starts up the listeners and the FM radio if it isn't already active
     */
    @Override
    protected void onStart() {
        super.onStart();
        mReceiverScanListener = new com.stericsson.hardware.fm.FmReceiver.OnScanListener() {

            // FullScan results
            public void onFullScan(int[] frequency, int[] signalStrength, boolean aborted) {
                Utils.debugFunc("onFullScan(). aborted: " + aborted, Log.INFO);
                //stop progress animation
                stopScanAnimation();

                mStationList = new ArrayList<String>(frequency.length);
                for (int i = 0; i < frequency.length; i++) {
                    Utils.debugFunc("[item] freq: " + frequency[i] + ", signal: " + signalStrength[i], Log.INFO);
                    String a = Double.toString((double) frequency[i] / 1000);
                    if (mFmBand.getChannelOffset() == Constants.CHANNEL_OFFSET_50KHZ) {
                        a = String.format(a, "%.2f");
                    } else {
                        a = String.format(a, "%.1f");
                    }
                    mStationList.add(a);
                }

                //fill stations list
                mGalStationsList.setAdapter(new StationsAdapter(FmRadioReceiver.this, mStationList));
                mGalStationsList.setOnItemClickListener(FmRadioReceiver.this);

                //initialize playback of first station
                if (mInit) {
                    mInit = false;
                    try {
                        mFmReceiver.setFrequency(frequency[0]);
                        mFrequencyTextView.setText(mStationList.get(0));
                    } catch (Exception e) {
                        Utils.debugFunc("onFullScan(). E.: " + e.getMessage(), Log.ERROR);
                        showToast(R.string.unable_to_set_frequency, Toast.LENGTH_LONG);
                    }
                }
            }

            // Returns the new frequency.
            public void onScan(int tunedFrequency, int signalStrength, int scanDirection, boolean aborted) {
                Utils.debugFunc("onScan(). freq: " + tunedFrequency + ", signal: " + signalStrength + ", dir: " + scanDirection + ", aborted? " + aborted, Log.INFO);

                stopScanAnimation();

                String a = Double.toString((double) tunedFrequency / 1000);
                if (mFmBand.getChannelOffset() == Constants.CHANNEL_OFFSET_50KHZ) {
                    mFrequencyTextView.setText(String.format(a, "%.2f"));
                } else {
                    mFrequencyTextView.setText(String.format(a, "%.1f"));
                }
                mBtnSeekUp.setEnabled(true);
                mBtnSeekDown.setEnabled(true);
            }
        };
        mReceiverRdsDataFoundListener = new com.stericsson.hardware.fm.FmReceiver.OnRDSDataFoundListener() {

            // Receives the current frequency's RDS Data
            public void onRDSDataFound(Bundle rdsData, int frequency) {
                if (rdsData.containsKey("PSN")) {
                    Utils.debugFunc("onRDSDataFound(). PSN: " + rdsData.getString("PSN"), Log.INFO);
                    mStationNameTextView.setText(rdsData.getString("PSN"));
                }
            }
        };

        mReceiverStartedListener = new com.stericsson.hardware.fm.FmReceiver.OnStartedListener() {

            public void onStarted() {
                Utils.debugFunc("onStarted()", Log.INFO);
                // Activate all the buttons
                mBtnSeekUp.setEnabled(true);
                mBtnSeekDown.setEnabled(true);
                mBtnMute.setEnabled(true);
                mBtnLoudSpeaker.setEnabled(true);
                mBtnFullScan.setEnabled(true);
                initialBandscan();
                startAudio();
            }
        };

        mFmReceiver.addOnScanListener(mReceiverScanListener);
        mFmReceiver.addOnRDSDataFoundListener(mReceiverRdsDataFoundListener);
        mFmReceiver.addOnStartedListener(mReceiverStartedListener);

       /* if (!mRestart) {
            turnRadioOn();
        } */
        mRestart = false;
    }

    /**
     * Stops the FM Radio listeners
     */
    @Override
    protected void onRestart() {
        super.onRestart();
        mRestart = true;
    }

    /**
     * Stops the FM Radio listeners
     */
    @Override
    protected void onStop() {
        super.onStop();

        if (mFmReceiver != null) {
            mFmReceiver.removeOnScanListener(mReceiverScanListener);
            mFmReceiver.removeOnRDSDataFoundListener(mReceiverRdsDataFoundListener);
            mFmReceiver.removeOnStartedListener(mReceiverStartedListener);
        }
    }

    /**
     * Saves the FmBand for next time the program is used and closes the radio
     * and media player.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("selectedBand", mSelectedBand);
        editor.commit();
        try {
            mFmReceiver.reset();
        } catch (IOException e) {
            Utils.debugFunc("Unable to reset correctly E.: " + e, Log.ERROR);
        }
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_FM, AudioSystem.DEVICE_STATE_UNAVAILABLE, "");
        mMediaPlayer.setParameters("fm_off=1");
    }

    /**
     * Starts the initial bandscan in it's own thread
     */
    private void initialBandscan() {
        Utils.debugFunc("initialBandscan()", Log.INFO);
        Thread bandscanThread = new Thread() {
            public void run() {
                try {
                    mFmReceiver.startFullScan();
                } catch (IllegalStateException e) {
                    showToast(R.string.unable_to_scan, Toast.LENGTH_LONG);
                    Utils.debugFunc("initialBandscan(). E.: " + e.getMessage(), Log.ERROR);
                    return;
                }
            }
        };
        bandscanThread.start();
    }


    /**
     * Helper method to display toast
     */
    private void showToast(final int resourceID, final int duration) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(context, getString(resourceID), duration).show();
            }
        });
    }

    /**
     * Starts the FM receiver and makes the buttons appear inactive
     */
    private void turnRadioOn() {
        Utils.debugFunc("turnRadioOn", Log.INFO);

        try {
            mFmReceiver.startAsync(mFmBand);
            // Darken the the buttons
            mBtnSeekUp.setEnabled(false);
            mBtnSeekDown.setEnabled(false);
            mBtnMute.setEnabled(false);
            mBtnLoudSpeaker.setEnabled(false);
            mBtnFullScan.setEnabled(false);
            showToast(R.string.scanning_for_stations, Toast.LENGTH_LONG);
        } catch (Exception e) {
            Utils.debugFunc("turnRadioOn(). E.: " + e.getMessage(), Log.ERROR);
            showToast(R.string.unable_to_start_radio, Toast.LENGTH_LONG);
        }
    }

    /**
     * Starts the FM receiver and makes the buttons appear inactive
     */
    private void startAudio() {
        Utils.debugFunc("startAudio()", Log.INFO);
        try {
            boolean speaker = !context.getResources().getBoolean(R.bool.speaker_supported);
            setSpeakerUI(speaker);
            setSpeakerFunc(speaker);
        } catch (Exception e) {
            Utils.debugFunc("startAudio. E.: " + e.getMessage(), Log.ERROR);
            showToast(R.string.unable_to_mediaplayer, Toast.LENGTH_LONG);
        }
    }

    /**
     * Sets up the buttons and their listeners
     */
    private void setupButtons() {
        Utils.debugFunc("setupButtons()", Log.INFO);

        mFrequencyTextView = (TextView) findViewById(R.id.FrequencyTextView);
        mStationNameTextView = (TextView) findViewById(R.id.tv_rds_text);
        mProgressScan = (ProgressBar) findViewById(R.id.scan_progressbar);

        mGalStationsList = (Gallery) findViewById(R.id.gal_stations_list);

        mBtnSeekUp = (ImageButton) findViewById(R.id.btn_seek_up);
        mBtnSeekUp.setOnClickListener(this);

        mBtnSeekDown = (ImageButton) findViewById(R.id.btn_seek_down);
        mBtnSeekDown.setOnClickListener(this);

        mBtnMute = (ImageButton) findViewById(R.id.btn_mute);
        mBtnMute.setOnClickListener(this);

        mBtnLoudSpeaker = (ImageButton) findViewById(R.id.btn_loudspeaker);
        mBtnLoudSpeaker.setOnClickListener(this);
        if (!context.getResources().getBoolean(R.bool.speaker_supported))
            mBtnLoudSpeaker.setVisibility(View.INVISIBLE);

        mBtnFullScan = (ImageButton) findViewById(R.id.btn_fullscan);
        mBtnFullScan.setOnClickListener(this);
    }

    /**
     * Stops scanning animation
     */
    private void stopScanAnimation() {
        mProgressScan.setVisibility(View.GONE);
        mBtnFullScan.setVisibility(View.VISIBLE);
        mBtnFullScan.setEnabled(true);
    }

    /**
     * Start scanning animation
     */
    private void startScanAnimation() {
        mProgressScan.setVisibility(View.VISIBLE);
        mBtnFullScan.setVisibility(View.GONE);
        mBtnFullScan.setEnabled(false);
    }

    /**
     * If application is currently scanning, that scanning operation is
     * canceled.
     */
    private void stopCurrentScan() {
        if (mFmReceiver.getState() == FmReceiver.STATE_SCANNING) {
            Utils.debugFunc("There is a scan in progress. Stopping it.", Log.INFO);
            mFmReceiver.stopScan();
        }
    }

    private void setSpeakerUI(boolean on) {
        mBtnLoudSpeaker.setImageResource(on ? R.drawable.fm_loudspeaker : R.drawable.fm_loudspeaker_off);
    }

    private void setSpeakerFunc(boolean on) {
        AudioSystem.setForceUse(AudioSystem.FOR_MEDIA, on ? AudioSystem.FORCE_SPEAKER : AudioSystem.FORCE_NONE);
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_FM, AudioSystem.DEVICE_STATE_UNAVAILABLE, "");
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_FM, AudioSystem.DEVICE_STATE_AVAILABLE, "");
        mMediaPlayer.setParameters("fm_on=1");
        isSpeakerOn = on;
    }

    /**
     * Sets up the options menu when the menu button is pushed, dynamic
     * population of the station select menu
     */
    public boolean onPrepareOptionsMenu(Menu menu) {
        Utils.debugFunc("onPrepareOptionsMenu()", Log.INFO);
        menu.clear();
        boolean result = super.onCreateOptionsMenu(menu);
        SubMenu subMenu = menu.addSubMenu(BASE_OPTION_MENU, FM_BAND, Menu.NONE,
                R.string.band_select);
        subMenu.setIcon(android.R.drawable.ic_menu_mapmode);
        // Populate the band selection menu
        subMenu.add(BAND_SELECTION_MENU, BAND_US, Menu.NONE, R.string.band_us);
        subMenu.add(BAND_SELECTION_MENU, BAND_EU, Menu.NONE, R.string.band_eu);
        subMenu.add(BAND_SELECTION_MENU, BAND_JAPAN, Menu.NONE, R.string.band_ja);
        subMenu.add(BAND_SELECTION_MENU, BAND_CHINA, Menu.NONE, R.string.band_ch);
        subMenu.setGroupCheckable(BAND_SELECTION_MENU, true, true);
        subMenu.getItem(mSelectedBand).setChecked(true);
        return result;
    }


    /**
     * React to a selection in the option menu
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getGroupId()) {

            case BAND_SELECTION_MENU:
                switch (item.getItemId()) {
                    case BAND_US:
                        mSelectedBand = FmBand.BAND_US;
                        item.setChecked(true);
                        break;
                    case BAND_EU:
                        mSelectedBand = FmBand.BAND_EU;
                        item.setChecked(true);
                        break;
                    case BAND_JAPAN:
                        mSelectedBand = FmBand.BAND_JAPAN;
                        item.setChecked(true);
                        break;
                    case BAND_CHINA:
                        mSelectedBand = FmBand.BAND_CHINA;
                        item.setChecked(true);
                        break;
                    default:
                        break;
                }
                mFmBand = new FmBand(mSelectedBand);
                try {
                    mFmReceiver.reset();
                } catch (IOException e) {
                    Utils.debugFunc("Unable to restart. E.: " + e.getMessage(), Log.ERROR);
                    showToast(R.string.unable_to_restart, Toast.LENGTH_LONG);
                }
                turnRadioOn();
                break;

            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.btn_seek_down:
                Utils.debugFunc("SeekDown pressed", Log.INFO);
                try {
                    v.setEnabled(false);
                    // stop current scan (if one is in progress)
                    stopCurrentScan();
                    mFmReceiver.scanDown();
                    startScanAnimation();
                } catch (IllegalStateException e) {
                    v.setEnabled(true);
                    Utils.debugFunc("Unable to ScanDown. E.: " + e.getMessage(), Log.ERROR);
                    return;
                }
                break;

            case R.id.btn_seek_up:
                Utils.debugFunc("SeekUp pressed", Log.INFO);
                try {
                    v.setEnabled(false);
                    // stop current scan (if one is in progress)
                    stopCurrentScan();
                    mFmReceiver.scanUp();
                    startScanAnimation();
                } catch (IllegalStateException e) {
                    v.setEnabled(true);
                    Utils.debugFunc("Unable to ScanUp. E.: " + e.getMessage(), Log.ERROR);
                    return;
                }
                break;

            case R.id.btn_fullscan:
                Utils.debugFunc("Fullscan pressed", Log.INFO);
                try {
                    mFmReceiver.startFullScan();
                    startScanAnimation();
                } catch (IllegalStateException e) {
                    Utils.debugFunc("Scan error: " + e.getMessage(), Log.ERROR);
                    showToast(R.string.unable_to_scan, Toast.LENGTH_LONG);
                    v.setEnabled(true);
                }
                break;

            case R.id.btn_mute:
                Utils.debugFunc("Mute pressed", Log.INFO);
                if (mFmReceiver.getState() == FmReceiver.STATE_PAUSED && !mPauseMutex) {
                    try {
                        mPauseMutex = true;
                        mFmReceiver.resume();
                        mBtnMute.setImageResource(R.drawable.fm_volume_mute);
                    } catch (Exception e) {
                        Utils.debugFunc("Unable to resume. E.: " + e.getMessage(), Log.ERROR);
                        showToast(R.string.unable_to_resume, Toast.LENGTH_LONG);
                    }
                    mPauseMutex = false;
                } else if (mFmReceiver.getState() == FmReceiver.STATE_STARTED
                        && !mPauseMutex) {
                    try {
                        mPauseMutex = true;
                        mFmReceiver.pause();
                        mBtnMute.setImageResource(R.drawable.fm_volume);
                    } catch (Exception e) {
                        Utils.debugFunc("Unable to pause. E.: " + e.getMessage(), Log.ERROR);
                        showToast(R.string.unable_to_pause, Toast.LENGTH_LONG);
                    }
                    mPauseMutex = false;
                } else if (mPauseMutex) {
                    showToast(R.string.mediaplayer_busy, Toast.LENGTH_LONG);
                } else {
                    Utils.debugFunc("No action: incorrect state - " + mFmReceiver.getState(), Log.WARN);
                }
                break;

            case R.id.btn_loudspeaker:
                Utils.debugFunc("Loudspeaker pressed", Log.INFO);
                boolean newState = !isSpeakerOn;
                setSpeakerUI(newState);
                setSpeakerFunc(newState);
                break;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        //right now just handling gallery item clicks
        try {
            mFmReceiver.setFrequency((int) (Double.valueOf(mStationList.get(position)) * 1000));
            mFrequencyTextView.setText(mStationList.get(position));
        } catch (Exception e) {
            Utils.debugFunc("Set frequency failed! E.: " + e.getMessage(), Log.ERROR);
            showToast(R.string.unable_to_set_frequency, Toast.LENGTH_LONG);
        }
    }
}

package com.usfreu2016.datacollector_2beacons;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.Toast;

import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.SystemRequirementsChecker;
import com.estimote.sdk.Utils;
import com.estimote.sdk.eddystone.Eddystone;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    final int REQUEST_CODE_ASK_PERMISSIONS = 1;

    private BeaconManager beaconManager;

    /** Scan ID of Eddystone scanner */
    String scanId;

    /** Flag to indicate scanning activity */
    boolean scanning;

    /** Flag to indicate service ready */
    boolean serviceReady;

    /** Views for Activity */
    EditText locationEditText;
    EditText positionEditText;
    RadioButton beacon1RadioButton;
    RadioButton beacon2RadioButton;
    Button collectDataButton;
    ProgressBar beacon1ProgressBar;
    ProgressBar beacon2ProgressBar;


    /** DATA COLLECTION VARIABLES */

    final int DISTANCE_BETWEEN_BEACONS = 15;
    final int NUMBER_OF_SAMPLES = 20;

    /** Flag to indicate data is being collected */
    boolean collectingData;

    /** Position of device during collection */
    int position;

    /** Transmit power of beacon 1 and 2 */
    int beacon1TransmitPower;
    int beacon2TransmitPower;

    /** File info */
    String directoryName;
    String fileName;

    /* beacon information */
    final String beacon1Id = "J8Afaf";
    final String beacon2Id = "nsk4UG";

    final String beacon1MacAddress = "D5:00:25:D5:22:A9";
    final String beacon2MacAddress = "FF:48:85:91:B0:0D";

    int beacon1SampleCount;
    int beacon2SampleCount;

    double[] beacon1DistanceValues;
    double[] beacon2DistanceValues;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        collectingData = false;

        directoryName = "TWO BEACON DATA COLLECTION";

        locationEditText = (EditText) findViewById(R.id.locationEditText);
        positionEditText = (EditText) findViewById(R.id.positionEditText);
        beacon1RadioButton = (RadioButton) findViewById(R.id.beacon1RadioButton);
        beacon1RadioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beacon2RadioButton.setChecked(false);
            }
        });
        beacon2RadioButton = (RadioButton) findViewById(R.id.beacon2RadioButton);
        beacon2RadioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beacon1RadioButton.setChecked(false);
            }
        });
        collectDataButton = (Button) findViewById(R.id.collectDataButton);
        beacon1ProgressBar = (ProgressBar) findViewById(R.id.beacon1ProgressBar);
        beacon2ProgressBar = (ProgressBar) findViewById(R.id.beacon2ProgressBar);

        collectDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);

                if (!collectingData) {
                    beacon1SampleCount = 0;
                    beacon2SampleCount = 0;

                    beacon1DistanceValues = new double[NUMBER_OF_SAMPLES];
                    beacon2DistanceValues = new double[NUMBER_OF_SAMPLES];

                    beacon1ProgressBar.setProgress(0);
                    beacon2ProgressBar.setProgress(0);

                    collectingData = true;

                    position = Integer.parseInt(positionEditText.getText().toString());

                    fileName = positionEditText.getText().toString()
                            + " - " + locationEditText.getText().toString() + ".csv";
                }
            }
        });

        beaconManager = new BeaconManager(getApplicationContext());
        beaconManager.setEddystoneListener(new BeaconManager.EddystoneListener() {
            @Override
            public void onEddystonesFound(List<Eddystone> list) {

                if (collectingData) {
                    for (Eddystone eddystone : list) {
                        String macAddress = eddystone.macAddress.toStandardString();
                        if (beacon1MacAddress.equals(macAddress)) {

                            /** record transmit power - NOTE this is not efficient because value is  updated frequently, HOWEVER,
                             * The transmit power should remain constant throughout testing
                             */
                            beacon1TransmitPower = eddystone.calibratedTxPower;

                            if (beacon1SampleCount < NUMBER_OF_SAMPLES) {
                                beacon1DistanceValues[beacon1SampleCount] = Utils.computeAccuracy(eddystone);
                                beacon1SampleCount++;
                                beacon1ProgressBar.setProgress((int) ((((double) beacon1SampleCount / NUMBER_OF_SAMPLES)) * 100));
                            }
                        }
                        else if (beacon2MacAddress.equals(macAddress)) {

                            /** record transmit power - NOTE this is not efficient because value is  updated frequently, HOWEVER,
                             * The transmit power should remain constant throughout testing
                             */
                            beacon2TransmitPower = eddystone.calibratedTxPower;

                            if (beacon2SampleCount < NUMBER_OF_SAMPLES) {
                                beacon2DistanceValues[beacon2SampleCount] = Utils.computeAccuracy(eddystone);
                                beacon2SampleCount++;
                                beacon2ProgressBar.setProgress((int) ((((double) beacon2SampleCount / NUMBER_OF_SAMPLES)) * 100));
                            }
                        }

                        if (beacon1SampleCount >= NUMBER_OF_SAMPLES && beacon2SampleCount >= NUMBER_OF_SAMPLES) {
                            collectingData = false;
                            Toast.makeText(MainActivity.this, "Data Collected.", Toast.LENGTH_SHORT).show();
                            checkPermissions();
                        }

                    }
                }

            }
        });

        beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                serviceReady = true;

                if (!scanning) {
                    startScanning();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (serviceReady && !scanning) {
            startScanning();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (scanning) {
            stopScanning();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        beaconManager.disconnect();
    }

    private void startScanning() {
        SystemRequirementsChecker.checkWithDefaultDialogs(this);
        scanId = beaconManager.startEddystoneScanning();
        scanning = true;
    }

    private void stopScanning() {
        beaconManager.stopEddystoneScanning(scanId);
        scanning = false;
    }

    private void checkPermissions() {
        int hasWriteExternalStoragePermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (hasWriteExternalStoragePermission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_ASK_PERMISSIONS);
            return;
        }

        recordData();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    recordData();
                }
                else {
                    Toast.makeText(this, "Permissions Denied", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void recordData() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), directoryName);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e("Error", "Directory not created.");
            }
        }
        File file = new File(dir, fileName);
        try {
            FileWriter writer = new FileWriter(file, true);
            BufferedWriter output = new BufferedWriter(writer);

            output.append("Position:" + "," + position);
            output.append(",");
            output.append("\n");

            output.append("Facing:" + "," + ((beacon1RadioButton.isChecked()) ? beacon1RadioButton.getText().toString() : beacon2RadioButton.getText().toString()));
            output.append(",");
            output.append("\n");

            output.append("Time:" + "," + Calendar.getInstance().getTime().toString());
            output.append(",");
            output.append("\n");

            output.append(beacon1Id + " Tx:" + "," + beacon1TransmitPower);
            output.append(",");
            output.append("\n");

            output.append(beacon2Id + " Tx:" + "," + beacon1TransmitPower);
            output.append(",");
            output.append("\n");

            output.append(beacon1Id);
            output.append(",");
            output.append("Error");
            output.append(",");
            output.append("% Error");
            output.append(",");
            output.append(beacon2Id);
            output.append(",");
            output.append("Error");
            output.append(",");
            output.append("% Error");
            output.append(",");
            output.append("\n");

            for (int i = 0; i < NUMBER_OF_SAMPLES; i++) {

                /** Calculated distance of beacon 1 */
                output.append(String.valueOf(beacon1DistanceValues[i]));
                output.append(",");

                /** Distance error of beacon 1 */
                output.append(String.valueOf(
                        Math.abs(beacon1DistanceValues[i] - position)));
                output.append(",");

                /** Percent error of beacon 1 */
                output.append(String.valueOf(Math.abs(((beacon1DistanceValues[i] - position)) / position) * 100));
                output.append(",");

                /** Calculated distance of beacon 2 */
                output.append(String.valueOf(beacon2DistanceValues[i]));
                output.append(",");

                /** Distance error of beacon 2 */
                output.append(String.valueOf(
                        Math.abs(beacon2DistanceValues[i] - (DISTANCE_BETWEEN_BEACONS - position))));
                output.append(",");

                /** Percent error of beacon 2 */
                output.append(String.valueOf(
                        Math.abs(((beacon2DistanceValues[i] - (DISTANCE_BETWEEN_BEACONS - position))) / (DISTANCE_BETWEEN_BEACONS - position)) * 100));
                output.append(",");
                output.append("\n");
            }
            output.close();
            Toast.makeText(this, "Data recorded.", Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            Log.e("Error", "File not found exception");
        } catch (IOException e) {
            Log.e("Error", "IOException");
        }
    }
}
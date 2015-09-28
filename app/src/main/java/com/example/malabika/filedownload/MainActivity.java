package com.example.malabika.filedownload;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.audiofx.BassBoost;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.sql.Connection;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static android.net.wifi.WifiManager.EXTRA_NETWORK_INFO;

public class MainActivity extends Activity {

    //Declaring THE UI ELEMENTS
    EditText downloadLink;
    Button BtnDownload;
    Button BtnExit;
    Button BtnSaveSettings;
    Button BtnDownloadDone;
    RadioGroup SettingsRadioButtons;
    ProgressBar _busyProgressBar;
    CheckBox _onlyWifiButton;
    CheckBox _onlyRUWifiButton;
    CheckBox _showLogButton;
    TextView textProgress;
    TextView textDownloadLogs;

    private boolean _anyWifiDownload;
    private boolean _onlyRUWifiDownload;
    String myURLLink = null;

    Context context;
    BroadcastReceiver receiverDownload;
    String TAG = "WIFISTATEFILE";

    WifiManager wifiManager;
    WifiInfo wifiInfo;

    static boolean isFileDownloading = false;
    static boolean _fileCompletelyDownloaded = false;
    static String File_Name = "DownloadedFiiles.txt";
    static String Conn_Name;
    static String Conn_Time;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = MainActivity.this;

        //Initializing UI elements
        BtnDownload = (Button) findViewById(R.id.downloadButton);
        downloadLink = (EditText) findViewById(R.id.urlText);
        BtnExit = (Button) findViewById(R.id.exitButton);
        BtnDownloadDone = (Button) findViewById(R.id.okProgressButton);
        SettingsRadioButtons = (RadioGroup) findViewById(R.id.settingsGroup);
        BtnSaveSettings = (Button) findViewById(R.id.saveSettingsButton);
        _onlyRUWifiButton = (CheckBox) findViewById(R.id.ruOnlyButton);
        _onlyWifiButton = (CheckBox) findViewById(R.id.anyWifiDownloadButton);
        _busyProgressBar =(ProgressBar) findViewById(R.id.progressBar);
        textProgress = (TextView) findViewById(R.id.progressBarText);
        _showLogButton = (CheckBox) findViewById(R.id.logCheckBox);
        textDownloadLogs = (TextView) findViewById(R.id.logTextView);

        //To check if the download is set to download n any Wifi or RU Wifi
        _anyWifiDownload = _onlyWifiButton.isChecked();
        _onlyRUWifiDownload = _onlyRUWifiButton.isChecked();

//        downloadLink.setText("http://www.winlab.rutgers.edu/~janne/mobisys14gesturesecurity.pdf");
        downloadLink.setText("http://aufbix.org/~bolek/download/guide1.pdf"); // Default Link for Download
        myURLLink = downloadLink.getText().toString();

        wifiManager = (WifiManager) getBaseContext().getSystemService(Context.WIFI_SERVICE); //WiFi Manager Initialisation

        BtnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(!wifiManager.isWifiEnabled()) {
                    if (_anyWifiDownload) {
                        Toast.makeText(MainActivity.this, "WiFi is disabled. DOWNLOAD will begin after Wifi is enabled", Toast.LENGTH_LONG).show();
                    }
                    else if (_onlyRUWifiDownload)
                        Toast.makeText(MainActivity.this, "Not Connected to RU Wifi. DOWNLOAD will begin automatically when near Rutgers WIFI", Toast.LENGTH_LONG).show();
                }
                _fileCompletelyDownloaded = false;  //Check for complete file download
                isFileDownloading = false;          //Check if download is pending
                new downloadTask().execute();       //Start the download AsyncTask
            }
        });

        //For exiting the application
        BtnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExitAlert();
            }
        });

        //For hiding the Settings block
        BtnSaveSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsRadioButtons.setVisibility(View.INVISIBLE);
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        Log.e("WIFISTATEFILE", "Inside on resume method");

        IntentFilter wifiIntentFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);

        receiverDownload = new BroadcastReceiver() {

            boolean status = false;

            @Override
            public void onReceive(Context context, Intent intent) {
                //check for Wifi scan results
                Toast.makeText(MainActivity.this, "WIFI INTENT RECEIVED", Toast.LENGTH_SHORT).show();
                status = intent.getBooleanExtra(WifiManager.EXTRA_NEW_STATE,wifiManager.isWifiEnabled());
//                NetworkInfo _nInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                Log.e("WIFISTATEFILE", "the wifi state is : " + status);

                if (status && isFileDownloading && !_fileCompletelyDownloaded) {
//                    Log.e(TAG, "the wifi state is; "+status+"file download status: "+isFileDownloading);
                    new downloadTask().execute();
                }
            }
        };
        registerReceiver(receiverDownload, wifiIntentFilter);
    }

    @Override
    public void onPause() {

        super.onPause();
        unregisterReceiver(receiverDownload);
    }


    private class downloadTask extends AsyncTask<Void,String,Void>{

        int total;
        Thread _wifiScanThread;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.e("WIFISTATEFILE", "Present state of WIFI: " + wifiManager.isWifiEnabled());

            _wifiScanThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while(!wifiManager.isWifiEnabled())
                    {
                        Intent wifiIntent = new Intent(MainActivity.this, receiverDownload.getClass());
                        wifiIntent.addCategory(WifiManager.WIFI_STATE_CHANGED_ACTION);
                        context.sendBroadcast(wifiIntent);
                    }
                }
            });

        }

        @Override
        protected Void doInBackground(Void... params) {

//            Log.e(TAG, "AT START of do in background method");
            if(wifiManager.isWifiEnabled())
            {
                while(!_fileCompletelyDownloaded){

                    wifiInfo = (WifiInfo) wifiManager.getConnectionInfo();
//                    Log.e(TAG, "SSID: "+ wifiInfo.getSSID());
                    Conn_Name = wifiInfo.getSSID();
                    SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
                    Conn_Time = sdf.format(new Date());

                    if(_anyWifiDownload)
                        connectAndGetFile();
                    else if(_onlyRUWifiDownload)
                    {
//                        Log.e(TAG, "Download only on RU wifi - condition");
                        if (wifiInfo.getSSID().equals("\"PowerPuffGirls\""))  //wifiInfo.getSSID().equals("\"RUWireless\"") || wifiInfo.getSSID().equals("\"RUWireless_Secure\"") || wifiInfo.getSSID().equals("\"LAWN\""))
                        {
                            Log.e(TAG, "Connected to PPG wifi");
                            Log.e(TAG, "Connection Time is : " + Conn_Time);
                            connectAndGetFile();
                        }
                    }
                }
            }
            else
            {
//                Log.e(TAG, "WIFI is not enabled, lets wait for wifi");
                isFileDownloading = true;
                _fileCompletelyDownloaded = false;
                _wifiScanThread.start();
            }

//            Log.e("WIFISTATEFILE", "End of Do In Background method");
            return null;
        }

        public void connectAndGetFile() {

            total = 0;
            try {
                isFileDownloading = true;
                URL downloadURL = new URL(myURLLink);

                HttpURLConnection _connection = (HttpURLConnection) downloadURL.openConnection();
                _connection.setRequestMethod("GET");
                _connection.setDoOutput(true);
                _connection.connect();

                int length = _connection.getContentLength();
//                Log.e("FILEDOWNLOAD", "size of file is: " + length);

                String NameOfFile = URLUtil.guessFileName(myURLLink, null, MimeTypeMap.getFileExtensionFromUrl(myURLLink));

                File _file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), NameOfFile);

                if(_file.exists()&&_fileCompletelyDownloaded) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showAlert("File already exists! It will be rewritten", "OK");
                        }
                    });
                    _file.delete();
                }

                _file.createNewFile();

                InputStream _fileInputStream = _connection.getInputStream();
                FileOutputStream _fileoutput = new FileOutputStream(_file);
                byte[] buffer = new byte[length];
                int byteCount = 0;

                while (((byteCount = _fileInputStream.read(buffer))) > 0) {

                    total += byteCount;

                    if(_onlyRUWifiDownload && !wifiInfo.getSSID().equals("\"PowerPuffGirls\"")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "DISCONNECTED FROM RU WiFi", Toast.LENGTH_LONG).show();
                            }
                        });
                        break;
                    }

                    publishProgress("" + String.valueOf((int) ((total * 100) / length)));
                    _fileoutput.write(buffer, 0, byteCount);
                }
                _fileoutput.close();

                if (total == length) {
                    _fileCompletelyDownloaded = true;
                    isFileDownloading = false;
                    storeDataInTextFile(NameOfFile);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
//                            Toast.makeText(MainActivity.this, "Updating Log file", Toast.LENGTH_LONG).show();
                            if(!_showLogButton.isChecked())
                                textDownloadLogs.setVisibility(View.INVISIBLE);
                            updateLogText();
                        }
                    });


                    Intent _intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    _intent.setData(Uri.fromFile(_file));
                    sendBroadcast(_intent);
                }
                else if (total < length) {
                    _fileCompletelyDownloaded = false;
                    _file.delete();
                    _wifiScanThread.start();
                }

            }catch(FileNotFoundException e){
                e.printStackTrace();
            }catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        protected void onProgressUpdate(String... progress) {

            _busyProgressBar.setVisibility(View.VISIBLE);
            textProgress.setVisibility(View.VISIBLE);
            _busyProgressBar.setProgress(Integer.parseInt(progress[0]));
            if(_busyProgressBar.getProgress()==100)
            {
//                Toast.makeText(MainActivity.this, "COMPLETED FILE DOWNLOAD", Toast.LENGTH_SHORT).show();
                textProgress.setText("DOWNLOAD COMPLETED");
                BtnDownloadDone.setVisibility(View.VISIBLE);
                BtnDownloadDone.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        textProgress.setText("Downloading file...");
                        textProgress.setVisibility(View.INVISIBLE);
                        _busyProgressBar.setProgress(0);
                        _busyProgressBar.setVisibility(View.INVISIBLE);
                        BtnDownloadDone.setVisibility(View.INVISIBLE);
                    }
                });

            }
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
//            Log.e(TAG, "COMPLETED FILE DOWNLOAD");
        }
    }

    public void enableRUWifiDownload(View view) {
        _onlyRUWifiDownload = true;
        _anyWifiDownload = false;
        if(_onlyRUWifiButton.isChecked())
            _onlyWifiButton.setChecked(false);
    }

    public void enableAnyWifiDownload(View view) {
        _anyWifiDownload = true;
        _onlyRUWifiDownload = false;
        if(_onlyWifiButton.isChecked())
            _onlyRUWifiButton.setChecked(false);
    }

    public void enableLogDisplay(View view) {

        if( _showLogButton.isChecked()) {
            textDownloadLogs.setVisibility(View.VISIBLE);
            updateLogText();
        }
        else if(!_showLogButton.isChecked())
        {
            textDownloadLogs.setVisibility(View.INVISIBLE);
        }
    }

    private void updateLogText()
    {
        try {
            InputStream is = openFileInput(File_Name);
            if (is != null) {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader reader = new BufferedReader(isr);
                String str;
                StringBuilder sb = new StringBuilder();
                while ((str = reader.readLine()) != null) {
                    sb.append(str + "\n");
                }
                is.close();
                textDownloadLogs.setText(sb.toString());
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void storeDataInTextFile(String _nFile) {

        try {
            OutputStreamWriter _out = new OutputStreamWriter(openFileOutput(File_Name,0));
            _out.write("File Name: "+_nFile + "\t" + Conn_Name + "\n"+Conn_Time);
            _out.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void showExitAlert() {
        AlertDialog.Builder exitAlertDialog = new AlertDialog.Builder(context);
        exitAlertDialog.setMessage("Do you want to leave this wonderful app? :'( Are you sureeeee?");

        exitAlertDialog.setPositiveButton("YES", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });

        exitAlertDialog.setNegativeButton("NO", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        exitAlertDialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            SettingsRadioButtons.setVisibility(View.VISIBLE);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAlert(String message, String _pText)
    {
        AlertDialog.Builder exitAlertDialog = new AlertDialog.Builder(context);
        exitAlertDialog.setMessage(message);
        exitAlertDialog.setPositiveButton(_pText, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        exitAlertDialog.show();
    }

}


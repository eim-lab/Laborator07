package ro.pub.cs.systems.eim.bluetoothchatapp;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Objects;

import ro.pub.cs.systems.eim.bluetoothchatapp.DeviceListActivity;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private ChatUtils chatUtils;
    private EditText edCreateMessage;
    private ArrayAdapter<String> adapterMainChat;
    private String connectedDevice;

    private void initViews() {
        ListView listMainChat = findViewById(R.id.list_conversation);
        edCreateMessage = findViewById(R.id.ed_enter_message);
        Button btnSendMessage = findViewById(R.id.btn_send_msg);

        // Adaptorul funcționează ca o legatura între Componenta UI și o sursa de date.
        // Acesta convertește datele din sursele de date în elemente vizuale care pot fi afișate în Componenta UI.
        adapterMainChat = new ArrayAdapter<>(this, R.layout.message_layout);

        // Schimbarile din adaptor se vor vedea imediat in interfata grafica. In acest
        // exemplue, listMainChat de tip ListView va fi actualizata cand facem schimbari
        // la adapterMainChat
        listMainChat.setAdapter(adapterMainChat);

        // Butonul send va chema functia sendMessage pe care o vom defini mai tariu
        btnSendMessage.setOnClickListener(view -> sendMessage());
    }

    private void initBluetooth() {
        // Vom lua o referinta la adaptorul de bluetooth
        // de pe telefon. Putem vedea acest adaptor ca o interfata
        // cu driver-ul de bluetooth.
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "No bluetooth found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initBluetooth();
        // Creeam o instanta a clasei ChatUtils. Clasa o vom
        // defini mai tarziu si o vom folosi pentru a
        // realiza comunicatia prin bluetooth pe un thread separat
        // de cel principal. Comunicatia dintr MainActivity si
        // ChatUtils se va face prin intermediul unei instante de
        // Handler.
        chatUtils = new ChatUtils(MainActivity.this, handler);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Vom reponrni comunicatia cu chatUtils
        if (chatUtils != null && chatUtils.getState() == ChatUtils.STATE_NONE) {
            chatUtils.start();
        }
    }


    private void sendMessage() {
        String message = edCreateMessage.getText().toString();
        if (!message.isEmpty()) {
            edCreateMessage.setText("");
            chatUtils.write(message.getBytes());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_enable_bluetooth) {
            enableBluetooth();
            return true;
        } else if (item.getItemId() == R.id.menu_search_devices) {
            checkPermissions();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(ACCESS_FINE_LOCATION);
        } else {
            selectDeviceLauncher.launch(new Intent(this, DeviceListActivity.class));
        }
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this).setCancelable(false).setMessage("Location permission is required.\nPlease grant").setPositiveButton("Grant", (dialogInterface, i) -> checkPermissions()).setNegativeButton("Deny", (dialogInterface, i) -> finish()).show();
    }

    private void enableBluetooth() {
        // TODO
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatUtils != null) {
            chatUtils.stop();
        }
    }

    private void setConnectedDevice(Message message) {
        connectedDevice = message.getData().getString(Constants.DEVICE_NAME);
        Toast.makeText(MainActivity.this, connectedDevice, Toast.LENGTH_SHORT).show();
    }

    private void displayToast(Message message) {
        Toast.makeText(MainActivity.this, message.getData().getString(Constants.TOAST), Toast.LENGTH_SHORT).show();
    }

    private final ActivityResultLauncher<Intent> selectDeviceLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            String address = Objects.requireNonNull(result.getData()).getStringExtra("deviceAddress");
            chatUtils.connect(bluetoothAdapter.getRemoteDevice(address));
        }
    });

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (isGranted) {
            selectDeviceLauncher.launch(new Intent(MainActivity.this, DeviceListActivity.class));
        } else {
            showPermissionDialog();
        }
    });

    private final Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case Constants.MESSAGE_STATE_CHANGED:
                    updateConnectionState(message.arg1);
                    break;
                case Constants.MESSAGE_WRITE:
                    displayMessage("Me", message.obj);
                    break;
                case Constants.MESSAGE_READ:
                    displayMessage(connectedDevice, message.obj);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    setConnectedDevice(message);
                    break;
                case Constants.MESSAGE_TOAST:
                    displayToast(message);
                    break;
            }
            return false;
        }
    });

    private void updateConnectionState(int state) {
        CharSequence subTitle = switch (state) {
            case ChatUtils.STATE_NONE, ChatUtils.STATE_LISTEN -> "Not Connected";
            case ChatUtils.STATE_CONNECTING -> "Connecting...";
            case ChatUtils.STATE_CONNECTED -> "Connected: " + connectedDevice;
            default -> "Unknown State";
        };
        Objects.requireNonNull(getSupportActionBar()).setSubtitle(subTitle);
    }

    private void displayMessage(String sender, Object messageObj) {
        byte[] buffer = (byte[]) messageObj;
        String message = new String(buffer);
        adapterMainChat.add(sender + ": " + message);
    }
}


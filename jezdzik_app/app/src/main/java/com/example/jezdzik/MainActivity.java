package com.example.jezdzik;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "BluetoothCamera";
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    private static final UUID DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic characteristic;
    private ImageView imageView;
    private List<Byte> imageBuffer = new ArrayList<>();

    private int counter = 0;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        imageView = findViewById(R.id.image);

        Button connectButton = findViewById(R.id.connect);
        connectButton.setOnClickListener(v -> checkPermissionsAndConnect());

        Button captureButton = findViewById(R.id.capture);
        captureButton.setOnClickListener(v -> sendText("capture"));

        // Adding buttons and their click listeners
        Button forwardButton = findViewById(R.id.forward);
        forwardButton.setOnTouchListener((v, event) -> handleTouch(event, "f", "a"));

        Button backwardButton = findViewById(R.id.backward);
        backwardButton.setOnTouchListener((v, event) -> handleTouch(event, "b", "a"));

        Button leftButton = findViewById(R.id.left);
        leftButton.setOnTouchListener((v, event) -> handleTouch(event, "l", "a"));

        Button rightButton = findViewById(R.id.right);
        rightButton.setOnTouchListener((v, event) -> handleTouch(event, "r", "a"));

        Button button1 = findViewById(R.id.button1);
        button1.setOnClickListener(v -> sendText("z"));

        Button button2 = findViewById(R.id.button2);
        button2.setOnClickListener(v -> sendText("y"));

        Button button3 = findViewById(R.id.button3);
        button3.setOnClickListener(v -> sendText("x"));
    }

    @SuppressLint("MissingPermission")
    private boolean handleTouch(MotionEvent event, String downText, String upText) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            sendText(downText);
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            sendText(upText);
        }
        return true;
    }

    private void checkPermissionsAndConnect() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_PERMISSIONS);
        } else {
            connectToDevice();
        }
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice() {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice("34:98:7A:B6:D7:C6");
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothAdapter.STATE_CONNECTED) {
                gatt.discoverServices();
            } else if (newState == BluetoothAdapter.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.");
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    gatt.setCharacteristicNotification(characteristic, true);
                    Log.w(TAG, "descriptor");
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(DESCRIPTOR_UUID);
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

//        @Override
//        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
//
//        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.w(TAG, "changed");
            byte[] data = characteristic.getValue();
            for (byte b : data) {
                imageBuffer.add(b);
            }
            Log.w(TAG, "changed " + imageBuffer.size());
            // Check for the end of image marker (0xFFD9)
            if (imageBuffer.size() >= 2 && imageBuffer.get(imageBuffer.size() - 2) == (byte) 0xFF && imageBuffer.get(imageBuffer.size() - 1) == (byte) 0xD9) {
                Log.w(TAG, "end");
                byte[] imageBytes = new byte[imageBuffer.size()];
                for (int i = 0; i < imageBuffer.size(); i++) {
                    imageBytes[i] = imageBuffer.get(i);
                }
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                imageBuffer.clear();
            }
        }
//        @Override
//        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
//            byte[] data = characteristic.getValue();
//            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
//            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
//            runOnUiThread(() -> imageView.setImageBitmap(bitmap));
//        }

    };

    @SuppressLint("MissingPermission")
    private void sendText(String text) {
        if (characteristic != null) {
            characteristic.setValue(text.getBytes());
            bluetoothGatt.writeCharacteristic(characteristic);
        } else {
            Toast.makeText(this, "Not connected to a Bluetooth Device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                sendText("f");
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                sendText("b");
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                sendText("l");
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                sendText("r");
                return true;
            case KeyEvent.KEYCODE_1:
                sendText("z");
                return true;
            case KeyEvent.KEYCODE_2:
                sendText("y");
                return true;
            case KeyEvent.KEYCODE_3:
                sendText("x");
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToDevice();
            } else {
                Toast.makeText(this, "Permissions required for Bluetooth", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

package expo.modules.connector;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

// cd ./android && ./gradlew :connector:testDebugUnitTest

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.O_MR1}) // Use a specific SDK version supported by Robolectric
public class BleSingletonTest {

    @Mock
    private Context mockContext;

    @Mock
    private BridgerBleManager mockBleManager;

    @Mock
    private BluetoothManager mockBluetoothManager;

    @Mock
    private BluetoothAdapter mockBluetoothAdapter;

    @Mock
    private BluetoothDevice mockDevice;

    private BleSingleton bleSingleton;

    private final String VALID_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb"; // Example UUID
    private final String VALID_CHAR_UUID = "00002a37-0000-1000-8000-00805f9b34fb"; // Example UUID
    private final String TEST_MAC_ADDRESS = "00:11:22:33:44:55";

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock Context behavior
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(mockBluetoothManager);
        when(mockBluetoothManager.getAdapter()).thenReturn(mockBluetoothAdapter);

        // Create the singleton instance with the mocked manager
        bleSingleton = BleSingleton.createInstanceForTests(mockContext, mockBleManager);
    }

    @Test
    public void setup_configuresManagerWithUUIDs() throws Exception {
        bleSingleton.setup(VALID_SERVICE_UUID, VALID_CHAR_UUID);

        verify(mockBleManager).setConfiguration(
                UUID.fromString(VALID_SERVICE_UUID),
                UUID.fromString(VALID_CHAR_UUID)
        );
    }

    @Test(expected = BleSingleton.ConnectionActiveException.class)
    public void setup_throwsException_whenConnected() throws Exception {
        when(mockBleManager.isConnected()).thenReturn(true);
        bleSingleton.setup(VALID_SERVICE_UUID, VALID_CHAR_UUID);
    }

    @Test(expected = BleSingleton.NotConfiguredException.class)
    public void connect_throwsException_whenNotConfigured() throws Exception {
        // Calling connect without calling setup() first
        bleSingleton.connect(TEST_MAC_ADDRESS);
    }

    @Test(expected = BleSingleton.ConnectionActiveException.class)
    public void connect_throwsException_whenAlreadyConnected() throws Exception {
        bleSingleton.setup(VALID_SERVICE_UUID, VALID_CHAR_UUID);
        when(mockBleManager.isConnected()).thenReturn(true);

        bleSingleton.connect(TEST_MAC_ADDRESS);
    }

    @Test(expected = BleSingleton.BluetoothUnavailableException.class)
    public void connect_throwsException_whenBluetoothDisabled() throws Exception {
        bleSingleton.setup(VALID_SERVICE_UUID, VALID_CHAR_UUID);
        when(mockBluetoothAdapter.isEnabled()).thenReturn(false);

        bleSingleton.connect(TEST_MAC_ADDRESS);
    }

    @Test
    public void listeners_receiveDataUpdates() {
        // Setup a mock listener
        BleSingleton.BleDataListener mockListener = mock(BleSingleton.BleDataListener.class);
        bleSingleton.addDataListener(mockListener);

        // Capture the message listener passed to the manager
        ArgumentCaptor<BridgerBleManager.BridgerMessageListener> captor = ArgumentCaptor.forClass(BridgerBleManager.BridgerMessageListener.class);
        // Re-create logic to ensure capturing happens in constructor if possible, 
        // but here the constructor was called in setUp. 
        // We verify that setMessageListener was called on the mock manager.
        verify(mockBleManager).setMessageListener(captor.capture());

        // Simulate receiving a message
        BridgerMessage testMessage = new BridgerMessage(BridgerMessage.MessageType.CLIPBOARD, "Test Data");
        captor.getValue().onMessageReceived(testMessage);

        // Verify the listener received it
        verify(mockListener).onDataReceived(testMessage);
    }
    
    @Test
    public void listeners_removesWeakReferences() {
        // This test is tricky with WeakReference in a mocked environment without forcing GC,
        // but we can verify add/remove logic basics.
        BleSingleton.BleDataListener mockListener = mock(BleSingleton.BleDataListener.class);
        bleSingleton.addDataListener(mockListener);
        bleSingleton.removeDataListener(mockListener);

        ArgumentCaptor<BridgerBleManager.BridgerMessageListener> captor = ArgumentCaptor.forClass(BridgerBleManager.BridgerMessageListener.class);
        verify(mockBleManager).setMessageListener(captor.capture());
        
        BridgerMessage testMessage = new BridgerMessage(BridgerMessage.MessageType.CLIPBOARD, "Test Data");
        captor.getValue().onMessageReceived(testMessage);

        // Should NOT receive data
        verify(mockListener, never()).onDataReceived(any());
    }

    // =============================================================================================
    // TODO: ADDITIONAL TEST CASES
    // =============================================================================================

    // Test Case: Successful connection initiation
    // Verify that bleManager.connect() is called with the correct BluetoothDevice
    // @Test
    // public void connect_initiatesConnectionSuccessfully() throws Exception {
    //     bleSingleton.setup(VALID_SERVICE_UUID, VALID_CHAR_UUID);
    //     when(mockBluetoothAdapter.isEnabled()).thenReturn(true);
    //     when(mockBluetoothAdapter.getRemoteDevice(TEST_MAC_ADDRESS)).thenReturn(mockDevice);
    //
    //     bleSingleton.connect(TEST_MAC_ADDRESS);
    //
    //     verify(mockBleManager).connect(mockDevice);
    // }

    // Test Case: Disconnect when connected
    // Verify that bleManager.disconnect().enqueue() is called
    // @Test
    // public void disconnect_callsBleManagerDisconnectWhenConnected() {
    //     when(mockBleManager.isConnected()).thenReturn(true);
    //     bleSingleton.disconnect();
    //     verify(mockBleManager).disconnect();
    // }

    // Test Case: Disconnect when not connected (should do nothing)
    // Verify that bleManager.disconnect() is NOT called
    // @Test
    // public void disconnect_doesNothingWhenNotConnected() {
    //     when(mockBleManager.isConnected()).thenReturn(false);
    //     bleSingleton.disconnect();
    //     verify(mockBleManager, never()).disconnect();
    // }

    // Test Case: Successful data send
    // Verify that bleManager.performWrite() is called with the correct Data
    // @Test
    // public void send_sendsDataSuccessfully() throws Exception {
    //     when(mockBleManager.isConnected()).thenReturn(true);
    //     BridgerMessage testMessage = new BridgerMessage(BridgerMessage.MessageType.CLIPBOARD, "Test Content");
    //     bleSingleton.send(testMessage);
    //
    //     verify(mockBleManager).performWrite(any(Data.class)); // Can use ArgumentCaptor for exact Data matching
    // }

    // Test Case: Send data when not connected
    // Verify that NotConnectedException is thrown
    // @Test(expected = BleSingleton.NotConnectedException.class)
    // public void send_throwsExceptionWhenNotConnected() throws Exception {
    //     when(mockBleManager.isConnected()).thenReturn(false);
    //     BridgerMessage testMessage = new BridgerMessage(BridgerMessage.MessageType.CLIPBOARD, "Test Content");
    //     bleSingleton.send(testMessage);
    // }

    // Test Case: addConnectionListener adds listener
    // Verify that the listener is added to the Observable
    // @Test
    // public void addConnectionListener_addsListener() {
    //     BleSingleton.BleConnectionListener mockConnectionListener = mock(BleSingleton.BleConnectionListener.class);
    //     bleSingleton.addConnectionListener(mockConnectionListener);
    //     // This would require reflection to verify internal Observable state or a more complex mock setup
    //     // For now, rely on existing tests that verify listener *invocation*
    // }

    // Test Case: removeConnectionListener removes listener
    // Verify that the listener is removed from the Observable
    // @Test
    // public void removeConnectionListener_removesListener() {
    //     BleSingleton.BleConnectionListener mockConnectionListener = mock(BleSingleton.BleConnectionListener.class);
    //     bleSingleton.addConnectionListener(mockConnectionListener);
    //     bleSingleton.removeConnectionListener(mockConnectionListener);
    //     // Similar to add, verification of removal is tricky without exposing Observable internals.
    //     // Implicitly tested if subsequent notifications don't reach it.
    // }

    // Test Case: ConnectionObserver onDeviceReady sends device name
    // Verify that bleManager.performWrite is called with DEVICE_NAME message
    // @Test
    // public void onDeviceReady_sendsDeviceName() {
    //     // Simulate onDeviceReady being called on the internal ConnectionObserver
    //     // This requires accessing the private connectionObserver field, possibly via reflection or by making it package-private for tests.
    //     // Alternatively, mock BleManager and verify performWrite when its onDeviceReady is triggered.
    // }

    // ... more detailed tests for ConnectionObserver callbacks and their listener notifications ...

}

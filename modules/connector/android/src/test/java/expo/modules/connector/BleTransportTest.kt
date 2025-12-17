package expo.modules.connector

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import expo.modules.connector.interfaces.IBleTransport
import expo.modules.connector.models.Message
import expo.modules.connector.transports.ble.BleManager
import expo.modules.connector.transports.ble.BleTransport
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import no.nordicsemi.android.ble.observer.ConnectionObserver
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class BleTransportTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK(relaxed = true)
    lateinit var mockContext: Context

    @MockK(relaxed = true)
    lateinit var mockBleManager: BleManager

    @MockK(relaxed = true)
    lateinit var mockBluetoothManager: BluetoothManager

    @MockK(relaxed = true)
    lateinit var mockBluetoothAdapter: BluetoothAdapter

    @MockK(relaxed = true)
    lateinit var mockDevice: BluetoothDevice

    private lateinit var bleTransport: BleTransport
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val VALID_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb"
    private val VALID_CHAR_UUID = "00002a37-0000-1000-8000-00805f9b34fb"
    private val TEST_MAC_ADDRESS = "00:11:22:33:44:55"

    @Before
    fun setUp() {
        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBluetoothManager
        every { mockBluetoothManager.adapter } returns mockBluetoothAdapter
        every { mockBluetoothAdapter.getRemoteDevice(TEST_MAC_ADDRESS) } returns mockDevice
        every { mockDevice.address } returns TEST_MAC_ADDRESS

        // BleTransport calls setConfiguration and setupCallbacks in init
        bleTransport = BleTransport(mockContext, VALID_SERVICE_UUID, VALID_CHAR_UUID, mockBleManager, testScope)
    }

    @Test
    fun init_configuresManagerWithUUIDs() {
        verify {
            mockBleManager.setConfiguration(
                UUID.fromString(VALID_SERVICE_UUID),
                UUID.fromString(VALID_CHAR_UUID)
            )
        }
    }

    @Test
    fun connect_checksBluetoothEnabled() = runTest(testDispatcher) {
        every { mockBluetoothAdapter.isEnabled } returns false
        
        bleTransport.connect(TEST_MAC_ADDRESS)
        
        assertEquals(IBleTransport.ConnectionState.POWERED_OFF, bleTransport.connectionState.value)
    }

    @Test
    fun send_returnsFalseWhenNotReady() = runTest(testDispatcher) {
        val message = Message(Message.Type.CLIPBOARD, "Test Content")
        val result = bleTransport.send(message)
        
        assertFalse(result)
        verify(exactly = 0) { mockBleManager.performWrite(any()) }
    }

    @Test
    fun incomingMessages_emitsParsedData() = runTest(testDispatcher) {
        val slot = slot<(BluetoothDevice, no.nordicsemi.android.ble.data.Data) -> Unit>()
        
        // Capture callback assignment
        verify { mockBleManager.onDataReceived = capture(slot) }
        
        val testMessage = Message(Message.Type.CLIPBOARD, "Test Data")
        val jsonData = no.nordicsemi.android.ble.data.Data.from(testMessage.toJson())
        
        var receivedMessage: Message? = null
        val job = launch {
            receivedMessage = bleTransport.incomingMessages.first()
        }
        
        slot.captured.invoke(mockDevice, jsonData)
        
        assertNotNull(receivedMessage)
        assertEquals(testMessage.value, receivedMessage?.value)
        assertEquals(testMessage.getType(), receivedMessage?.getType())
        
        job.cancel()
    }

    @Test
    fun connectionState_updatesOnObserverCallbacks() {
        val slot = slot<ConnectionObserver>()
        verify { mockBleManager.connectionObserver = capture(slot) }
        val observer = slot.captured

        observer.onDeviceConnecting(mockDevice)
        assertEquals(IBleTransport.ConnectionState.CONNECTING, bleTransport.connectionState.value)

        observer.onDeviceConnected(mockDevice)
        assertEquals(IBleTransport.ConnectionState.CONNECTED, bleTransport.connectionState.value)

        observer.onDeviceReady(mockDevice)
        assertEquals(IBleTransport.ConnectionState.READY, bleTransport.connectionState.value)

        observer.onDeviceDisconnected(mockDevice, 0)
        assertEquals(IBleTransport.ConnectionState.DISCONNECTED, bleTransport.connectionState.value)
    }
}
import { useBleScanner } from "@/hooks/useBleScanner";
import { useBluetoothPermissions } from "@/hooks/useBluetoothPermissions";
import { Button, FlatList, Platform, StyleSheet, Text, View } from "react-native";
import { RESULTS } from 'react-native-permissions';

export default function Index() {
  const { status, isLoading, request, allPermissionsGranted, showPermissionRationale } = useBluetoothPermissions();
  const { isScanning, foundDevices, scanError, startScan, stopScan, clearDevices } = useBleScanner();

  if (isLoading) {
    return (
      <View style={styles.container}>
        <Text>Checking Bluetooth permissions...</Text>
      </View>
    );
  }

  if (!allPermissionsGranted) {
    return (
      <View style={styles.container}>
        <Text style={styles.title}>Bluetooth Permissions Status</Text>
        {Platform.OS === 'android' ? (
          <>
            {status.android && Object.entries(status.android).map(([perm, stat]) => (
              <View key={perm} style={styles.permissionItem}>
                <Text>{perm}: <Text style={{ color: stat === RESULTS.GRANTED ? 'green' : 'red' }}>{stat}</Text></Text>
                {stat === RESULTS.DENIED || stat === RESULTS.BLOCKED ? (
                  <Button title="Show Rationale / Open Settings" onPress={() => showPermissionRationale()} />
                ) : null}
              </View>
            ))}
            <Button title="Request Permissions Again" onPress={request} />
          </>
        ) : (
          <Text>Bluetooth permissions are only applicable on Android.</Text>
        )}
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>BLE Scanner</Text>
      <View style={styles.buttonContainer}>
        <Button title="Start Scan" onPress={startScan} disabled={isScanning} />
        <Button title="Stop Scan" onPress={stopScan} disabled={!isScanning} />
        <Button title="Clear Devices" onPress={clearDevices} />
      </View>

      {isScanning && <Text>Scanning for devices...</Text>}
      {scanError && (
        <Text style={styles.errorText}>
          Scan Error: {scanError.message} (Code: {scanError.code})
        </Text>
      )}

      <Text style={styles.subtitle}>Found Devices ({foundDevices.length}):</Text>
      <FlatList
        style={styles.list}
        data={foundDevices}
        keyExtractor={(item) => item.address}
        renderItem={({ item }) => (
          <View style={styles.deviceItem}>
            <Text style={styles.deviceName}>{item.name || 'N/A'}</Text>
            <Text style={styles.deviceInfo}>Address: {item.address}</Text>
            <Text style={styles.deviceInfo}>RSSI: {item.rssi}</Text>
          </View>
        )}
        ListEmptyComponent={<Text>No devices found yet.</Text>}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 20,
    backgroundColor: '#f0f0f0',
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    marginBottom: 20,
    color: '#333',
  },
  subtitle: {
    fontSize: 18,
    fontWeight: "600",
    marginTop: 20,
    marginBottom: 10,
    color: '#555',
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    width: '100%',
    marginBottom: 20,
  },
  errorText: {
    color: 'red',
    marginTop: 10,
    textAlign: 'center',
  },
  list: {
    width: '100%',
    marginTop: 10,
  },
  deviceItem: {
    backgroundColor: '#fff',
    padding: 15,
    borderRadius: 8,
    marginBottom: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 1.41,
    elevation: 2,
  },
  deviceName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#333',
  },
  deviceInfo: {
    fontSize: 14,
    color: '#666',
  },
  permissionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    width: '100%',
    marginBottom: 10,
    paddingHorizontal: 10,
  },
});

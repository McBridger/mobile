import { router } from "expo-router";
import { useEffect } from 'react'; // Import useEffect
import { Button, Platform, StyleSheet, Text, View } from "react-native";
import { RESULTS } from 'react-native-permissions';
import { useBluetoothPermissions } from "../hooks/useBluetoothPermissions";

export default function PermissionsScreen() {
  const { status, isLoading, request, allPermissionsGranted, showPermissionRationale } = useBluetoothPermissions();

  useEffect(() => {
    if (!isLoading && allPermissionsGranted) router.replace('/');
  }, [isLoading, allPermissionsGranted]);

  if (isLoading) {
    return (
      <View style={styles.container}>
        <Text>Checking Bluetooth permissions...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Bluetooth Permissions Required</Text>
      <Text style={styles.description}>
        This app needs access to Bluetooth to discover and connect to nearby devices.
        Location permission is also required for Bluetooth scanning on Android.
        Please grant the necessary permissions to continue.
      </Text>

      {Platform.OS === 'android' && status.android && Object.entries(status.android).map(([perm, stat]) => (
        <View key={perm} style={styles.permissionItem}>
          <Text>{perm}: <Text style={{ color: stat === RESULTS.GRANTED ? 'green' : 'red' }}>{stat}</Text></Text>
          {stat === RESULTS.DENIED || stat === RESULTS.BLOCKED ? (
            <Button title="Show Rationale / Open Settings" onPress={() => showPermissionRationale()} />
          ) : null}
        </View>
      ))}

      {!allPermissionsGranted && (
        <View style={styles.buttonContainer}>
          <Button title="Grant Permissions" onPress={request} />
        </View>
      )}

      {allPermissionsGranted && (
        <Text style={styles.grantedText}>All required Bluetooth permissions granted!</Text>
      )}
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
    marginBottom: 15,
    textAlign: 'center',
  },
  description: {
    fontSize: 16,
    textAlign: 'center',
    marginBottom: 30,
    color: '#555',
  },
  permissionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    width: '100%',
    marginBottom: 10,
    paddingHorizontal: 10,
    backgroundColor: '#fff',
    paddingVertical: 10,
    borderRadius: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 1.41,
    elevation: 2,
  },
  buttonContainer: {
    marginTop: 20,
    // You can add padding or other styles to the container if needed
  },
  grantedText: {
    marginTop: 20,
    fontSize: 18,
    color: 'green',
    fontWeight: 'bold',
  },
});

import { useBluetoothPermissions } from "@/hooks/useBluetoothPermissions";
import { Button, Platform, StyleSheet, Text, View } from "react-native";
import { RESULTS } from 'react-native-permissions';

export default function Index() {
  const { status, isLoading, request, allPermissionsGranted, showPermissionRationale } = useBluetoothPermissions();

  if (isLoading) {
    return (
      <View style={styles.container}>
        <Text>Checking Bluetooth permissions...</Text>
      </View>
    );
  }

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

          {!allPermissionsGranted && (
            <Button title="Request Permissions Again" onPress={request} />
          )}

          {allPermissionsGranted && (
            <Text style={styles.grantedText}>All required Bluetooth permissions granted!</Text>
          )}
        </>
      ) : (
        <Text>Bluetooth permissions are only applicable on Android.</Text>
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
  },
  title: {
    fontSize: 20,
    fontWeight: "bold",
    marginBottom: 20,
  },
  permissionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    width: '100%',
    marginBottom: 10,
    paddingHorizontal: 10,
  },
  grantedText: {
    marginTop: 20,
    fontSize: 16,
    color: 'green',
  },
});

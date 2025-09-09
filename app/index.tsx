import { Redirect } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';
import { useBluetoothPermissions } from '../hooks/useBluetoothPermissions';

export default function AppEntry() {
  const { isLoading, allPermissionsGranted } = useBluetoothPermissions();

  if (isLoading) {
    return (
      <View style={styles.container}>
        <Text>Loading app and checking permissions...</Text>
      </View>
    );
  }

  if (allPermissionsGranted) {
    return <Redirect href="/devices" />;
  } else {
    return <Redirect href="/permissions" />;
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#fff',
  },
});

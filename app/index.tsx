import { BridgerHeadlessTask } from '@/utils/headless';
import { Redirect } from 'expo-router';
import { AppRegistry, StyleSheet, Text, View } from 'react-native';
import { useBluetoothPermissions } from '../hooks/useBluetoothPermissions';

AppRegistry.registerHeadlessTask(BridgerHeadlessTask.name, () => BridgerHeadlessTask);

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

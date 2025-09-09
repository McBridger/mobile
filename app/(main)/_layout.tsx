import { Redirect, Stack, useLocalSearchParams } from 'expo-router';

export default function MainLayout() {
  const params = useLocalSearchParams();

  if (params.address) {
    return <Redirect href={{ pathname: "/connection", params: { address: params.address } }} />;
  }

  return (
    <Stack>
      <Stack.Screen name="devices" options={{ headerShown: false }} />
      <Stack.Screen name="connection" options={{ title: 'Connection' }} />
    </Stack>
  );
}

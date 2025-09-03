import { Stack } from 'expo-router';

export default function MainLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ headerShown: false }} />
      {/* Add other screens for the main app here if needed */}
    </Stack>
  );
}

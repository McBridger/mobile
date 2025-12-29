import { Stack } from "expo-router";
import { SafeAreaView, useColorScheme } from "react-native";
import { MD3Theme, PaperProvider } from "react-native-paper";
import { CustomDarkTheme, CustomLightTheme } from "../theme/CustomTheme";
import { useThemeStore } from "@/hooks/useThemeStore";
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from "react";
import { 
  useFonts, 
  Roboto_400Regular, 
  Roboto_500Medium, 
  Roboto_700Bold 
} from '@expo-google-fonts/roboto';
import { 
  CormorantInfant_600SemiBold, 
  CormorantInfant_700Bold 
} from '@expo-google-fonts/cormorant-infant';

SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  const systemColorScheme = useColorScheme();
  const { themeMode } = useThemeStore();
  
  const [fontsLoaded, fontError] = useFonts({
    'Roboto-Regular': Roboto_400Regular,
    'Roboto-Medium': Roboto_500Medium,
    'Roboto-Bold': Roboto_700Bold,
    'CormorantInfant-SemiBold': CormorantInfant_600SemiBold,
    'CormorantInfant-Bold': CormorantInfant_700Bold,
  });

  useEffect(() => {
    if (fontsLoaded || fontError) {
      SplashScreen.hideAsync();
    }
  }, [fontsLoaded, fontError]);

  if (!fontsLoaded && !fontError) {
    return null;
  }

  const isDark = themeMode === "system" 
    ? systemColorScheme === "dark" 
    : themeMode === "dark";

  const activeTheme = (isDark ? CustomDarkTheme : CustomLightTheme) as MD3Theme;
  
  return (
    <PaperProvider theme={activeTheme}>
      <SafeAreaView style={{ flex: 1, backgroundColor: activeTheme.colors.background }}>
        <Stack>
          <Stack.Screen name="(main)" options={{ headerShown: false }} />
        </Stack>
      </SafeAreaView>
    </PaperProvider>
  );
}
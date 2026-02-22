import { useBluetoothPermissions } from "@/hooks/useBluetoothPermissions";
import { useAppConfig } from "@/hooks/useConfig";
import { useThemeStore } from "@/hooks/useThemeStore";
import { useConnector } from "@/modules/connector";
import {
  CormorantInfant_600SemiBold,
  CormorantInfant_700Bold,
} from "@expo-google-fonts/cormorant-infant";
import {
  Roboto_400Regular,
  Roboto_500Medium,
  Roboto_700Bold,
  useFonts,
} from "@expo-google-fonts/roboto";
import { Stack, useRouter, useSegments } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import { useEffect, useRef, useState } from "react";
import { useColorScheme } from "react-native";
import { MD3Theme, PaperProvider } from "react-native-paper";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { CustomDarkTheme, CustomLightTheme } from "../theme/CustomTheme";

SplashScreen.preventAutoHideAsync();

/**
 * Separate component to handle navigation guards.
 * This prevents the entire RootLayout (with all its providers and stacks)
 * from re-rendering every time the URL segments change.
 */
function NavigationGuard({
  isReady,
  permsLoading,
  allPermissionsGranted,
  isInitializing,
  fontsLoaded,
}: {
  isReady: boolean;
  permsLoading: boolean;
  allPermissionsGranted: boolean;
  isInitializing: boolean;
  fontsLoaded: boolean;
}) {
  const segments = useSegments();
  const router = useRouter();
  const prevIsReady = useRef(isReady);

  useEffect(() => {
    if (!fontsLoaded || permsLoading || isInitializing) return;

    const rootSegment = segments[0];
    const isPermissionsPage = rootSegment === "permissions";
    const isSetupPage = segments[1] === "setup";
    const isRoot = !rootSegment;

    // Transition detection: did we just become ready?
    const wasJustReady = isReady && !prevIsReady.current;
    prevIsReady.current = isReady;

    if (!allPermissionsGranted) {
      if (!isPermissionsPage) router.replace("/permissions");
      return;
    }

    if (!isReady) {
      if (!isSetupPage) router.replace("/setup");
      return;
    }

    // Redirect to connection if:
    // 1. We are at root
    // 2. We just finished setup (isReady transition from false to true)
    if (isRoot || (isSetupPage && wasJustReady)) {
      router.replace("/connection");
    }
  }, [
    fontsLoaded,
    permsLoading,
    isInitializing,
    allPermissionsGranted,
    isReady,
    segments,
    router,
  ]);

  return null;
}

export default function RootLayout() {
  const systemColorScheme = useColorScheme();
  const { extra } = useAppConfig();
  const { themeMode } = useThemeStore();

  const [isInitializing, setIsInitializing] = useState(true);

  const { isLoading: permsLoading, allPermissionsGranted } =
    useBluetoothPermissions();
  const isReady = useConnector((state) => state.isReady);

  const [fontsLoaded, fontError] = useFonts({
    "Roboto-Regular": Roboto_400Regular,
    "Roboto-Medium": Roboto_500Medium,
    "Roboto-Bold": Roboto_700Bold,
    "CormorantInfant-SemiBold": CormorantInfant_600SemiBold,
    "CormorantInfant-Bold": CormorantInfant_700Bold,
  });

  useEffect(() => {
    if (permsLoading) return;

    const init = async () => {
      try {
        if (!isReady && extra.MNEMONIC_LOCAL && extra.ENCRYPTION_SALT) {
          await useConnector
            .getState()
            .setup(extra.MNEMONIC_LOCAL, extra.ENCRYPTION_SALT);
        }
      } catch (e) {
        console.error("Initialization failed", e);
      } finally {
        setIsInitializing(false);
      }
    };

    init();
  }, [permsLoading, isReady, extra]);

  useEffect(() => {
    if ((fontsLoaded || fontError) && !permsLoading && !isInitializing) {
      SplashScreen.hideAsync();
    }
  }, [fontsLoaded, fontError, permsLoading, isInitializing]);

  if ((!fontsLoaded && !fontError) || permsLoading || isInitializing) {
    return null;
  }

  const isDark =
    themeMode === "system"
      ? systemColorScheme === "dark"
      : themeMode === "dark";

  const activeTheme = (isDark ? CustomDarkTheme : CustomLightTheme) as MD3Theme;

  return (
    <SafeAreaProvider>
      <PaperProvider theme={activeTheme}>
        <NavigationGuard
          isReady={isReady}
          permsLoading={permsLoading}
          allPermissionsGranted={allPermissionsGranted}
          isInitializing={isInitializing}
          fontsLoaded={fontsLoaded}
        />
        <Stack
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: activeTheme.colors.background },
          }}
        >
          <Stack.Screen name="(main)" />
        </Stack>
      </PaperProvider>
    </SafeAreaProvider>
  );
}

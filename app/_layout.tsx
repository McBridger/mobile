import { Stack } from "expo-router";
import { SafeAreaView, useColorScheme } from "react-native";
import { MD3Theme, PaperProvider } from "react-native-paper";
import { CustomDarkTheme, CustomLightTheme } from "../theme/CustomTheme";

export default function RootLayout() {
  const colorScheme = useColorScheme();
  // const activeTheme = (
  //   colorScheme === "dark" ? CustomDarkTheme : CustomLightTheme
  // ) as MD3Theme;
const activeTheme = CustomDarkTheme;
// const activeTheme = CustomLightTheme;
  return (
    <PaperProvider theme={activeTheme}>
      <SafeAreaView style={{ flex: 1 }}>
        <Stack>
          <Stack.Screen name="(main)" options={{ headerShown: false }} />
        </Stack>
      </SafeAreaView>
    </PaperProvider>
  );
}

import { MD3DarkTheme, MD3LightTheme, configureFonts } from "react-native-paper";
import { DARK_THEME, LIGHT_THEME } from "./colors";

const fontConfig = {
  displayLarge: { fontFamily: 'CormorantInfant-Bold' },
  displayMedium: { fontFamily: 'CormorantInfant-Bold' },
  displaySmall: { fontFamily: 'CormorantInfant-SemiBold' },
  headlineLarge: { fontFamily: 'CormorantInfant-Bold' },
  headlineMedium: { fontFamily: 'CormorantInfant-Bold' },
  headlineSmall: { fontFamily: 'CormorantInfant-SemiBold' },
  titleLarge: { fontFamily: 'Roboto-Bold' },
  titleMedium: { fontFamily: 'Roboto-Medium' },
  titleSmall: { fontFamily: 'Roboto-Medium' },
  labelLarge: { fontFamily: 'Roboto-Medium' },
  labelMedium: { fontFamily: 'Roboto-Medium' },
  labelSmall: { fontFamily: 'Roboto-Medium' },
  bodyLarge: { fontFamily: 'Roboto-Regular' },
  bodyMedium: { fontFamily: 'Roboto-Regular' },
  bodySmall: { fontFamily: 'Roboto-Regular' },
};

export const CustomLightTheme = {
  ...MD3LightTheme,
  version: 3,
  fonts: configureFonts({ config: fontConfig }),
  colors: {
    ...MD3LightTheme.colors,
    ...LIGHT_THEME,
  },
} as const;

export const CustomDarkTheme = {
  ...MD3DarkTheme,
  version: 3,
  fonts: configureFonts({ config: fontConfig }),
  colors: {
    ...MD3DarkTheme.colors,
    ...DARK_THEME,
  },
} as const;

export type AppTheme = typeof CustomLightTheme;

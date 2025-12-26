import { MD3DarkTheme, MD3LightTheme } from "react-native-paper";
import { DARK_THEME, LIGHT_THEME } from "./colors";

export const CustomLightTheme = {
  ...MD3LightTheme,
  version: 3,
  colors: {
    ...MD3LightTheme.colors,
    ...LIGHT_THEME,
  },
};

export const CustomDarkTheme = {
  ...MD3DarkTheme,
  version: 3,
  colors: {
    ...MD3DarkTheme.colors,
    ...DARK_THEME,
  },
};
import { ConfigPlugin, withSettingsGradle } from "@expo/config-plugins";

type Primitive = string | number | boolean;
type BlockProps = Record<string, Primitive>;
type SettingsProps = {
  // @ts-expect-error
  plugins?: string[]; // New property for plugin IDs to be added inside the plugins { } block
  [key: string]: BlockProps | Primitive;
};

/**
 * Generates a Groovy block string (e.g., buildScan { ... }).
 * @param blockName The name of the block.
 * @param props The properties within the block.
 * @returns A formatted string representing the Groovy block.
 */
function generateBlock(blockName: string, props: BlockProps): string {
  let blockContent = `
${blockName} {
`;
  for (const [key, value] of Object.entries(props)) {
    const formattedValue = typeof value === "string" ? `"${value}"` : value;
    blockContent += `  ${key} = ${formattedValue}
`;
  }
  blockContent += `}
`;
  return blockContent;
}

/**
 * Generates a Groovy property assignment string (e.g., org.gradle.caching = true).
 * @param key The property key.
 * @param value The primitive value.
 * @returns A formatted string representing the Groovy property.
 */
function generateProperty(key: string, value: Primitive): string {
  const formattedValue = typeof value === "string" ? `"${value}"` : value;
  return `
${key} = ${formattedValue}
`;
}

/**
 * Adds plugin IDs to the existing plugins { } block in settings.gradle.
 * @param settingsGradle The current contents of the settings.gradle file.
 * @param pluginIds An array of plugin IDs to add.
 * @returns The modified contents of the settings.gradle file.
 */
function addPluginsToSettingsGradle(
  settingsGradle: string,
  pluginIds: string[]
): string {
  const pluginsBlockRegex = /plugins\s*\{\s*(?<value>[\s\S]*?)\s*\}/m;
  const match = settingsGradle.match(pluginsBlockRegex);

  if (!match?.groups?.value) {
    console.warn(
      "Plugins block not found in settings.gradle. Cannot add plugins."
    );
    return settingsGradle;
  }

  const plugins = new Set(
    match.groups.value.split("\n").map((line) => line.trim())
  );
  for (const pluginId of pluginIds) plugins.add(`id("${pluginId}")`);

  // Reconstruct the settings.gradle content
  return settingsGradle.replace(
    pluginsBlockRegex,
    "plugins {\n" + [...plugins].map((line) => `  ${line}`).join("\n") + "\n}"
  );
}

/**
 * Applies settings to the settings.gradle file.
 * @param settingsGradle The current contents of the settings.gradle file.
 * @param props The properties to apply.
 * @returns The modified contents of the settings.gradle file.
 */
function applySettings(
  settingsGradle: string,
  { plugins, ...props }: SettingsProps
): string {
  let newContents = settingsGradle;

  // Handle plugins block first
  if (plugins && Array.isArray(plugins)) {
    newContents = addPluginsToSettingsGradle(newContents, plugins);
  }

  // Handle other blocks and properties
  for (const [key, value] of Object.entries(props)) {
    const isBlock =
      typeof value === "object" && value !== null && !Array.isArray(value);
    const keyExists = isBlock
      ? newContents.includes(`${key} {`)
      : newContents.includes(`${key} =`);

    if (keyExists) {
      continue;
    }

    if (isBlock) {
      newContents += generateBlock(key, value as BlockProps);
    } else {
      newContents += generateProperty(key, value as Primitive);
    }
  }

  return newContents;
}

const withGradleSettings: ConfigPlugin<SettingsProps> = (
  config,
  props = {}
) => {
  return withSettingsGradle(config, (config) => {
    if (config.modResults.language === "groovy") {
      config.modResults.contents = applySettings(
        config.modResults.contents,
        props
      );
    }
    return config;
  });
};

export default withGradleSettings;

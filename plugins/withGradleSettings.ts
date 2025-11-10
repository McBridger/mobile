import { ConfigPlugin, withSettingsGradle } from "@expo/config-plugins";

type Primitive = string | number | boolean;
type GroovyValue = Primitive | GroovyBlock;
interface GroovyBlock {
  [key: string]: GroovyValue;
}

type SettingsProps = {
  // @ts-expect-error
  pluginManagement?: string;
  // @ts-expect-error
  plugins?: string[];
  [key: string]: GroovyValue;
};

/**
 * Recursively generates a Groovy configuration string from a JavaScript object.
 * @param props The object representing the Groovy configuration.
 * @param indentLevel The current indentation level.
 * @returns A formatted string representing the Groovy configuration.
 */
function generateGroovyContent(props: GroovyBlock, indentLevel: number): string {
  const indent = "  ".repeat(indentLevel);
  let content = "";

  for (const [key, value] of Object.entries(props)) {
    if (typeof value === "object" && value !== null && !Array.isArray(value)) {
      // It's a nested block
      content += `${indent}${key} {\n`;
      content += generateGroovyContent(
        value as GroovyBlock,
        indentLevel + 1
      );
      content += `${indent}}\n`;
    } else {
      // It's a property
      const formattedValue = typeof value === "string" ? `"${value}"` : value;
      content += `${indent}${key} = ${formattedValue}\n`;
    }
  }
  return content;
}

/**
 * Adds plugin IDs to the existing plugins { } block in settings.gradle.
 * @param settingsGradle The current contents of the settings.gradle file.
 * @param newPlugins An array of plugins to add.
 * @returns The modified contents of the settings.gradle file.
 */
function addPluginsToSettingsGradle(
  settingsGradle: string,
  newPlugins: string[]
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
  for (const plugin of newPlugins) plugins.add(plugin);

  // Reconstruct the settings.gradle content
  return settingsGradle.replace(
    pluginsBlockRegex,
    "plugins {\n" + [...plugins].map((line) => `  ${line}`).join("\n") + "\n}"
  );
}

function handlePluginManagement(settingsGradle: string, value: string): string {
  const pluginManagementBlockRegex = /pluginManagement\s*\{\s*(?<value>[\s\S]*?)\s*\}/m;
  const match = settingsGradle.match(pluginManagementBlockRegex);

  if (!match?.groups?.value) {
    console.warn(
      "Plugin management block not found in settings.gradle. Cannot add plugin management."
    );
    return settingsGradle;
  }

  return settingsGradle.replace(
    pluginManagementBlockRegex,
    "pluginManagement {" + "\n" + value + "\n\n" + "  " + match.groups.value + "\n" + "}"
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
  { plugins, pluginManagement, ...props }: SettingsProps
): string {
  let newContents = settingsGradle;

  // Handle plugins block first as a special case
  if (plugins && Array.isArray(plugins)) {
    newContents = addPluginsToSettingsGradle(newContents, plugins);
  }

  if (pluginManagement) {
    newContents = handlePluginManagement(newContents, pluginManagement);
  }

  // Filter out properties that already exist in the file
  const propsToAdd: GroovyBlock = {};
  for (const [key, value] of Object.entries(props)) {
    const isBlock =
      typeof value === "object" && value !== null && !Array.isArray(value);
    const keyExists = isBlock
      ? newContents.includes(`${key} {`)
      : newContents.includes(`${key} =`);

    if (!keyExists) {
      propsToAdd[key] = value;
    }
  }

  // Append the new, non-existent properties
  if (Object.keys(propsToAdd).length > 0) {
    newContents += "\n" + generateGroovyContent(propsToAdd, 0);
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

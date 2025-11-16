import { ConfigPlugin, withGradleProperties } from '@expo/config-plugins';

type GradleProperties = Record<string, string | number | boolean>;

const withProperties: ConfigPlugin<GradleProperties> = (config, props = {}) => {
  return withGradleProperties(config, (config) => {
    for (const [key, value] of Object.entries(props)) {
      // Find an existing property with the same key
      const existingProp = config.modResults.find(p => p.type === 'property' && p.key === key);
      
      if (existingProp) {
        // Update existing property
        // @ts-expect-error
        existingProp.value = String(value);
      } else {
        // Add new property
        config.modResults.push({
          type: 'property',
          key: key,
          value: String(value),
        });
      }
    }
    return config;
  });
};

export default withProperties;

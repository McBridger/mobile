export interface TestOptions {
  skip?: boolean;
  only?: boolean;
}

export interface RegisteredTest {
  name: string;
  methodName: string;
  options: TestOptions;
}

// Storage for test metadata
export const testRegistry = new Map<any, RegisteredTest[]>();

export function Test(name: string, options: TestOptions = {}) {
  // Use rest args to satisfy any TS compiler mood
  return function (...args: any[]) {
    const target = args[0];
    const propertyKeyOrContext = args[1];

    // Legacy: second arg is a string (method name)
    if (typeof propertyKeyOrContext === "string") {
      const methodName = propertyKeyOrContext;
      const classProto = target.constructor;
      register(classProto, name, methodName, options);
      return;
    }

    // Stage 3: second arg is a context object
    if (propertyKeyOrContext && typeof propertyKeyOrContext === "object") {
      const methodName = String(propertyKeyOrContext.name);
      
      if (propertyKeyOrContext.addInitializer) {
        propertyKeyOrContext.addInitializer(function (this: any) {
          register(this.constructor, name, methodName, options);
        });
      } else {
        // Fallback for Bun's half-baked Stage 3
        // If we're here, we might be in trouble, but let's try to find the proto
        if (target && target.constructor) {
          register(target.constructor, name, methodName, options);
        }
      }
    }
  };
}

function register(classProto: any, name: string, methodName: string, options: TestOptions) {
  const tests = testRegistry.get(classProto) || [];
  if (!tests.some(t => t.methodName === methodName)) {
    tests.push({ name, methodName, options });
    testRegistry.set(classProto, tests);
  }
}

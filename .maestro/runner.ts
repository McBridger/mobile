import { BasicTest } from "./basic";
import { ErrorTest } from "./errors";

async function runTest() {
  try {
    // Run Happy Path
    await new BasicTest().run();

    // Run Error Cases
    await new ErrorTest().run();

    console.log("\n✅ All E2E Tests Passed Successfully!");
  } catch (error) {
    console.error("\n❌ E2E Test Suite Failed!");
    // We don't log the whole error object to keep CLI output clean, 
    // but in a real CI environment you might want more detail.
    process.exit(1);
  }
}

runTest();

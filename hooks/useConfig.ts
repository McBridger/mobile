import { AppConfig } from '@/app.config';
import Constants from 'expo-constants';

export const useAppConfig = (): AppConfig => {
  return Constants.expoConfig as AppConfig;
};
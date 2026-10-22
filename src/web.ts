import { WebPlugin } from '@capacitor/core';

import type { LifevitPlugin, PermissionStatus } from './definitions';

const NA = (): never => {
  throw new Error('El plugin Lifevit solo está disponible en Android.');
};

export class LifevitWeb extends WebPlugin implements LifevitPlugin {
  async requestPermissions(): Promise<PermissionStatus> {
    return { granted: false };
  }
  async scan(): Promise<void> {
    NA();
  }
  async stopScan(): Promise<void> {
    NA();
  }
  async connect(): Promise<void> {
    NA();
  }
  async disconnect(): Promise<void> {
    NA();
  }
  async isConnected(): Promise<{ connected: boolean }> {
    return { connected: false };
  }
  async getDeviceAddress(): Promise<{ address: string }> {
    return { address: '' };
  }
  async startMeasurement(): Promise<void> {
    NA();
  }
  async sendThermometerCommand(): Promise<void> {
    NA();
  }
  async rawScan(): Promise<void> {
    NA();
  }
  async rawStopScan(): Promise<void> {
    NA();
  }
  async rawConnect(): Promise<void> {
    NA();
  }
  async rawDisconnect(): Promise<void> {
    NA();
  }
}

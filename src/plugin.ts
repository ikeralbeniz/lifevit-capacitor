import { registerPlugin } from '@capacitor/core';

import type { LifevitPlugin } from './definitions';

const Lifevit = registerPlugin<LifevitPlugin>('Lifevit', {
  web: () => import('./web').then((m) => new m.LifevitWeb()),
});

export { Lifevit };

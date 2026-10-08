import { Routes } from '@angular/router';

/**
 * SPA pública do balcão: só a página do QR. O Online não tem área administrativa no MVP.
 */
export const routes: Routes = [
  {
    path: 'q/:identificadorQr',
    loadComponent: () => import('./features/qr/qr').then((m) => m.Qr),
  },
  { path: '**', redirectTo: 'q/' },
];

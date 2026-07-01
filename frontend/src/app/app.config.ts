import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { APP_INITIALIZER, ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { provideHotToastConfig } from '@ngxpert/hot-toast';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { refreshInterceptor } from './core/interceptors/refresh.interceptor';
import { AuthStore } from './core/stores/auth.store';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideAnimationsAsync(),
    provideHttpClient(
      withInterceptors([
        authInterceptor,
        refreshInterceptor,
        errorInterceptor,
      ]),
    ),
    provideCharts(withDefaultRegisterables()),
    provideHotToastConfig({
      position: 'top-right',
      duration: 3000,
      style: { fontSize: '14px' },
    }),
    {
      provide: APP_INITIALIZER,
      useFactory: (authStore: AuthStore) => () => authStore.tryRestoreSession(),
      deps: [AuthStore],
      multi: true,
    },
  ],
};

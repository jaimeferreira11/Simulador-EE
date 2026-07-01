import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { Problem } from '../models/api.model';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 || error.status === 403 || error.status === 404 || error.status === 422) {
        return throwError(() => error);
      }

      const problem = error.error as Problem | null;
      const message = problem?.detail ?? `Error ${error.status}: ${error.statusText}`;

      snackBar.open(message, 'Cerrar', {
        duration: 5000,
        panelClass: 'snackbar-error',
      });

      return throwError(() => error);
    })
  );
};

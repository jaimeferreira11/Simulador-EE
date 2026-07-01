import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthStore } from './auth.store';
import { TokenResponse } from '../models/usuario.model';

describe('AuthStore', () => {
  let store: AuthStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        AuthStore,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    store = TestBed.inject(AuthStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should start unauthenticated', () => {
    expect(store.isAuthenticated()).toBe(false);
    expect(store.user()).toBeNull();
    expect(store.rol()).toBeUndefined();
  });

  it('should set user and tokens after login', () => {
    const mockResponse: TokenResponse = {
      access_token: 'jwt-abc',
      refresh_token: 'ref-xyz',
    };

    store.login('test@test.py', 'pass123');

    const loginReq = httpMock.expectOne('/v1/auth/login');
    loginReq.flush(mockResponse);

    const meReq = httpMock.expectOne('/v1/auth/me');
    meReq.flush({
      id: 1,
      email: 'test@test.py',
      nombre_completo: 'Test User',
      rol: 'MODERADOR',
      activo: true,
    });

    expect(store.isAuthenticated()).toBe(true);
    expect(store.accessToken()).toBe('jwt-abc');
    expect(store.user()?.email).toBe('test@test.py');
    expect(store.rol()).toBe('MODERADOR');
    expect(localStorage.getItem('access_token')).toBe('jwt-abc');
    expect(localStorage.getItem('refresh_token')).toBe('ref-xyz');
  });

  it('should clear state on logout', () => {
    store.accessToken.set('jwt-abc');
    store.user.set({
      id: 1,
      email: 'test@test.py',
      nombre_completo: 'Test',
      rol: 'MODERADOR',
      activo: true,
    });
    localStorage.setItem('access_token', 'jwt-abc');
    localStorage.setItem('refresh_token', 'ref-xyz');

    store.logout();

    const req = httpMock.expectOne('/v1/auth/logout');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(store.isAuthenticated()).toBe(false);
    expect(store.user()).toBeNull();
    expect(localStorage.getItem('access_token')).toBeNull();
  });

  it('should restore session from localStorage', () => {
    localStorage.setItem('access_token', 'saved-jwt');
    localStorage.setItem('refresh_token', 'saved-ref');

    store.tryRestoreSession();

    const meReq = httpMock.expectOne('/v1/auth/me');
    meReq.flush({
      id: 1,
      email: 'restored@test.py',
      nombre_completo: 'Restored',
      rol: 'JUGADOR',
      activo: true,
    });

    expect(store.isAuthenticated()).toBe(true);
    expect(store.user()?.email).toBe('restored@test.py');
  });
});

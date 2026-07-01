import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { TokenResponse } from '../models/usuario.model';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should POST /v1/auth/login with credentials', () => {
    const mockResponse: TokenResponse = {
      access_token: 'jwt-abc',
      refresh_token: 'ref-xyz',
    };

    service.login('test@test.py', 'password123').subscribe((res) => {
      expect(res.access_token).toBe('jwt-abc');
      expect(res.refresh_token).toBe('ref-xyz');
    });

    const req = httpMock.expectOne('/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'test@test.py',
      password: 'password123',
    });
    req.flush(mockResponse);
  });

  it('should POST /v1/auth/refresh with refresh token', () => {
    const mockResponse: TokenResponse = {
      access_token: 'jwt-new',
      refresh_token: 'ref-new',
    };

    service.refresh('ref-xyz').subscribe((res) => {
      expect(res.access_token).toBe('jwt-new');
    });

    const req = httpMock.expectOne('/v1/auth/refresh');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ refresh_token: 'ref-xyz' });
    req.flush(mockResponse);
  });

  it('should GET /v1/auth/me', () => {
    service.me().subscribe((user) => {
      expect(user.email).toBe('test@test.py');
    });

    const req = httpMock.expectOne('/v1/auth/me');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 1, email: 'test@test.py', nombre_completo: 'Test', rol: 'MODERADOR', activo: true });
  });

  it('should POST /v1/auth/logout', () => {
    service.logout().subscribe();

    const req = httpMock.expectOne('/v1/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChatPage } from '../models/chat.model';

@Injectable({ providedIn: 'root' })
export class ChatApiService {
  private http = inject(HttpClient);

  listar(equipoId: number, competenciaId: number, page = 0, size = 30): Observable<ChatPage> {
    const params = new HttpParams()
      .set('competencia_id', competenciaId)
      .set('page', page)
      .set('size', size);
    return this.http.get<ChatPage>(`/v1/equipos/${equipoId}/chat`, { params });
  }

  enviar(equipoId: number, competenciaId: number, contenido: string): Observable<any> {
    return this.http.post(`/v1/equipos/${equipoId}/chat`, {
      competencia_id: competenciaId,
      contenido,
    });
  }
}

import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from './environment';

/**
 * Chamadas da página pública do QR. O identificador de aparelho (limite por aparelho, PEND-096) é um
 * id anônimo gerado no navegador — sem dado pessoal e sem cookie de rastreio.
 */
@Injectable({ providedIn: 'root' })
export class QrService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBase;
  private readonly aparelho = 'navegador-' + Math.random().toString(36).slice(2, 12);

  config(identificadorQr: string) {
    return this.http.get<ConfigQr>(`${this.base}/config-qr/${encodeURIComponent(identificadorQr)}`);
  }

  preCadastro(corpo: unknown) {
    return this.http.post<{ codigoCurto: string }>(`${this.base}/pre-cadastro`, corpo, {
      headers: new HttpHeaders({ 'X-Aparelho': this.aparelho }),
    });
  }
}

export interface ConfigQr {
  ativo: boolean;
  preCadastroExpiraHoras: number;
  codigoCurtoDigitos: number;
  termo: { termoId: string; termoVersao: number; sha256Texto: string; texto: string } | null;
  formToken: string;
}

import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { QrService } from '../../qr.service';

/**
 * Página pública do QR (balcão): formulário do visitante + aceite + código de 6 dígitos. O termo
 * exibido vem do backend (CONFIG_SITE recebida do SGT) — nunca hardcoded. Só interpolação, sem
 * innerHTML. Mobile-first.
 */
@Component({
  selector: 'app-qr',
  imports: [FormsModule],
  templateUrl: './qr.html',
  styleUrl: './qr.css',
})
export class Qr implements OnInit {
  private readonly rota = inject(ActivatedRoute);
  private readonly service = inject(QrService);

  estado: 'carregando' | 'formulario' | 'codigo' | 'erro' = 'carregando';
  erro = '';
  erroForm = '';
  termo: string | null = null;
  formToken = '';
  identificadorQr = '';
  expiraHoras = 4;
  codigo = '';
  enviando = false;

  model = {
    nome: '',
    cpf: '',
    docTipo: '',
    docNumero: '',
    empresa: '',
    telefone: '',
    email: '',
    aceite: false,
    siteWeb: '',
  };

  ngOnInit(): void {
    this.identificadorQr = this.rota.snapshot.paramMap.get('identificadorQr') ?? '';
    this.service.config(this.identificadorQr).subscribe({
      next: (c) => {
        this.formToken = c.formToken;
        this.expiraHoras = c.preCadastroExpiraHoras ?? 4;
        if (!c.ativo) {
          this.estado = 'erro';
          this.erro = 'Este QR não é mais válido.';
        } else if (!c.termo) {
          this.estado = 'erro';
          this.erro = 'Cadastro indisponível neste momento.';
        } else {
          this.termo = c.termo.texto;
          this.estado = 'formulario';
        }
      },
      error: () => {
        this.estado = 'erro';
        this.erro = 'Este QR não é mais válido.';
      },
    });
  }

  enviar(): void {
    if (!this.model.aceite || this.enviando) {
      return;
    }
    this.enviando = true;
    this.erroForm = '';
    const corpo = {
      identificadorQr: this.identificadorQr,
      formToken: this.formToken,
      aceite: true,
      site_web: this.model.siteWeb,
      pessoa: {
        nome: this.model.nome,
        cpf: this.model.cpf || null,
        documento:
          this.model.docTipo || this.model.docNumero
            ? { tipo: this.model.docTipo, numero: this.model.docNumero }
            : null,
        empresa: this.model.empresa || null,
        telefone: this.model.telefone || null,
        email: this.model.email || null,
      },
    };
    this.service.preCadastro(corpo).subscribe({
      next: (r) => {
        this.codigo = r.codigoCurto;
        this.estado = 'codigo';
        this.enviando = false;
      },
      error: (e: HttpErrorResponse) => {
        this.enviando = false;
        this.erroForm = e.error?.mensagem ?? 'Não foi possível concluir. Tente novamente.';
      },
    });
  }
}

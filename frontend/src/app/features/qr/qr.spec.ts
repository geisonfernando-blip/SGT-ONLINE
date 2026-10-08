import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { Qr } from './qr';
import { QrService } from '../../qr.service';

/**
 * Página pública do QR: abre o formulário só com site ativo + termo; envio só com aceite; sem termo,
 * mostra indisponibilidade. O termo é renderizado por interpolação (sem innerHTML).
 */
describe('Qr', () => {
  class QrMock {
    config = () =>
      of({
        ativo: true,
        preCadastroExpiraHoras: 4,
        codigoCurtoDigitos: 6,
        termo: { termoId: 't', termoVersao: 1, sha256Texto: 'a'.repeat(64), texto: 'TERMO PROVISÓRIO' },
        formToken: 'tok',
      });
    preCadastro = () => of({ codigoCurto: '123456' });
  }

  let fixture: ComponentFixture<Qr>;

  function criar(config: QrMock) {
    TestBed.configureTestingModule({
      imports: [Qr],
      providers: [
        { provide: QrService, useValue: config },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'ensaioQR0001' } } } },
      ],
    });
    fixture = TestBed.createComponent(Qr);
    fixture.detectChanges();
  }

  it('mostra o formulário e o termo quando o site está ativo', async () => {
    criar(new QrMock());
    await fixture.whenStable();
    expect(fixture.debugElement.query(By.css('form'))).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('TERMO PROVISÓRIO');
  });

  it('desabilita o envio sem o aceite do termo', async () => {
    criar(new QrMock());
    await fixture.whenStable();
    const botao = fixture.debugElement.query(By.css('button')).nativeElement as HTMLButtonElement;
    expect(botao.disabled).toBe(true);
  });

  it('mostra indisponibilidade quando não há termo', async () => {
    const semTermo = new QrMock();
    semTermo.config = () =>
      of({ ativo: true, preCadastroExpiraHoras: 4, codigoCurtoDigitos: 6, termo: null, formToken: 'tok' });
    criar(semTermo);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Cadastro indisponível');
  });
});

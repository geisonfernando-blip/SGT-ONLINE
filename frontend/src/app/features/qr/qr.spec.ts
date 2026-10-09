import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { Qr } from './qr';
import { ConfigQr, QrService } from '../../qr.service';

/**
 * Página pública do QR: formulário só com site ativo + termo; envio só com aceite; honeypot oculto;
 * código de 6 dígitos após envio; erros (400/429) mostram a mensagem do backend sem detalhe técnico.
 * O termo é renderizado por interpolação (sem innerHTML).
 */
describe('Qr', () => {
  const configPadrao: ConfigQr = {
    ativo: true,
    preCadastroExpiraHoras: 4,
    codigoCurtoDigitos: 6,
    termo: { termoId: 't', termoVersao: 1, sha256Texto: 'a'.repeat(64), texto: 'TERMO PROVISÓRIO' },
    formToken: 'tok',
  };

  class QrMock {
    config: QrService['config'] = () => of(configPadrao);
    preCadastro: QrService['preCadastro'] = () => of({ codigoCurto: '123456' });
  }

  let fixture: ComponentFixture<Qr>;
  let mock: QrMock;

  function criar(m: QrMock) {
    mock = m;
    TestBed.configureTestingModule({
      imports: [Qr],
      providers: [
        { provide: QrService, useValue: m },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'ensaioQR0001' } } } },
      ],
    });
    fixture = TestBed.createComponent(Qr);
    fixture.detectChanges();
  }

  async function preparar() {
    await fixture.whenStable();
    return fixture.componentInstance;
  }

  it('mostra o formulário e o termo quando o site está ativo', async () => {
    criar(new QrMock());
    await preparar();
    expect(fixture.debugElement.query(By.css('form'))).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('TERMO PROVISÓRIO');
  });

  it('desabilita o envio sem o aceite do termo', async () => {
    criar(new QrMock());
    await preparar();
    const botao = fixture.debugElement.query(By.css('button')).nativeElement as HTMLButtonElement;
    expect(botao.disabled).toBe(true);
  });

  it('mostra indisponibilidade quando não há termo', async () => {
    const semTermo = new QrMock();
    semTermo.config = () =>
      of({ ativo: true, preCadastroExpiraHoras: 4, codigoCurtoDigitos: 6, termo: null, formToken: 'tok' });
    criar(semTermo);
    await preparar();
    expect(fixture.nativeElement.textContent).toContain('Cadastro indisponível');
    expect(fixture.debugElement.query(By.css('form'))).toBeNull();
  });

  it('campo nome é obrigatório', async () => {
    criar(new QrMock());
    await preparar();
    const nome = fixture.debugElement.query(By.css('input[name="nome"]'));
    expect(nome.attributes['required']).toBeDefined();
  });

  it('cpf e documento são campos distintos', async () => {
    criar(new QrMock());
    await preparar();
    expect(fixture.debugElement.query(By.css('input[name="cpf"]'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('input[name="docTipo"]'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('input[name="docNumero"]'))).toBeTruthy();
  });

  it('honeypot está presente e invisível', async () => {
    criar(new QrMock());
    await preparar();
    const hp = fixture.debugElement.query(By.css('input.honeypot'));
    expect(hp).toBeTruthy();
    expect(hp.attributes['aria-hidden']).toBe('true');
    expect(hp.attributes['tabindex']).toBe('-1');
  });

  it('após o envio, exibe o código de 6 dígitos', async () => {
    const m = new QrMock();
    m.preCadastro = () => of({ codigoCurto: '482731' });
    criar(m);
    const comp = await preparar();
    comp.model.aceite = true;
    comp.model.nome = 'Fulano';
    comp.enviar();
    await fixture.whenStable();
    expect(comp.estado).toBe('codigo');
    expect(comp.codigo).toBe('482731');
    expect(fixture.debugElement.query(By.css('.codigo')).nativeElement.textContent.trim()).toBe('482731');
  });

  it('sem aceite marcado, enviar() não chama o serviço', async () => {
    const m = new QrMock();
    let chamado = false;
    m.preCadastro = () => {
      chamado = true;
      return of({ codigoCurto: '123456' });
    };
    criar(m);
    await preparar();
    fixture.componentInstance.enviar();
    expect(chamado).toBe(false);
  });

  it('erro 400 mostra a mensagem legível do backend (sem detalhe técnico)', async () => {
    const m = new QrMock();
    m.preCadastro = () =>
      throwError(() => new HttpErrorResponse({ status: 400, error: { mensagem: 'cpf inválido' } }));
    criar(m);
    const comp = await preparar();
    comp.model.aceite = true;
    comp.model.nome = 'Fulano';
    comp.enviar();
    await fixture.whenStable();
    expect(comp.erroForm).toBe('cpf inválido');
    expect(fixture.nativeElement.textContent).toContain('cpf inválido');
  });

  it('erro 429 mostra a mensagem legível do backend', async () => {
    const m = new QrMock();
    m.preCadastro = () =>
      throwError(() => new HttpErrorResponse({ status: 429, error: { mensagem: 'muitas tentativas; tente mais tarde' } }));
    criar(m);
    const comp = await preparar();
    comp.model.aceite = true;
    comp.model.nome = 'Fulano';
    comp.enviar();
    await fixture.whenStable();
    expect(comp.erroForm).toBe('muitas tentativas; tente mais tarde');
  });
});

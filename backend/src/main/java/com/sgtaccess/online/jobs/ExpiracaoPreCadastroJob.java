package com.sgtaccess.online.jobs;

import com.sgtaccess.online.OnlineRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expira pré-cadastros PENDENTE vencidos e apaga os dados pessoais (LGPD, DEC §4/M3). Idempotente.
 * A confirmação/baixa real da visita acontece no SGT; aqui é só o pré-cadastro do balcão.
 */
@Component
public class ExpiracaoPreCadastroJob {

    private final OnlineRepository repo;

    public ExpiracaoPreCadastroJob(OnlineRepository repo) {
        this.repo = repo;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void expirar() {
        repo.expirarVencidos();
    }
}

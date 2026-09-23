package com.nexusfood.plataforma.service;

import com.nexusfood.plataforma.dto.BairroEntregaRequest;
import com.nexusfood.plataforma.dto.ConfiguracaoRestauranteRequest;
import com.nexusfood.plataforma.exception.RecursoNaoEncontradoException;
import com.nexusfood.plataforma.exception.RegraDeNegocioException;
import com.nexusfood.plataforma.model.BairroEntrega;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.BairroEntregaRepository;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;

/** Configuração do restaurante logado: dados, modalidades, taxa de entrega e bairros atendidos. */
@Service
@RequiredArgsConstructor
public class RestauranteService {

    private final RestauranteRepository restauranteRepository;
    private final BairroEntregaRepository bairroEntregaRepository;

    public Restaurante atual() {
        return restauranteRepository.findById(SecurityUtils.restauranteAtualId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante não encontrado"));
    }

    @Transactional
    public Restaurante atualizarConfiguracao(ConfiguracaoRestauranteRequest req) {
        if (!req.isAceitaRetirada() && !req.isAceitaEntrega() && !req.isAceitaConsumoLocal()) {
            throw new RegraDeNegocioException("Escolha pelo menos uma forma de atendimento: retirada, entrega ou consumo no local.");
        }
        try {
            ZoneId.of(req.getFusoHorario());
        } catch (DateTimeException e) {
            throw new RegraDeNegocioException("Fuso horário inválido.");
        }

        Restaurante r = atual();
        r.setNome(req.getNome().trim());
        r.setTelefone(req.getTelefone());
        r.setEndereco(req.getEndereco());
        r.setLogoUrl(req.getLogoUrl());
        r.setFusoHorario(req.getFusoHorario());
        r.setHoraViradaDia(req.getHoraViradaDia());
        r.setAceitaRetirada(req.isAceitaRetirada());
        r.setAceitaEntrega(req.isAceitaEntrega());
        r.setAceitaConsumoLocal(req.isAceitaConsumoLocal());
        r.setTipoTaxaEntrega(req.getTipoTaxaEntrega());
        r.setTaxaEntregaFixa(req.getTaxaEntregaFixa());
        r.setPedidoMinimo(req.getPedidoMinimo());
        r.setTempoPreparoEstimadoMin(req.getTempoPreparoEstimadoMin());
        return restauranteRepository.save(r);
    }

    @Transactional
    public Restaurante definirAceitandoPedidos(boolean aceitando) {
        Restaurante r = atual();
        r.setAceitandoPedidos(aceitando);
        return restauranteRepository.save(r);
    }

    public List<BairroEntrega> listarBairros() {
        return bairroEntregaRepository.findAllByRestauranteIdOrderByNomeAsc(SecurityUtils.restauranteAtualId());
    }

    @Transactional
    public BairroEntrega criarBairro(BairroEntregaRequest req) {
        BairroEntrega bairro = BairroEntrega.builder()
                .restaurante(atual())
                .nome(req.getNome().trim())
                .taxa(req.getTaxa())
                .ativo(req.isAtivo())
                .build();
        return bairroEntregaRepository.save(bairro);
    }

    @Transactional
    public BairroEntrega atualizarBairro(Long id, BairroEntregaRequest req) {
        BairroEntrega bairro = buscarBairro(id);
        bairro.setNome(req.getNome().trim());
        bairro.setTaxa(req.getTaxa());
        bairro.setAtivo(req.isAtivo());
        return bairroEntregaRepository.save(bairro);
    }

    /** Pode apagar de verdade: o pedido guarda uma cópia do nome e da taxa do bairro. */
    @Transactional
    public void excluirBairro(Long id) {
        bairroEntregaRepository.delete(buscarBairro(id));
    }

    private BairroEntrega buscarBairro(Long id) {
        return bairroEntregaRepository.findByIdAndRestauranteId(id, SecurityUtils.restauranteAtualId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bairro não encontrado"));
    }
}

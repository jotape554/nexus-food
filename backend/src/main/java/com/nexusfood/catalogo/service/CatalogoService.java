package com.nexusfood.catalogo.service;

import com.nexusfood.catalogo.dto.CardapioResponse;
import com.nexusfood.catalogo.dto.CategoriaRequest;
import com.nexusfood.catalogo.dto.ProdutoRequest;
import com.nexusfood.catalogo.dto.ProdutoResponse;
import com.nexusfood.catalogo.model.Categoria;
import com.nexusfood.catalogo.dto.OpcoesProdutoRequest;
import com.nexusfood.catalogo.enums.CobrancaGrupo;
import com.nexusfood.catalogo.model.GrupoOpcoes;
import com.nexusfood.catalogo.model.Opcao;
import com.nexusfood.catalogo.model.Produto;
import com.nexusfood.catalogo.repository.CategoriaRepository;
import com.nexusfood.catalogo.repository.ProdutoRepository;
import com.nexusfood.plataforma.exception.RecursoNaoEncontradoException;
import com.nexusfood.plataforma.exception.RegraDeNegocioException;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Categorias e produtos do restaurante logado, e o cardápio público montado a partir deles. */
@Service
@RequiredArgsConstructor
public class CatalogoService {

    private final CategoriaRepository categoriaRepository;
    private final ProdutoRepository produtoRepository;
    private final RestauranteRepository restauranteRepository;
    private final Clock clock;

    public List<Categoria> listarCategorias() {
        return categoriaRepository.findAllByRestauranteIdOrderByOrdemAscNomeAsc(SecurityUtils.restauranteAtualId());
    }

    @Transactional
    public Categoria criarCategoria(CategoriaRequest req) {
        Categoria categoria = Categoria.builder()
                .restaurante(restauranteAtual())
                .nome(req.getNome().trim())
                .ordem(req.getOrdem() != null ? req.getOrdem() : 0)
                .ativa(req.isAtiva())
                .build();
        return categoriaRepository.save(categoria);
    }

    @Transactional
    public Categoria atualizarCategoria(Long id, CategoriaRequest req) {
        Categoria categoria = buscarCategoria(id);
        categoria.setNome(req.getNome().trim());
        if (req.getOrdem() != null) categoria.setOrdem(req.getOrdem());
        categoria.setAtiva(req.isAtiva());
        return categoriaRepository.save(categoria);
    }

    /**
     * Remover = desativar: produtos já removidos ainda apontam para a categoria (histórico de
     * pedidos). Com produtos ativos dentro, pede para mover ou remover os produtos antes.
     */
    @Transactional
    public void excluirCategoria(Long id) {
        Categoria categoria = buscarCategoria(id);
        if (produtoRepository.existsByCategoriaIdAndAtivoTrue(categoria.getId())) {
            throw new RegraDeNegocioException("Esta categoria ainda tem produtos. Mova ou remova os produtos antes, ou apenas desative a categoria.");
        }
        categoria.setAtiva(false);
        categoriaRepository.save(categoria);
    }

    @Transactional(readOnly = true)
    public List<ProdutoResponse> listarProdutos() {
        return produtoRepository.findAllByRestauranteIdAndAtivoTrueOrderByOrdemAscNomeAsc(SecurityUtils.restauranteAtualId())
                .stream().map(ProdutoResponse::de).toList();
    }

    @Transactional
    public ProdutoResponse criarProduto(ProdutoRequest req) {
        Produto produto = Produto.builder()
                .restaurante(restauranteAtual())
                .categoria(buscarCategoria(req.getCategoriaId()))
                .nome(req.getNome().trim())
                .descricao(req.getDescricao())
                .preco(req.getPreco())
                .imagemUrl(req.getImagemUrl())
                .disponivel(req.isDisponivel())
                .ordem(req.getOrdem() != null ? req.getOrdem() : 0)
                .criadoEm(Instant.now(clock))
                .build();
        return ProdutoResponse.de(produtoRepository.save(produto));
    }

    @Transactional
    public ProdutoResponse atualizarProduto(Long id, ProdutoRequest req) {
        Produto produto = buscarProduto(id);
        produto.setCategoria(buscarCategoria(req.getCategoriaId()));
        produto.setNome(req.getNome().trim());
        produto.setDescricao(req.getDescricao());
        produto.setPreco(req.getPreco());
        produto.setImagemUrl(req.getImagemUrl());
        produto.setDisponivel(req.isDisponivel());
        if (req.getOrdem() != null) produto.setOrdem(req.getOrdem());
        return ProdutoResponse.de(produtoRepository.save(produto));
    }

    /** Liga/desliga "esgotado" sem abrir o formulário — é o que se faz no meio do serviço. */
    @Transactional
    public ProdutoResponse definirDisponivel(Long id, boolean disponivel) {
        Produto produto = buscarProduto(id);
        produto.setDisponivel(disponivel);
        return ProdutoResponse.de(produtoRepository.save(produto));
    }

    /**
     * Salva os grupos de opções do produto (substitui a lista inteira, mantendo os ids que vierem
     * para não quebrar sacolas abertas de clientes). Regras além das anotações do request:
     * mínimo ≤ máximo, mínimo ≤ número de opções, ids precisam ser deste produto.
     */
    @Transactional
    public ProdutoResponse salvarOpcoes(Long produtoId, OpcoesProdutoRequest req) {
        Produto produto = buscarProduto(produtoId);
        Map<Long, GrupoOpcoes> gruposAtuais = produto.getGrupos().stream()
                .collect(Collectors.toMap(GrupoOpcoes::getId, g -> g));

        List<GrupoOpcoes> novos = new ArrayList<>();
        for (int i = 0; i < req.grupos().size(); i++) {
            OpcoesProdutoRequest.Grupo gReq = req.grupos().get(i);
            String nomeGrupo = gReq.nome().trim();
            if (gReq.minimo() > gReq.maximo()) {
                throw new RegraDeNegocioException("Em \"" + nomeGrupo + "\", o mínimo de escolhas não pode ser maior que o máximo.");
            }
            if (gReq.minimo() > gReq.opcoes().size()) {
                throw new RegraDeNegocioException("Em \"" + nomeGrupo + "\", o mínimo de escolhas é maior que o número de opções.");
            }

            GrupoOpcoes grupo;
            if (gReq.id() != null) {
                grupo = gruposAtuais.remove(gReq.id());
                if (grupo == null) throw new RecursoNaoEncontradoException("Grupo de opções não encontrado");
            } else {
                grupo = GrupoOpcoes.builder().produto(produto).build();
            }
            grupo.setNome(nomeGrupo);
            grupo.setMinimo(gReq.minimo());
            grupo.setMaximo(Math.min(gReq.maximo(), gReq.opcoes().size()));
            grupo.setCobranca(gReq.cobranca() != null ? gReq.cobranca() : CobrancaGrupo.SOMA);
            grupo.setOrdem(i);
            atualizarOpcoes(grupo, gReq.opcoes());
            novos.add(grupo);
        }

        produto.getGrupos().clear();
        produto.getGrupos().addAll(novos);
        produtoRepository.saveAndFlush(produto);
        return ProdutoResponse.de(produto);
    }

    private void atualizarOpcoes(GrupoOpcoes grupo, List<OpcoesProdutoRequest.Opcao> opcoesReq) {
        Map<Long, Opcao> atuais = grupo.getOpcoes().stream().collect(Collectors.toMap(Opcao::getId, o -> o));
        List<Opcao> novas = new ArrayList<>();
        for (int i = 0; i < opcoesReq.size(); i++) {
            OpcoesProdutoRequest.Opcao oReq = opcoesReq.get(i);
            Opcao opcao;
            if (oReq.id() != null) {
                opcao = atuais.remove(oReq.id());
                if (opcao == null) throw new RecursoNaoEncontradoException("Opção não encontrada");
            } else {
                opcao = Opcao.builder().grupo(grupo).build();
            }
            opcao.setNome(oReq.nome().trim());
            opcao.setPreco(oReq.preco().setScale(2, RoundingMode.HALF_UP));
            opcao.setDisponivel(oReq.disponivel() == null || oReq.disponivel());
            opcao.setOrdem(i);
            novas.add(opcao);
        }
        grupo.getOpcoes().clear();
        grupo.getOpcoes().addAll(novas);
    }

    /** Esgotar/liberar uma opção (ex.: acabou o catupiry) sem abrir o produto. Liberado para a equipe toda. */
    @Transactional
    public ProdutoResponse definirOpcaoDisponivel(Long produtoId, Long opcaoId, boolean disponivel) {
        Produto produto = buscarProduto(produtoId);
        Opcao opcao = produto.getGrupos().stream().flatMap(g -> g.getOpcoes().stream())
                .filter(o -> o.getId().equals(opcaoId)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Opção não encontrada"));
        opcao.setDisponivel(disponivel);
        produtoRepository.saveAndFlush(produto);
        return ProdutoResponse.de(produto);
    }

    @Transactional
    public void removerProduto(Long id) {
        Produto produto = buscarProduto(id);
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }

    @Transactional(readOnly = true)
    public CardapioResponse cardapioPublico(Long restauranteId) {
        Map<Long, List<ProdutoResponse>> produtosPorCategoria = produtoRepository
                .findAllByRestauranteIdAndAtivoTrueOrderByOrdemAscNomeAsc(restauranteId).stream()
                .map(ProdutoResponse::de)
                .collect(Collectors.groupingBy(ProdutoResponse::categoriaId));

        List<CardapioResponse.CategoriaCardapio> categorias = categoriaRepository
                .findAllByRestauranteIdAndAtivaTrueOrderByOrdemAscNomeAsc(restauranteId).stream()
                .filter(c -> produtosPorCategoria.containsKey(c.getId()))
                .map(c -> new CardapioResponse.CategoriaCardapio(c.getId(), c.getNome(), produtosPorCategoria.get(c.getId())))
                .toList();

        return new CardapioResponse(categorias);
    }

    private Restaurante restauranteAtual() {
        return restauranteRepository.findById(SecurityUtils.restauranteAtualId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante não encontrado"));
    }

    private Categoria buscarCategoria(Long id) {
        return categoriaRepository.findByIdAndRestauranteId(id, SecurityUtils.restauranteAtualId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));
    }

    private Produto buscarProduto(Long id) {
        return produtoRepository.findByIdAndRestauranteId(id, SecurityUtils.restauranteAtualId())
                .filter(Produto::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado"));
    }
}

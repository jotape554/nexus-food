# Nexus Food — guia para quem mexe no código

SaaS de pedidos para restaurantes (multi-tenant) com Nexus Analytics. Produto da **Nexus Sistemas**:
a assinatura aparece via `components/AssinaturaNexus` e os dados da marca ficam em `frontend/src/marca.js`. Produto separado do
BarberFlow, criado a partir da mesma base de plataforma. O plano de arquitetura aprovado está em
`docs/PLANO_ARQUITETURA.md` — leia antes de mudar qualquer regra de negócio.

## Estrutura

```
backend/   Spring Boot 3 / Java 21 / PostgreSQL / Flyway / JWT      (./gradlew test)
frontend/  React 18 + Vite + React Router + TanStack Query           (npm run build)
```

Backend em pacotes por módulo (`com.nexusfood.<modulo>.{model,repository,service,controller,dto}`).
Dependências só de cima para baixo:

```
plataforma → catalogo → clientes → pedidos → relatorios → analytics
plataforma → equipe
```

`relatorios` e `analytics` só LEEM pedidos; nunca escrevem. `analytics` reutiliza o `MetricasCalculator`
de `relatorios`.

## Regras que não podem ser quebradas

1. **Tenant só pelo token.** `SecurityUtils.restauranteAtualId()`; nunca aceite `restauranteId`
   vindo do corpo/URL de uma rota `/api/**`. Toda busca por id usa `findByIdAndRestauranteId`.
2. **Preço sempre no servidor.** O pedido público não traz preço; `PedidoService` recalcula a
   partir do `Produto` e das opções escolhidas (ids). `ItemPedido` guarda cópia de nome e preço
   (já com as opções) e `ItemPedidoOpcao` guarda cópia de grupo, opção e preço; o pedido guarda
   cópia da taxa e do bairro.
   Opções: `GrupoOpcoes` (mínimo/máximo de escolhas, cobrança `SOMA`/`MAIOR`/`MEDIA`) → `Opcao`.
   A mesma conta existe no navegador (`frontend/src/api/opcoes.js`) só para mostrar preço.
3. **Status só por `Pedido.transicionarPara(...)`.** Ele valida a transição, grava o horário da
   etapa (`*Em`) e o `PedidoEvento`. Nunca altere `status` ou os `*Em` diretamente.
4. **"Agora" e "hoje" só por `RelogioRestaurante` / `Clock` injetado.** Nada de
   `LocalDate.now()`/`Instant.now()` soltos. Dia de relatório = `diaOperacional` (fuso + hora de
   virada do restaurante).
5. **Telefone do cliente sempre normalizado** por `TelefoneUtil` (só dígitos, com DDI 55).
6. **Venda = pedido `CONCLUIDO`.** Não existe tabela de receita separada.
7. **Schema só por migração Flyway** (`backend/src/main/resources/db/migration/V{n}__descricao.sql`).
   Nunca edite uma migração já publicada; crie a próxima. SQL portável entre PostgreSQL e H2
   (modo PostgreSQL, usado nos testes) — nada de `ON CONFLICT`, JSONB etc.
8. **Nenhum endpoint público devolve dado do cliente** (telefone, endereço, histórico).
   Acompanhamento só pelo `codigoPublico` (UUID).
9. **Acesso por plano decidido no backend.** Toda rota de `/api/**` declara `@RequerRecurso(Recurso.X)`
   ou `@AcessoLivre("motivo")` (o `RecursoAnotacaoTest` quebra o build se faltar). Quem responde "o que
   este restaurante pode usar" é só o `AcessoPlanoService` (teste grátis = Premium). Limites de
   quantidade (usuários, histórico de relatório) ficam no `PlanoSaas` e são checados no service,
   com `PlanoInsuficienteException` (402 + `upgradeNecessario` + `planoNecessario`). Downgrade nunca
   apaga dado, só muda o que se lê. O frontend só esconde/mostra (`usePlano`, `RecursoBloqueado`).
12. **Equipe:** senha só quem cria é a própria pessoa (link de convite de 72 h); o restaurante sempre
   tem um administrador ativo; ninguém desativa ou muda o próprio perfil; desativar corta o acesso
   na hora (o `JwtAuthFilter` recusa token de usuário inativo). Toda chamada à Stripe passa pelo
   `StripeGateway`; trocar de plano de quem já assina muda o preço da assinatura existente.
10. **Métricas num lugar só:** `relatorios/service/MetricasCalculator` (venda = CONCLUIDO, dia =
    `diaOperacional`, ticket = faturamento ÷ concluídos, cancelamento = cancelados ÷ recebidos).
    Relatórios e os resumos diários do Nexus usam essa mesma classe.
11. **Nexus Score:** a regra é o arquivo `backend/src/main/resources/analytics/regras/nexus-score-vN.json`.
    Versão publicada é imutável (`RegraScoreImutavelTest` confere o hash): mudou peso, âncora, mínimo
    ou fórmula → crie a próxima versão. O motor (`NexusScoreEngine`) é puro; as medidas saem do
    `ColetorIndicadores`; insights são modelos de texto em `InsightService.CATALOGO` (mudou texto ou
    condição → suba a versão do modelo). Dia de referência = último dia operacional fechado.
    Regras do Nexus Score publicadas são imutáveis: mudou peso/âncora → nova versão.

## Convenções

- Código, mensagens e comentários em português. Mensagens de erro para o usuário final são
  frases completas e educadas (`RegraDeNegocioException` → 400).
- Respostas da API em DTOs (`record`), não entidades, quando há dado sensível ou lazy.
- Testes: unitários puros para regras (ex.: `PedidoTransicaoTest`), integração com MockMvc + H2
  para fluxos. Use `RelogioDeTeste` (via `RelogioDeTesteConfig`) para controlar o tempo.
- Consultas JPQL novas: testar também no PostgreSQL (o H2 aceita coisas que o Postgres recusa,
  ex.: `:param IS NULL OR LOWER(:param)`).
- Demonstração (`frontend/src/demo/`): espelha as regras do backend em JavaScript. Mudou regra de
  pedido, relatório, plano (`demo/planos.js`) ou equipe no backend → mude lá também e rode
  `npm run build:demo`.
- Impressão (`frontend/src/impressao/`): comanda montada como HTML próprio e impressa por uma
  moldura escondida; configuração por aparelho no `localStorage`. Todo texto vindo do cliente
  passa por `esc` antes de entrar na comanda.
- Antes de subir: `cd backend && ./gradlew test` e `cd frontend && npm run build`.

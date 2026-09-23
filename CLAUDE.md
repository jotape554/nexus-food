# Nexus Food — guia para quem mexe no código

SaaS de pedidos para restaurantes (multi-tenant) com Nexus Analytics. Produto separado do
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
```

`relatorios` e `analytics` só LEEM pedidos; nunca escrevem.

## Regras que não podem ser quebradas

1. **Tenant só pelo token.** `SecurityUtils.restauranteAtualId()`; nunca aceite `restauranteId`
   vindo do corpo/URL de uma rota `/api/**`. Toda busca por id usa `findByIdAndRestauranteId`.
2. **Preço sempre no servidor.** O pedido público não traz preço; `PedidoService` recalcula a
   partir do `Produto`. `ItemPedido` guarda cópia de nome e preço; o pedido guarda cópia da taxa
   e do bairro.
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
9. **Acesso por plano decidido no backend** (`Recurso` + `RecursoGateFilter`, resposta 402 com
   `upgradeNecessario`). O frontend só esconde/mostra.
10. **Métricas (Fases 3/4) num lugar só** (`MetricasCalculator`), usado por relatórios e snapshots.
    Regras do Nexus Score publicadas são imutáveis: mudou peso/âncora → nova versão.

## Convenções

- Código, mensagens e comentários em português. Mensagens de erro para o usuário final são
  frases completas e educadas (`RegraDeNegocioException` → 400).
- Respostas da API em DTOs (`record`), não entidades, quando há dado sensível ou lazy.
- Testes: unitários puros para regras (ex.: `PedidoTransicaoTest`), integração com MockMvc + H2
  para fluxos. Use `RelogioDeTeste` (via `RelogioDeTesteConfig`) para controlar o tempo.
- Antes de subir: `cd backend && ./gradlew test` e `cd frontend && npm run build`.

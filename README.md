# Nexus Food

**Um produto [Nexus Sistemas](https://www.instagram.com/nexussistemas.co/).**

Sistema de pedidos para restaurantes (SaaS multi-tenant): cardápio digital com link próprio,
pedidos com acompanhamento em tempo real, painel de pedidos para a equipe e, nas próximas
fases, relatórios de vendas e o **Nexus Analytics** (Nexus Score e insights).

## Estrutura

```
backend/    Spring Boot 3 (Java 21) + PostgreSQL + Flyway + JWT
frontend/   React 18 + Vite (painel do restaurante e cardápio público)
docs/       Plano de arquitetura aprovado
```

## Backend

```
cd backend
./gradlew bootRun
```

Variáveis de ambiente (todas têm padrão de desenvolvimento em `application.properties`):

```
DB_URL=jdbc:postgresql://localhost:5432/nexusfood
DB_USERNAME=postgres
DB_PASSWORD=postgres
JWT_SECRET=uma-chave-longa-e-secreta
```

O schema é criado pelas migrações do Flyway na primeira subida. Testes: `./gradlew test`
(H2 em memória, sem precisar de Postgres).

## Frontend

```
cd frontend
npm install
npm run dev
```

O Vite faz proxy de `/api`, `/auth`, `/public` e `/webhooks` para `http://localhost:8080`.

## Demonstração (sem backend)

```
cd frontend
npm run build:demo
```

Gera `frontend/dist-demo/nexus-food-demo.html`, um arquivo único que roda só no navegador.
É o mesmo frontend, com uma API falsa (`src/demo/`) no lugar do servidor, que segue as mesmas
regras do backend e usa os dados de exemplo da Cantina da Nona. Mostra o painel do
restaurante e o celular do cliente, separados ou lado a lado: um pedido feito no celular
chega no painel na hora. A build normal (`npm run build`) não inclui nada disso.

Se mudar uma regra de pedido no backend, mude também em `src/demo/regras.js` e
`src/demo/servico.js`.

## Rotas principais

| Quem | Rota | O quê |
|---|---|---|
| Cliente final | `/r/{slug}` | Cardápio e carrinho |
| Cliente final | `/pedido/{codigo}` | Acompanhamento do pedido |
| Restaurante | `/painel/pedidos` | Painel de pedidos (atualiza sozinho) |
| Restaurante | `/painel/cardapio` | Categorias e produtos |
| Restaurante | `/painel/relatorios` | Vendas por dia, semana e mês, com comparação e planilha |
| Restaurante | `/painel/nexus` | Nexus Score: nota, áreas, indicadores, evolução e insights |
| Restaurante | `/painel/configuracoes` | Modalidades, taxa de entrega, bairros, horários |
| Restaurante | `/painel/equipe` | Usuários, perfis e convites (só administrador) |
| Restaurante | `/painel/assinatura` | Planos, uso do plano e troca de plano |
| Convidado | `/redefinir-senha?token=` | Cria a própria senha (convite) ou troca a senha |

## Fases

1. ✅ Plano de arquitetura
2. ✅ Domínio do restaurante: cardápio, pedidos, clientes, painel
3. ✅ Relatórios de vendas por dia, semana e mês
4. ✅ Nexus Analytics: snapshots, indicadores, Nexus Score, insights
5. ✅ Controle de acesso por plano e equipe

### Planos

| | Básico | Profissional | Premium |
|---|---|---|---|
| Cardápio, pedidos, clientes | ✓ | ✓ | ✓ |
| Relatórios | últimos 30 dias | últimos 12 meses | histórico completo |
| Usuários ativos | 2 | 5 | sem limite |
| Nexus Score (nota, áreas, evolução) | — | ✓ | ✓ |
| Indicadores detalhados e dicas | — | — | ✓ |

O teste grátis (14 dias) libera tudo. A regra fica em `PlanoSaas` + `Recurso` (backend).

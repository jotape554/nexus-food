# Nexus Food

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

## Rotas principais

| Quem | Rota | O quê |
|---|---|---|
| Cliente final | `/r/{slug}` | Cardápio e carrinho |
| Cliente final | `/pedido/{codigo}` | Acompanhamento do pedido |
| Restaurante | `/painel/pedidos` | Painel de pedidos (atualiza sozinho) |
| Restaurante | `/painel/cardapio` | Categorias e produtos |
| Restaurante | `/painel/configuracoes` | Modalidades, taxa de entrega, bairros, horários |

## Fases

1. ✅ Plano de arquitetura
2. ✅ Domínio do restaurante: cardápio, pedidos, clientes, painel
3. Relatórios de vendas por dia, semana e mês
4. Nexus Analytics: snapshots, indicadores, Nexus Score, insights
5. Controle de acesso por plano

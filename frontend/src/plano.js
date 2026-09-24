import { useQuery } from '@tanstack/react-query';
import { api } from './api/http';

export const NOME_PLANO = { BASICO: 'Básico', PROFISSIONAL: 'Profissional', PREMIUM: 'Premium' };

/**
 * O que o restaurante pode usar agora, vindo de /api/assinatura. A tela só esconde ou mostra;
 * quem decide é sempre o backend (402 com upgradeNecessario).
 */
export function usePlano() {
  const { data, isLoading, refetch } = useQuery({
    queryKey: ['assinatura'],
    queryFn: () => api.get('/api/assinatura'),
    staleTime: 60 * 1000,
  });
  return {
    status: data,
    carregando: isLoading,
    recarregar: refetch,
    // Enquanto carrega, trata como liberado: evita piscar cadeado em quem tem o recurso.
    liberado: (recurso) => (data ? !!data.recursos?.[recurso] : true),
  };
}

/** Plano mínimo de cada recurso (espelha o enum Recurso do backend, só para rótulos). */
export const PLANO_DO_RECURSO = {
  NEXUS_SCORE: 'PROFISSIONAL',
  NEXUS_DETALHES: 'PREMIUM',
};

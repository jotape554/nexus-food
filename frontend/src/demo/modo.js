/**
 * Liga o modo demonstração (build com `--mode demo`): o frontend passa a conversar com uma API
 * falsa que roda no próprio navegador. Na build normal isto é `false` em tempo de build e todo
 * o código da demonstração fica de fora do pacote.
 */
export const DEMO = import.meta.env.VITE_DEMO === 'true';

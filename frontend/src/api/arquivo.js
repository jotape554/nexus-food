/**
 * Oferece um arquivo gerado na tela para o usuário salvar.
 *
 * No sistema normal é o download do navegador. Quando a página roda dentro do visualizador do
 * Claude (a demonstração publicada), downloads diretos são bloqueados; ali o arquivo passa pelo
 * recurso "downloads" do visualizador, que pede confirmação ao usuário.
 *
 * Resolve true se foi entregue ao navegador, false se o usuário recusou. Rejeita com uma
 * mensagem legível se não for possível salvar.
 */
export async function salvarArquivo(nome, conteudo) {
  const visualizador = typeof window !== 'undefined' ? window.claude : undefined;
  if (visualizador?.use) {
    const downloads = await visualizador.use('downloads');
    if (!downloads) throw new Error('Este visualizador não permite baixar arquivos.');
    try {
      await downloads.save({ filename: nome, data: conteudo });
      return true;
    } catch (e) {
      if (e?.code === 'declined') return false;
      if (e?.code === 'rate_limited') throw new Error('Já existe um download esperando confirmação.');
      throw new Error('Não foi possível baixar o arquivo aqui.');
    }
  }

  const url = URL.createObjectURL(conteudo instanceof Blob ? conteudo : new Blob([conteudo]));
  const link = document.createElement('a');
  link.href = url;
  link.download = nome;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  return true;
}

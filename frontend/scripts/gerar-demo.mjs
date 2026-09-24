// Junta a build de demonstração (dist-demo) num único HTML autossuficiente — CSS e JS embutidos —
// pronto para publicar como página estática (ex.: Artifact do Claude) ou mandar por arquivo.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist-demo');
const html = readFileSync(join(raiz, 'index.html'), 'utf8');

const js = [...html.matchAll(/<script[^>]*src="\/?([^"]+\.js)"[^>]*><\/script>/g)].map((m) => m[1]);
const css = [...html.matchAll(/<link[^>]*rel="stylesheet"[^>]*href="\/?(assets\/[^"]+\.css)"[^>]*>/g)].map((m) => m[1]);
if (js.length !== 1) throw new Error(`Esperava 1 script na build de demonstração, achei ${js.length}.`);

const ler = (arquivo) => readFileSync(join(raiz, arquivo), 'utf8');
// Um "</script" dentro do código fecharia a tag antes da hora.
const codigo = ler(js[0]).replace(/<\/script/gi, '<\\/script');
const estilos = css.map(ler).join('\n');

const pagina = `<title>Nexus Food</title>
<meta name="description" content="Demonstração do Nexus Food, um produto Nexus Sistemas: painel de pedidos, relatórios e cardápio do cliente.">
<meta name="author" content="Nexus Sistemas">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400;9..144,500;9..144,600&family=Inter:wght@400;500;600;700&family=Space+Grotesk:wght@600;700&display=swap">
<style>
html { color-scheme: light; }
body { font-size: 16px; line-height: normal; }
${estilos}
</style>
<div id="root"></div>
<script type="module">
${codigo}
</script>
`;

const saida = join(raiz, 'nexus-food-demo.html');
writeFileSync(saida, pagina);
console.log(`Demonstração gerada: ${saida} (${(pagina.length / 1024).toFixed(0)} KB)`);

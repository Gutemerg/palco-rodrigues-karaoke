// Busca de vídeos karaokê no YouTube pelo servidor: a chave fica só na Vercel (variável YT_KEY).
const ALLOWED = /(^|\.)palco-rodrigues-karaoke(-[\w-]+)?\.vercel\.app$/;

module.exports = async (req, res) => {
  const key = process.env.YT_KEY;
  if (!key) return res.status(500).json({ error: { message: 'NOKEY_SERVER' } });

  // só atende o próprio app (site ou APK)
  const ref = req.headers.origin || req.headers.referer || '';
  let host = '';
  try { host = new URL(ref).hostname; } catch (e) {}
  const ua = req.headers['user-agent'] || '';
  if (!ALLOWED.test(host) && !ua.includes('PalcoKaraokeApp')) {
    return res.status(403).json({ error: { message: 'Acesso não permitido' } });
  }

  const q = String(req.query.q || '').trim().slice(0, 200);
  const max = Math.max(1, Math.min(10, parseInt(req.query.max || '8', 10) || 8));
  if (!q) return res.status(400).json({ error: { message: 'Busca vazia' } });

  try {
    const url = 'https://www.googleapis.com/youtube/v3/search?' + new URLSearchParams({
      part: 'snippet', type: 'video', videoEmbeddable: 'true', maxResults: String(max), q, key
    });
    const r = await fetch(url);
    const d = await r.json();
    if (d.error) {
      const reason = d.error.errors && d.error.errors[0] && d.error.errors[0].reason;
      return res.status(502).json({ error: { message: reason === 'quotaExceeded' ? 'Limite diário de buscas do YouTube atingido' : 'Erro no YouTube', reason } });
    }
    const items = (d.items || []).map(i => ({
      id: i.id.videoId,
      title: i.snippet.title,
      ch: i.snippet.channelTitle,
      thumb: i.snippet.thumbnails && i.snippet.thumbnails.default && i.snippet.thumbnails.default.url
    }));
    // guarda o resultado no cache da Vercel por 30 dias pra economizar a cota
    res.setHeader('Cache-Control', 'public, s-maxage=2592000, stale-while-revalidate=86400');
    return res.status(200).json({ items });
  } catch (e) {
    return res.status(502).json({ error: { message: 'Falha ao falar com o YouTube' } });
  }
};

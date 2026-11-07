// Letra sincronizada (formato LRC) pelo LRCLIB, com cache na Vercel.
const ALLOWED = /(^|\.)palco-rodrigues-karaoke(-[\w-]+)?\.vercel\.app$/;
const UA = 'PalcoKaraoke/1.0 (app pessoal)';

module.exports = async (req, res) => {
  const ref = req.headers.origin || req.headers.referer || '';
  let host = '';
  try { host = new URL(ref).hostname; } catch (e) {}
  const ua = req.headers['user-agent'] || '';
  if (!ALLOWED.test(host) && !ua.includes('PalcoKaraokeApp')) {
    return res.status(403).json({ error: 'Acesso não permitido' });
  }
  const artist = String(req.query.artist || '').slice(0, 150);
  const title = String(req.query.title || '').slice(0, 150);
  const duration = parseInt(req.query.duration || '0', 10) || 0;
  if (!artist || !title) return res.status(400).json({ error: 'Faltou artista ou música' });

  const send = (synced) => {
    res.setHeader('Cache-Control', 'public, s-maxage=2592000, stale-while-revalidate=86400');
    return res.status(200).json({ synced: synced || null });
  };
  try {
    if (duration) {
      const r = await fetch('https://lrclib.net/api/get?' + new URLSearchParams({ artist_name: artist, track_name: title, duration: String(duration) }), { headers: { 'User-Agent': UA } });
      if (r.ok) { const d = await r.json(); if (d.syncedLyrics) return send(d.syncedLyrics); }
    }
    const r2 = await fetch('https://lrclib.net/api/search?' + new URLSearchParams({ artist_name: artist, track_name: title }), { headers: { 'User-Agent': UA } });
    const list = r2.ok ? await r2.json() : [];
    const withSync = (list || []).filter(x => x.syncedLyrics);
    if (duration) withSync.sort((a, b) => Math.abs((a.duration || 0) - duration) - Math.abs((b.duration || 0) - duration));
    return send(withSync[0] && withSync[0].syncedLyrics);
  } catch (e) {
    return res.status(502).json({ error: 'Falha ao buscar a letra' });
  }
};

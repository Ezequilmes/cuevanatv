const prompt = 'Actúa como experto en Marketing Viral para CuevanaTV. Escribe un CAPTION y un HOOK potente para el contenido: Transformers One (Acción, movie). Responde SOLO en formato JSON puro: {"hook": "...", "caption": "...", "hashtags": "#CuevanaTV #Viral ..."}. SIN MARKDOWN, SIN INTRODUCCIONES.';
const url = 'https://text.pollinations.ai/' + encodeURIComponent(prompt);
console.log('Fetching:', url);
fetch(url).then(r => r.text().then(t => console.log('Status:', r.status, '\nContent:', t))).catch(e => console.error(e));

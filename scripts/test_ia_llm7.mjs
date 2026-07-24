const prompt = 'Actúa como experto en Marketing Viral para CuevanaTV. Escribe un CAPTION y un HOOK potente para el contenido: Transformers One (Acción, movie). Responde SOLO en formato JSON puro: {"hook": "...", "caption": "...", "hashtags": "#CuevanaTV #Viral ..."}. SIN MARKDOWN, SIN INTRODUCCIONES.';
const payload = {
    model: 'fast',
    messages: [
        { role: 'system', content: 'Eres un experto en marketing.' },
        { role: 'user', content: prompt }
    ]
};
fetch('https://api.llm7.io/v1/chat/completions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer unused' },
    body: JSON.stringify(payload)
}).then(r => r.json().then(d => console.log(JSON.stringify(d, null, 2)))).catch(e => console.error(e));

import "dotenv/config";
import { createClient } from "@supabase/supabase-js";

const { SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY } = process.env;
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

async function fix() {
  console.log("🚀 INICIANDO REPARACIÓN FINAL DE CATEGORÍAS...");

  // 1. Todo lo que NO sea de categorías de sistema/series/live, es MOVIE.
  // Esto arreglará "Y entonces llegó ella" (Comedia) y similares.
  console.log("🛠️ Normalizando películas por género...");
  const { error: err1 } = await supabase
    .from('titles')
    .update({ type: 'movie' })
    .not('category', 'in', '("Series","Canales 24/7","Eventos Deportivos","Canales Argentinos","SISTEMA","TV Argentina","En Vivo","Canales TVLibr3")');

  if (err1) console.error("Error setting movies:", err1.message);

  // 2. Asegurar que las series reales sean SERIES.
  console.log("🛠️ Asegurando tipo 'series' para categoría Series...");
  await supabase.from('titles').update({ type: 'series' }).eq('category', 'Series');

  // 3. Asegurar que los canales en vivo sean LIVE.
  console.log("🛠️ Asegurando tipo 'live' para categorías de TV...");
  const liveCats = ["Canales 24/7", "Eventos Deportivos", "Canales Argentinos", "TV Argentina", "En Vivo", "Canales TVLibr3"];
  await supabase.from('titles').update({ type: 'live' }).in('category', liveCats);

  // 4. Eliminar títulos que son episodios (Limpiar "Novedades")
  console.log("🧹 Eliminando episodios infiltrados en 'titles'...");
  const { error: delErr } = await supabase
    .from('titles')
    .delete()
    .or('title.ilike.%S0%,title.ilike.%S1%,title.ilike.%S2%,title.ilike.%1x%,title.ilike.%2x%')
    .neq('type', 'series'); // Solo borrar si NO están marcados como el título de la serie principal

  if (delErr) console.error("Error deleting episode titles:", delErr.message);

  // 5. Verificar "Y entonces llegó ella"
  const { data: verify } = await supabase.from('titles').select('id, title, type, category').ilike('title', '%Y entonces llegó ella%');
  console.log("✅ Verificación 'Y entonces llegó ella':", verify);

  console.log("🏁 REPARACIÓN COMPLETADA.");
}

fix();

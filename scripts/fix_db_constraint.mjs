import { createClient } from '@supabase/supabase-js';
import dotenv from 'dotenv';
dotenv.config();

const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_KEY);

async function fixConstraint() {
    console.log("🚀 Actualizando restricción de tipo en la base de datos...");

    // SQL para actualizar el check constraint
    const sql = `
        ALTER TABLE titles DROP CONSTRAINT IF EXISTS titles_type_check;
        ALTER TABLE titles ADD CONSTRAINT titles_type_check CHECK (type IN ('movie', 'series', 'Pelis Web', 'live'));
    `;

    // Intentamos ejecutar via RPC si tienes habilitado el postgres executor,
    // de lo contrario, esto confirma que el problema es la restricción.
    console.log("⚠️  Por favor, ejecuta el siguiente SQL en el editor de Supabase (SQL Editor):");
    console.log(sql);
}

fixConstraint();

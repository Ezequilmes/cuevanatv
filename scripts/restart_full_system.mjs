import { exec } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const psScript = path.resolve(__dirname, '../start_cuevanatv.ps1');
const command = `powershell -ExecutionPolicy Bypass -File "${psScript}"`;

console.log(`🚀 Ejecutando reinicio maestro: ${command}`);

exec(command, (error, stdout, stderr) => {
    if (error) {
        console.error(`❌ Error al reiniciar: ${error.message}`);
        return;
    }
    if (stderr) {
        console.error(`⚠️ Stderr: ${stderr}`);
        return;
    }
    console.log(`✅ Resultado: ${stdout}`);
});

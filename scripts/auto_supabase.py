import os
import time
import requests
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler

# === CONFIGURACIÓN ===
WATCH_DIRECTORY = r"D:\pelis"
SUPABASE_URL = "TU_URL_SUPABASE"  # Ej: https://xyz.supabase.co
SUPABASE_KEY = "TU_KEY_SUPABASE"
NGROK_URL = "https://MI_URL_NGROK.ngrok-free.app"

class MovieHandler(FileSystemEventHandler):
    def on_created(self, event):
        # Solo procesar archivos .mp4 nuevos
        if not event.is_directory and event.src_path.lower().endswith(".mp4"):
            filename = os.path.basename(event.src_path)
            title = os.path.splitext(filename)[0]

            print(f"[*] Nuevo video detectado: {filename}")

            # Construcción de la URL para saltar el warning de Ngrok
            video_url = f"{NGROK_URL}/api/raw/{filename}"

            # Payload para la tabla 'titles'
            payload = {
                "title": title,
                "type": "movie",
                "video_url": video_url,
                "category": "Recientes" # Opcional
            }

            headers = {
                "apikey": SUPABASE_KEY,
                "Authorization": f"Bearer {SUPABASE_KEY}",
                "Content-Type": "application/json",
                "Prefer": "return=minimal"
            }

            try:
                response = requests.post(
                    f"{SUPABASE_URL}/rest/v1/titles",
                    json=payload,
                    headers=headers
                )

                if response.status_code in [200, 201]:
                    print(f"[+] Éxito: '{title}' subido a Supabase.")
                else:
                    print(f"[-] Error Supabase ({response.status_code}): {response.text}")
            except Exception as e:
                print(f"[!] Error de conexión: {e}")

if __name__ == "__main__":
    if not os.path.exists(WATCH_DIRECTORY):
        print(f"ERROR: La carpeta {WATCH_DIRECTORY} no existe.")
        exit(1)

    event_handler = MovieHandler()
    observer = Observer()
    observer.schedule(event_handler, WATCH_DIRECTORY, recursive=False)

    print(f"--- Vigilante de Películas Activo ---")
    print(f"Carpeta: {WATCH_DIRECTORY}")
    print(f"Target: {NGROK_URL}")

    observer.start()
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        observer.stop()
        print("\nDeteniendo vigilante...")
    observer.join()

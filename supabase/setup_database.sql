-- 1. Crear la tabla de perfiles de usuario vinculada a Auth
CREATE TABLE IF NOT EXISTS public.app_users (
  id UUID REFERENCES auth.users ON DELETE CASCADE NOT NULL PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  active BOOLEAN DEFAULT TRUE,
  days_remaining INTEGER DEFAULT 3,
  fecha_vencimiento TIMESTAMP WITH TIME ZONE DEFAULT (now() + interval '3 days'),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- 2. Habilitar RLS (Seguridad de Fila)
ALTER TABLE public.app_users ENABLE ROW LEVEL SECURITY;

-- 3. Crear política para que los usuarios vean solo sus propios datos
DROP POLICY IF EXISTS "Usuarios pueden ver su propio perfil" ON public.app_users;
CREATE POLICY "Usuarios pueden ver su propio perfil"
ON public.app_users
FOR SELECT
TO authenticated
USING (auth.uid() = id);

-- 4. Función para el Trigger de Registro Automático
CREATE OR REPLACE FUNCTION public.on_auth_user_created()
RETURNS trigger AS $$
BEGIN
  INSERT INTO public.app_users (id, email, active, days_remaining, fecha_vencimiento)
  VALUES (
    new.id,
    new.email,
    true,
    3,
    (now() + interval '3 days')
  );
  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 5. Crear el Trigger en la tabla de autenticación nativa de Supabase
DROP TRIGGER IF EXISTS tr_auth_user_created ON auth.users;
CREATE TRIGGER tr_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.on_auth_user_created();

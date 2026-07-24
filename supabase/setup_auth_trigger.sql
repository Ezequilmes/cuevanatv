-- 1. Crear la función que manejará la inserción automática
CREATE OR REPLACE FUNCTION public.on_auth_user_created()
RETURNS trigger AS $$
BEGIN
  INSERT INTO public.app_users (
    id,
    email,
    active,
    days_remaining,
    fecha_vencimiento,
    role,
    limite_pantallas
  )
  VALUES (
    new.id,
    new.email,
    true,
    3,
    (now() + interval '3 days'),
    'user',
    1
  );
  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 2. Crear el trigger que se dispara después de un signUp exitoso
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.on_auth_user_created();

-- 3. Habilitar RLS en app_users (si no está habilitado)
ALTER TABLE public.app_users ENABLE ROW LEVEL SECURITY;

-- 4. Crear política para que los usuarios puedan leer sus propios datos
DROP POLICY IF EXISTS "Users can read their own profile" ON public.app_users;
CREATE POLICY "Users can read their own profile"
ON public.app_users
FOR SELECT
TO authenticated
USING (auth.uid() = id);

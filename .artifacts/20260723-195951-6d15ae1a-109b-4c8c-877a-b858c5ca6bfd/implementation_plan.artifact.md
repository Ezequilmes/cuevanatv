# Fix Authentication and MercadoPago Integration

This plan outlines the steps to fix the authentication system for CuevanaTV using Supabase Auth and a database trigger, and integrate MercadoPago for subscription payments using a Supabase Edge Function.

## User Review Required

> [!IMPORTANT]
> - **MercadoPago Access Token**: You will need to set your MercadoPago Access Token as a secret in Supabase for the Edge Function to work.
> - **RLS Policies**: Ensure that `app_users` has appropriate Row Level Security (RLS) policies to allow users to read their own data.

## Proposed Changes

### Supabase Database Configuration

#### [NEW] [setup_auth_trigger.sql](file:///D:/magik/Mi%20app/Cuevanatv/supabase/setup_auth_trigger.sql)

- Create a SQL script to set up the `on_auth_user_created` function and trigger.
- This will automatically create an `app_users` record when a user signs up.

```sql
-- Function to handle new user creation
CREATE OR REPLACE FUNCTION public.on_auth_user_created()
RETURNS trigger AS $$
BEGIN
  INSERT INTO public.app_users (id, email, active, days_remaining, fecha_vencimiento, role)
  VALUES (
    new.id,
    new.email,
    true,
    3,
    (now() + interval '3 days'),
    'user'
  );
  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to execute the function after signup
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.on_auth_user_created();
```

---

### Frontend Logic

#### [index.html](file:///D:/magik/Mi%20app/Cuevanatv/docs/index.html)

- Refactor `login` and `register` functions to use Supabase Auth exclusively.
- Remove manual `insert` and `select` checks against `app_users` during authentication.
- Update `syncUserData` to fetch the subscription status from `app_users` after a successful login.
- Update `initMercadoPago` to call the new Edge Function.

---

### Supabase Edge Functions

#### [NEW] [index.ts (create-preference)](file:///D:/magik/Mi%20app/Cuevanatv/supabase/functions/create-preference/index.ts)

- Create an Edge Function to generate MercadoPago payment preferences.
- This function will securely interact with the MercadoPago API.

---

## Verification Plan

### Automated Tests
- I will verify the JavaScript logic by ensuring it correctly calls the Supabase and MercadoPago APIs.
- Since I cannot run the full Supabase environment locally with Docker, I will provide the SQL and Edge Function code for manual deployment and verification by the user.

### Manual Verification
1. **SQL Trigger**: Run the provided SQL in the Supabase SQL Editor and verify a new record is created in `app_users` upon a test signup.
2. **Auth Flow**: Perform a signup and login on the website and verify that the user menu and dashboard update correctly.
3. **Payment Flow**: Click the "Pagar Abono" button and verify that the MercadoPago Checkout Pro opens with the correct amount ($3000 ARS).

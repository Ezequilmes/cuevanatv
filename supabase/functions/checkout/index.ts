import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const body = await req.json()
    console.log("💳 Webhook Recibido:", JSON.stringify(body))

    // 1. Extraer ID del pago (MP envía notificaciones de varios tipos)
    const paymentId = body.data?.id || body.resource?.split('/').pop()

    if (!paymentId || (body.type !== 'payment' && body.action?.indexOf('payment') === -1)) {
        return new Response(JSON.stringify({ message: "Not a payment notification" }), { status: 200 })
    }

    // 2. Consultar estado real en MercadoPago para evitar fraudes
    const mpToken = Deno.env.get('MP_ACCESS_TOKEN')
    const mpRes = await fetch(`https://api.mercadopago.com/v1/payments/${paymentId}`, {
        headers: { 'Authorization': `Bearer ${mpToken}` }
    })

    if (!mpRes.ok) throw new Error("No se pudo validar el pago con MercadoPago")
    const paymentData = await mpRes.json()

    // 3. Procesar solo si está aprobado
    if (paymentData.status === 'approved') {
        const userEmail = paymentData.external_reference // Lo configuramos en create-preference
        const amount = paymentData.transaction_amount

        console.log(`✅ Pago Aprobado: ${userEmail} - Monto: ${amount}`)

        // Inicializar Supabase Admin (Service Role) para saltar RLS
        const supabase = createClient(
            Deno.env.get('SUPABASE_URL') ?? '',
            Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
        )

        // Obtener datos actuales del usuario
        const { data: user, error: userError } = await supabase
            .from('app_users')
            .select('days_remaining')
            .eq('email', userEmail)
            .single()

        if (userError || !user) throw new Error("Usuario no encontrado en base de datos")

        // Sumar 30 días (o según el monto)
        const daysToAdd = 30
        const newDays = (user.days_remaining || 0) + daysToAdd

        const { error: updateError } = await supabase
            .from('app_users')
            .update({
                active: true,
                days_remaining: newDays,
                updated_at: new Date().toISOString()
            })
            .eq('email', userEmail)

        if (updateError) throw updateError

        console.log(`✨ Días actualizados para ${userEmail}: ${newDays} totales.`)
    }

    return new Response(JSON.stringify({ success: true }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200
    })

  } catch (error) {
    console.error("❌ Error Webhook:", error.message)
    return new Response(JSON.stringify({ error: error.message }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })
  }
})

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { MercadoPagoConfig, Preference } from 'npm:mercadopago'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const { email, price, title } = await req.json()
    const client = new MercadoPagoConfig({ accessToken: Deno.env.get('MP_ACCESS_TOKEN') as string })
    const preference = new Preference(client)

    const result = await preference.create({
      body: {
        items: [
          {
            id: 'abono_mensual',
            title: title || 'Abono Mensual CuevanaTV',
            quantity: 1,
            unit_price: Number(price) || 3000,
            currency_id: 'ARS',
          }
        ],
        payer: { email: email },
        back_urls: {
          success: 'https://cuevanatv.store',
          failure: 'https://cuevanatv.store',
          pending: 'https://cuevanatv.store'
        },
        auto_return: 'approved',
      }
    })

    return new Response(
      JSON.stringify({ preferenceId: result.id }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  }
})
